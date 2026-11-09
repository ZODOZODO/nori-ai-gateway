package com.nori.ai.gateway.app.harness;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.json.JsonMapper;

public final class AppProcessHarness implements AutoCloseable {
    private final List<Process> children=new ArrayList<>();
    private final ExecutorService readers=Executors.newCachedThreadPool();
    private final AtomicInteger sentinelCount=new AtomicInteger();
    private final List<String> sentinels=new CopyOnWriteArrayList<>();
    private Process process;private int port;
    public AppProcessHarness(String sentinel){watch(sentinel);}
    public void watch(String sentinel){if(sentinel!=null&&!sentinel.isBlank()&&!sentinels.contains(sentinel))sentinels.add(sentinel);}
    public Process launch(Path state,Map<String,String> properties,String fingerprintOverride)throws Exception{return launch(state,properties,fingerprintOverride,Map.of());}
    /** environment 는 최소 OS 변수 위에 시험이 명시한 자식 환경변수만 더한다(예: 자동 채택 금지 확인용 OPENAI_*). */
    public Process launch(Path state,Map<String,String> properties,String fingerprintOverride,Map<String,String> environment)throws Exception{
        String classpath=System.getProperty("aigw.harness.classpath"),javaExe=System.getProperty("aigw.harness.javaExe");
        if(classpath==null||classpath.isBlank()||javaExe==null||!Files.isRegularFile(Path.of(javaExe)))throw new IllegalStateException("현재 Test task의 하네스 인자가 필요합니다");
        Files.createDirectories(state);Files.deleteIfExists(state.resolve("harness-ready.json"));
        String fingerprint=fingerprintOverride==null?HarnessFingerprint.calculate(classpath):fingerprintOverride;
        Path argfile=Files.createTempFile(state,"harness-",".args");var lines=new ArrayList<String>();
        // 공용 하네스도 운영 기본 OTLP endpoint로 보내지 않습니다. 시험이 명시한 값이 있으면 그 값이 우선합니다.
        var effective=new TreeMap<String,String>(Map.of("ai.gateway.otlp.metrics.enabled","false","ai.gateway.otlp.traces.enabled","false","ai.gateway.otlp.logs.enabled","false"));effective.putAll(properties);
        effective.forEach((key,value)->lines.add(quote("-D"+key+"="+value)));
        lines.add("-cp");lines.add(quote(classpath));lines.add(HarnessMain.class.getName());
        for(String value:List.of("--fingerprint",fingerprint,"--config",HarnessP09TestPorts.class.getName(),"--state-dir",state.toString()))lines.add(quote(value));
        Files.writeString(argfile,String.join("\n",lines)+"\n",StandardCharsets.UTF_8);
        var builder=new ProcessBuilder(javaExe,"@"+argfile);var env=builder.environment();env.clear();
        for(String name:List.of("PATH","Path","SystemRoot","SYSTEMROOT","WINDIR","TEMP","TMP","USERPROFILE","HOME","LANG","LC_ALL")){
            String value=System.getenv(name);if(value!=null)env.put(name,value);
        }
        env.putAll(environment);
        builder.redirectErrorStream(true);process=builder.start();children.add(process);var child=process;
        readers.submit(()->{try(var input=new java.io.BufferedReader(new java.io.InputStreamReader(child.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=input.readLine())!=null){boolean found=false;for(String sentinel:sentinels)found|=line.contains(sentinel);if(found)sentinelCount.incrementAndGet();}}catch(java.io.IOException failure){if(child.isAlive())throw new IllegalStateException("자식 출력 검사에 실패했습니다");}});
        return process;
    }
    public Map<String,Object> awaitReady(Path state)throws Exception{
        long deadline=System.nanoTime()+Duration.ofSeconds(120).toNanos();var http=HttpClient.newHttpClient();
        while(System.nanoTime()<deadline){
            if(!process.isAlive())throw new AssertionError("자식 JVM 기동 실패 exit="+process.exitValue());
            Path ready=state.resolve("harness-ready.json");
            if(Files.isRegularFile(ready)){
                @SuppressWarnings("unchecked") var data=(Map<String,Object>)JsonMapper.builder().build().readValue(Files.readString(ready),Map.class);
                port=((Number)data.get("port")).intValue();
                if(!Objects.equals(data.get("harnessClasspathSha256"),HarnessFingerprint.calculate(System.getProperty("aigw.harness.classpath"))))throw new AssertionError("자식 classpath 지문 불일치");
                var response=http.send(HttpRequest.newBuilder(uri("/actuator/health/readiness")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.discarding());
                if(response.statusCode()==200)return data;
            }Thread.sleep(25);
        }throw new AssertionError("실제 HTTP readiness 준비 한도를 초과했습니다");
    }
    public URI uri(String path){return URI.create("http://127.0.0.1:"+port+path);}
    public int awaitExit(Duration timeout)throws InterruptedException{if(!process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS))throw new AssertionError("자식 JVM이 종료되지 않았습니다");return process.exitValue();}
    public int sentinelCount(){return sentinelCount.get();}
    private static String quote(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    @Override public void close(){
        for(Process child:children){var descendants=child.descendants().toList();for(var descendant:descendants)descendant.destroy();child.destroy();
            try{if(!child.waitFor(5,TimeUnit.SECONDS))child.destroyForcibly().waitFor(5,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();child.destroyForcibly();}
            for(var descendant:descendants)if(descendant.isAlive())descendant.destroyForcibly();
        }readers.shutdown();
        try{if(!readers.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("자식 출력 검사가 종료되지 않았습니다");}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("자식 출력 검사 종료 대기가 중단되었습니다");}
    }
}
