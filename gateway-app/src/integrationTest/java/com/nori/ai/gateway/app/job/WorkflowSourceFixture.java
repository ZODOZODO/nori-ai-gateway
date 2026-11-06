package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.authz.SourceAuthorizationStep;
import com.nori.ai.gateway.core.port.SourceRetentionReader;
import com.nori.ai.gateway.integration.loki.*;
import com.nori.ai.gateway.integration.tempo.TempoTraceConnector;
import com.nori.ai.gateway.integration.gitlab.*;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.*;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 실제 P12 전송과 현재 인가를 유지하고 localhost 응답만 소유합니다. */
@TestConfiguration(proxyBeanMethods=false)
public class WorkflowSourceFixture {
    public static final class Server implements AutoCloseable {
        final HttpServer server;
        final java.util.concurrent.ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
        final SimpleMeterRegistry meters=new SimpleMeterRegistry();
        final AtomicInteger logs=new AtomicInteger(),traces=new AtomicInteger(),stopped=new AtomicInteger();
        final List<String> codeRefs=new CopyOnWriteArrayList<>(),pipelines=new CopyOnWriteArrayList<>();
        final AtomicInteger lineageTraces=new AtomicInteger(),commits=new AtomicInteger();
        volatile List<String> traceVersions=List.of();
        volatile String tracePrefix="0".repeat(28);
        volatile List<String> spanIds=List.of();
        volatile String logText="EQP-001 정상 관측";
        volatile int commitStatus=200;
        final AtomicBoolean blockNextStop=new AtomicBoolean();
        volatile CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),ended=new CountDownLatch(1);
        volatile Runnable afterResponse=()->{};
        volatile Runnable afterStop=()->{};
        volatile java.util.function.Consumer<String> afterHttpStop=ignored->{};
        final SourceObservability observations;
        public Server() throws java.io.IOException {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                String body;
                if(exchange.getRequestURI().getPath().startsWith("/loki/")) {
                    logs.incrementAndGet();var query=query(exchange.getRequestURI());String selector=query.get("query");
                    String app=LokiSourcePolicy.APPS.stream().filter(value->selector.contains("app=\""+value+"\"")).findFirst().orElseThrow();
                    // 실제 전달된 나노초 창의 첫 instant만 응답하므로 서버 fixture가 창을 축소하지 않습니다.
                    body=SchemaContracts.JSON.writeValueAsString(Map.of("status","success","data",Map.of("resultType","streams","result",List.of(Map.of("stream",Map.of("app",app,"namespace","tc"),"values",List.of(List.of(query.get("start"),logText)))))));
                } else if(exchange.getRequestURI().getPath().equals("/api/search")) {
                    traces.incrementAndGet();boolean selected=query(exchange.getRequestURI()).get("q").contains("tc-gateway");var hits=new ArrayList<Map<String,String>>();if(selected)for(int i=0;i<traceVersions.size();i++)hits.add(Map.of("traceID",traceId(i)));
                    body=SchemaContracts.JSON.writeValueAsString(Map.of("traces",hits));
                } else if(exchange.getRequestURI().getPath().startsWith("/api/traces/")) {
                    String id=exchange.getRequestURI().getPath().substring("/api/traces/".length());int index=Integer.parseInt(id.substring(28),16)-1;
                    var attributes=List.of(Map.of("key","service.name","value",Map.of("stringValue","tc-gateway")),Map.of("key","service.version","value",Map.of("stringValue",traceVersions.get(index))));
                    var spans=List.of(Map.of("spanId",spanIds.isEmpty()?"span-"+index:spanIds.get(index),"name","equipment","startTimeUnixNano",LokiLogConnector.nanos(P09QueueSupport.NOW.minusSeconds(2700)),"endTimeUnixNano",LokiLogConnector.nanos(P09QueueSupport.NOW.minusSeconds(2699))));
                    body=SchemaContracts.JSON.writeValueAsString(Map.of("batches",List.of(Map.of("resource",Map.of("attributes",attributes),"scopeSpans",List.of(Map.of("spans",spans))))));
                } else if(exchange.getRequestURI().getPath().contains("/pipelines/")) {
                    String version=exchange.getRequestURI().getPath().split("/")[6];pipelines.add(version);body="[{\"id\":"+(Long.parseLong(version)*10+3)+",\"name\":\"package\",\"status\":\"success\"}]";
                } else if(exchange.getRequestURI().getPath().endsWith("/trace")) {
                    lineageTraces.incrementAndGet();long job=Long.parseLong(exchange.getRequestURI().getPath().split("/")[6]);body="sibling nori-tc-gateway @ "+shortSha(Long.toString((job-3)/10))+"\n";
                } else if(exchange.getRequestURI().getPath().contains("/repository/commits/")) {
                    commits.incrementAndGet();String shortSha=exchange.getRequestURI().getPath().substring(exchange.getRequestURI().getPath().lastIndexOf('/')+1);body="{\"id\":\""+shortSha+"2".repeat(33)+"\"}";
                } else if(exchange.getRequestURI().getPath().endsWith("/search")) {
                    String ref=query(exchange.getRequestURI()).get("ref");codeRefs.add(ref);body=SchemaContracts.JSON.writeValueAsString(List.of(Map.of("path","src/main/Equipment.java","data","검증된 코드 "+ref)));
                } else throw new AssertionError("등록되지 않은 localhost 시험 경로입니다");
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                int status=exchange.getRequestURI().getPath().contains("/repository/commits/")?commitStatus:200;
                try {exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);}
                finally {exchange.close();afterResponse.run();}
            });
            server.setExecutor(executor);server.start();
            observations=SourceObservability.create(meters,new ObservationHandler<ClientRequestObservationContext>() {
                @Override public boolean supportsContext(Observation.Context context){return context instanceof ClientRequestObservationContext;}
                @Override public void onStop(ClientRequestObservationContext context) {
                    stopped.incrementAndGet();
                    afterStop.run();
                    afterHttpStop.accept(context.getCarrier().getURI().getPath());
                    if(!blockNextStop.compareAndSet(true,false))return;
                    entered.countDown();boolean interrupted=false;
                    try {
                        long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
                        while(release.getCount()!=0&&System.nanoTime()<deadline)try{release.await(100,TimeUnit.MILLISECONDS);}catch(InterruptedException failure){interrupted=true;}
                        if(release.getCount()!=0)throw new AssertionError("실제 Source 종료 관찰 해제가 누락되었습니다");
                    } finally {ended.countDown();if(interrupted)Thread.currentThread().interrupt();}
                }
            });
        }
        URI base(){return URI.create("http://127.0.0.1:"+server.getAddress().getPort());}
        String traceId(int index){return tracePrefix+String.format("%04x",index+1);}
        static String shortSha(String version){return version.equals("123")?"abcdef1":"fedcba1";}
        private Map<String,String> query(URI uri) {
            var values=new LinkedHashMap<String,String>();for(String part:uri.getRawQuery().split("&")){var pair=part.split("=",2);values.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8));}return values;
        }
        void blockNext(){entered=new CountDownLatch(1);release=new CountDownLatch(1);ended=new CountDownLatch(1);blockNextStop.set(true);}
        @Override public void close(){release.countDown();server.stop(0);executor.close();meters.close();}
    }
    @Bean Server workflowSourceServer() throws java.io.IOException{return new Server();}
    // P14 실제 보존 reader Bean과 함께 등록되므로 이 fixture의 고정 관측을 명시 우선합니다(Source 조립 경로 시험은 실제 reader를 씁니다).
    @Bean @org.springframework.context.annotation.Primary SourceRetentionReader workflowTestRetention(Clock clock){return source->Optional.of(new SourceRetentionReader.VerifiedOldest(P09QueueSupport.NOW.minus(Duration.ofDays(7)),clock.instant()));}
    @Bean LokiLogConnector workflowLoki(Server server,SourceAuthorizationStep authorization,Clock clock,SourceRetentionReader retention,LokiProperties properties){return new LokiLogConnector(new SourceHttpClient(server.base(),"loki",server.observations),new SourceReadAccess(authorization,clock),retention,properties,clock);}
    @Bean TempoTraceConnector workflowTempo(Server server,SourceAuthorizationStep authorization,Clock clock){return new TempoTraceConnector(new SourceHttpClient(server.base(),"tempo",server.observations),new SourceReadAccess(authorization,clock));}
    public static final class GitSetup implements AutoCloseable {
        final GitLabSourcePolicy.Repository repository=new GitLabSourcePolicy.Repository(22,"nori-tc-gateway",Set.of("main"),Set.of("src/main"),"local-test-approval");
        final GitLabSourcePolicy.LineageEntry lineage=new GitLabSourcePolicy.LineageEntry("tc-gateway",11,"package","nori-tc-gateway",22,"SIBLING_CLONE_LOG","local-test-approval");
        final GitLabSourcePolicy policy=new GitLabSourcePolicy(List.of(repository),List.of(lineage));
        final java.nio.file.Path directory,file;
        final GitLabReadTokenFile token;
        GitSetup() throws java.io.IOException {directory=java.nio.file.Files.createTempDirectory("p13-local-git-");file=directory.resolve("read-token");java.nio.file.Files.writeString(file,UUID.randomUUID().toString());token=new GitLabReadTokenFile(file);}
        @Override public void close() throws java.io.IOException{java.nio.file.Files.deleteIfExists(file);java.nio.file.Files.deleteIfExists(directory);}
    }
    @TestConfiguration(proxyBeanMethods=false) public static class GitConfiguration {
        @Bean GitSetup workflowGitSetup()throws java.io.IOException{return new GitSetup();}
        @Bean DirectSourceToolExecutor.GitExecutionScopes workflowGitScopes(GitSetup setup){return new DirectSourceToolExecutor.GitExecutionScopes(List.of(setup.repository),List.of(setup.lineage));}
        @Bean GitLabCodeConnector workflowGitCode(Server server,GitSetup setup,SourceAuthorizationStep authorization,Clock clock){return new GitLabCodeConnector(new SourceHttpClient(server.base(),"gitlab",server.observations),new SourceReadAccess(authorization,clock),setup.policy,setup.token);}
        @Bean GitLabPipelineLineageReader workflowGitLineage(Server server,GitSetup setup,SourceAuthorizationStep authorization,Clock clock){return new GitLabPipelineLineageReader(new SourceHttpClient(server.base(),"gitlab",server.observations),new SourceReadAccess(authorization,clock),setup.policy,setup.token);}
    }
}
