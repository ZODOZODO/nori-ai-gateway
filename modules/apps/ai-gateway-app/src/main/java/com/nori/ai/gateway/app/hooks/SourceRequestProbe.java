package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.integration.source.TcSourcePolicy;
import com.nori.ai.gateway.persistence.queue.TerminalCommitObserver;
import io.micrometer.observation.*;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.transaction.support.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;

/** 실제 송신 직전의 구조만 digest로 남깁니다. URI·질문·시간창 원문은 이 객체에 저장하지 않습니다. */
public final class SourceRequestProbe implements ObservationHandler<ClientRequestObservationContext>, TerminalCommitObserver {
    public static final int MAX_JOBS=128,MAX_ATTEMPTS=1024;
    public record Attempt(String digest,String state,String outcome) {}
    public record Observation(String capabilityId,String status,int count,List<Attempt> attempts) {}
    public record Snapshot(String jobId,String status,List<Observation> observations) {}
    public interface Scope extends AutoCloseable { @Override void close(); }
    private static final Pattern SELECTOR=Pattern.compile("\\{app=\"([^\"]+)\", namespace=\"([^\"]+)\"}");
    private static final class Data {String status="OPEN";final List<Attempt> attempts=new ArrayList<>();}
    private record Binding(String jobId,String capabilityId,Data data) {}
    private record Pending(Data data,int index) {}
    private final Map<String,Map<String,Data>> jobs=new HashMap<>();
    private final Set<String> terminal=new HashSet<>();
    private final ThreadLocal<Binding> current=new ThreadLocal<>();
    private final TestHookRegistry hooks;
    public SourceRequestProbe(TestHookRegistry hooks){this.hooks=hooks;hooks.onIdleReset(this::reset);}
    public Scope open(String jobId,String capabilityId) {
        if(hooks.binding(jobId).isEmpty())return ()->{};
        if(!Set.of("tc.logs.search","tc.trace.search","tc.git.searchCode").contains(capabilityId))throw new IllegalArgumentException("관측 Capability가 잘못되었습니다");
        synchronized(this){
            if(current.get()!=null){current.get().data.status="INVALID";return ()->{};}
            if(!jobs.containsKey(jobId)&&jobs.size()>=MAX_JOBS)return ()->{};
            var byCapability=jobs.computeIfAbsent(jobId,key->new HashMap<>());var previous=byCapability.get(capabilityId);
            if(previous!=null){previous.status="INVALID";return ()->{};}
            var data=new Data();byCapability.put(capabilityId,data);var binding=new Binding(jobId,capabilityId,data);current.set(binding);
            return ()->{synchronized(SourceRequestProbe.this){if(current.get()!=binding){data.status="INVALID";}else{current.remove();if(data.status.equals("OPEN"))data.status=data.attempts.stream().anyMatch(attempt->attempt.outcome().equals("STARTED"))?"INCOMPLETE":"COMPLETE";}}};
        }
    }
    public Snapshot snapshot(String jobId) {
        if(hooks.binding(jobId).isEmpty())return new Snapshot(jobId,"MISSING",List.of());
        synchronized(this){var data=jobs.get(jobId);if(data==null)return new Snapshot(jobId,jobs.size()>=MAX_JOBS?"OVERFLOW":"MISSING",List.of());var observations=new ArrayList<Observation>();data.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->observations.add(new Observation(entry.getKey(),entry.getValue().status,entry.getValue().attempts.size(),List.copyOf(entry.getValue().attempts))));return new Snapshot(jobId,"RECORDED",List.copyOf(observations));}
    }
    public synchronized boolean terminal(String jobId){return terminal.contains(jobId);}
    /** 종결 행이 다른 연결에 보이기 전에 표시하여 DB terminal 직후 조회가 RUNNING으로 보이지 않게 하고, 종결 tx가 롤백되면 되돌립니다. */
    @Override public void beforeCommit(String jobId){
        // hooks 잠금을 probe 잠금 안에서 잡지 않습니다. idle reset은 hooks→probe 순서로 잠급니다.
        if(hooks.binding(jobId).isEmpty())return;
        synchronized(this){terminal.add(jobId);}
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)synchronized(SourceRequestProbe.this){terminal.remove(jobId);}}
        });
    }
    @Override public void afterCommit(String jobId){}
    private synchronized void reset(){jobs.clear();terminal.clear();}
    @Override public boolean supportsContext(io.micrometer.observation.Observation.Context context){return context instanceof ClientRequestObservationContext;}
    @Override public void onStart(ClientRequestObservationContext context) {
        var binding=current.get();if(binding==null)return;
        synchronized(this){var data=binding.data();if(data.attempts.size()>=MAX_ATTEMPTS){data.status="OVERFLOW";return;}
            try {
                URI uri=Objects.requireNonNull(context.getCarrier()).getURI();
                if(!uri.getPath().endsWith("/loki/api/v1/query_range"))return;
                var values=new HashMap<String,String>();for(String pair:Objects.requireNonNull(uri.getRawQuery()).split("&")){var parts=pair.split("=",2);if(parts.length!=2||values.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),URLDecoder.decode(parts[1],StandardCharsets.UTF_8))!=null)throw new IllegalArgumentException();}
                var matcher=SELECTOR.matcher(values.getOrDefault("query",""));String state="INVALID",digest="";
                if(matcher.lookingAt()&&literalFilters(values.get("query").substring(matcher.end()))){
                    String app=matcher.group(1),namespace=matcher.group(2);var from=new BigInteger(values.get("start"));var to=new BigInteger(values.get("end"));if(from.compareTo(to)>=0)throw new IllegalArgumentException();
                    state=TcSourcePolicy.APPS.contains(app)&&namespace.equals("tc")?"ALLOWLIST":"OUTSIDE_ALLOWLIST";
                    digest=digest(app,namespace,from.toString(),to.toString());
                }else if(values.getOrDefault("query","").startsWith("{namespace="))state="NAMESPACE_ONLY";
                int index=data.attempts.size();data.attempts.add(new Attempt(digest,state,"STARTED"));context.put(Pending.class,new Pending(data,index));if(!state.equals("ALLOWLIST"))data.status="INVALID";
            }catch(RuntimeException failure){data.status="INVALID";int index=data.attempts.size();data.attempts.add(new Attempt("","INVALID","STARTED"));context.put(Pending.class,new Pending(data,index));}
        }
    }
    @Override public void onStop(ClientRequestObservationContext context){Pending pending=context.get(Pending.class);if(pending==null)return;synchronized(this){var previous=pending.data.attempts.get(pending.index);String outcome=context.getResponse()==null?"FAILED":"HTTP";try{if(context.getResponse()!=null)outcome=context.getResponse().getStatusCode().is2xxSuccessful()?"SUCCESS":"FAILED";}catch(java.io.IOException failure){outcome="FAILED";}pending.data.attempts.set(pending.index,new Attempt(previous.digest(),previous.state(),outcome));}}
    public static String digest(String app,String namespace,String fromNanos,String toNanos){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((app+"\n"+namespace+"\n"+fromNanos+"\n"+toNanos).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("관측 해시를 계산할 수 없습니다");}}
    private static boolean literalFilters(String tail){int index=0;while(index<tail.length()){
        if(!tail.startsWith(" |= \"",index))return false;index+=5;boolean closed=false;
        while(index<tail.length()){char value=tail.charAt(index++);if(value=='"'){closed=true;break;}if(value<32||value==127)return false;if(value=='\\'){
            if(index>=tail.length())return false;char escaped=tail.charAt(index++);if(escaped=='u'){if(index+4>tail.length()||!tail.substring(index,index+4).matches("[0-9a-fA-F]{4}"))return false;index+=4;}else if("\\\"nrt".indexOf(escaped)<0)return false;
        }}if(!closed)return false;
    }return true;}
}
