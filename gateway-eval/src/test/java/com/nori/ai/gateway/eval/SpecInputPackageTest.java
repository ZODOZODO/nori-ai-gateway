package com.nori.ai.gateway.eval;

import com.nori.ai.gateway.eval.io.Json;
import com.nori.ai.gateway.eval.spec.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.eval.TestSupport.error;

class SpecInputPackageTest {
    @TempDir Path directory;
    HttpServer server; byte[] stored; int responseCode; final AtomicInteger gets = new AtomicInteger(), puts = new AtomicInteger(), deletes = new AtomicInteger();
    final String sentinel = "PACKAGE_CREDENTIAL_SENTINEL";
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("JOB-TOKEN")).isEqualTo(sentinel);
            String method=exchange.getRequestMethod();
            if(method.equals("PUT")){ puts.incrementAndGet();stored=exchange.getRequestBody().readAllBytes();exchange.sendResponseHeaders(201,-1); }
            else if(method.equals("DELETE")){deletes.incrementAndGet();exchange.sendResponseHeaders(405,-1);}
            else { gets.incrementAndGet(); int code=responseCode!=0?responseCode:stored==null?404:200; if(code==302)exchange.getResponseHeaders().add("Location","https://forbidden.invalid/leak"); byte[] body=stored==null?new byte[0]:stored;exchange.sendResponseHeaders(code,body.length==0?-1:body.length);if(body.length>0)exchange.getResponseBody().write(body); }
            exchange.close();
        });server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    SpecInputPackage packages(){
        var client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return new SpecInputPackage(Map.of("FIXTURE_TOKEN",sentinel),(method,uri,header,token,body)->{
            try {
                URI local=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+uri.getRawPath());
                var request=HttpRequest.newBuilder(local).header(header,token).method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());return new SpecInputPackage.Response(response.statusCode(),response.body());
            }catch(Exception e){throw new IllegalStateException("STUB_TRANSPORT_FAILED");}
        });
    }
    Path fetch(TestSupport f,Path out){return packages().fetch(f.binding().specSha(),f.baseline,out,"https://gitlab.noricloud.org/api/v4","7","JOB-TOKEN","FIXTURE_TOKEN",null);}
    Map<String,Object> publish(TestSupport f,Path out){return packages().publish(f.spec.toString(),f.baseline,out,"https://gitlab.noricloud.org/api/v4","7","JOB-TOKEN","FIXTURE_TOKEN",null);}
    @Test void preservesEveryApprovedByteAcrossPackStubPutAndFetch() throws Exception {
        byte[][] fixtures={"LF\n".getBytes(StandardCharsets.UTF_8),"CRLF\r\n".getBytes(StandardCharsets.UTF_8),new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf,65,13,10},"no-final-newline".getBytes(StandardCharsets.UTF_8)};
        for(int i=0;i<fixtures.length;i++){
            TestSupport f=new TestSupport(directory.resolve("case"+i),fixtures[i]);stored=null;
            assertThat(publish(f,f.root.resolve("pack")).get("state")).isEqualTo("SPEC_PACKAGE_UPLOADED");
            Path fetched=fetch(f,f.root.resolve("fetch"));assertThat(Files.readAllBytes(fetched)).isEqualTo(fixtures[i]);assertThat(Json.read(fetched.resolveSibling("fetch-receipt.json")).path("sha256").asText()).isEqualTo(Json.sha(fixtures[i]));
            try(var files=Files.walk(f.root)){for(Path file:files.filter(Files::isRegularFile).toList())assertThat(new String(Files.readAllBytes(file),StandardCharsets.UTF_8)).doesNotContain(sentinel);}
        }
    }
    @Test void reusesMatchingPackageWithoutPutAndRejectsExistingDifferentContent() throws Exception {
        TestSupport f=new TestSupport(directory);stored=SpecInputPackage.compress(Files.readAllBytes(f.spec));
        assertThat(publish(f,directory.resolve("work")).get("state")).isEqualTo("SPEC_PACKAGE_REUSED");assertThat(puts).hasValue(0);
        stored=SpecInputPackage.compress("different".getBytes());error(()->publish(f,directory.resolve("work")),3,"SPEC_PACKAGE_CONTENT_MISMATCH");assertThat(puts).hasValue(0);assertThat(deletes).hasValue(0);
    }
    @Test void rejectsCanonicalMutationBeforeAnyHttpRequest() throws Exception {
        TestSupport f=new TestSupport(directory);Files.writeString(f.spec,"changed");error(()->publish(f,directory.resolve("work")),3,"SPEC_BASELINE_MISMATCH");assertThat(gets).hasValue(0);assertThat(puts).hasValue(0);
    }
    @Test void rejectsMissingForbiddenAndRedirectedPackagesWithoutLeakingCredentials() throws Exception {
        TestSupport f=new TestSupport(directory);
        error(()->fetch(f,directory.resolve("out")),5,"SPEC_PACKAGE_UNAVAILABLE");
        responseCode=403;error(()->fetch(f,directory.resolve("out")),5,"SPEC_PACKAGE_UNAVAILABLE");
        responseCode=302;error(()->fetch(f,directory.resolve("out")),2,"SPEC_PACKAGE_HOST_FORBIDDEN");
        int previous=gets.get();
        for(String url:List.of("http://gitlab.noricloud.org/api/v4","https://evil.invalid/api/v4","https://gitlab.noricloud.org:444/api/v4","https://gitlab.noricloud.org/api/v4/"))error(()->packages().fetch(f.binding().specSha(),f.baseline,directory.resolve("out"),url,"7","JOB-TOKEN","FIXTURE_TOKEN",null),2,"SPEC_PACKAGE_HOST_FORBIDDEN");
        assertThat(gets).hasValue(previous);
        ByteArrayOutputStream stdout=new ByteArrayOutputStream(),stderr=new ByteArrayOutputStream();
        int code=new EvalCli(Map.of("FIXTURE_TOKEN",sentinel),new PrintStream(stdout),new PrintStream(stderr),directory).execute("spec-input","fetch","--sha",f.binding().specSha(),"--baseline",f.baseline.toString(),"--out-dir",directory.resolve("cli").toString(),"--api-url","https://evil.invalid/api/v4","--project-id","7","--auth-header","JOB-TOKEN","--token-env","FIXTURE_TOKEN");
        assertThat(code).isEqualTo(2);assertThat(stdout.toString()+stderr).doesNotContain(sentinel);
    }
    @Test void rejectsWrongShaBeforeRequestAndWrongPayloadWithoutOutput() throws Exception {
        TestSupport f=new TestSupport(directory);
        error(()->packages().fetch("f".repeat(64),f.baseline,directory.resolve("out"),"https://gitlab.noricloud.org/api/v4","7","JOB-TOKEN","FIXTURE_TOKEN",null),3,"SPEC_BASELINE_MISMATCH");assertThat(gets).hasValue(0);
        stored=SpecInputPackage.compress("wrong".getBytes());error(()->fetch(f,directory.resolve("out")),3,"SPEC_PACKAGE_CONTENT_MISMATCH");
        if(Files.exists(directory.resolve("out")))try(var files=Files.walk(directory.resolve("out"))){assertThat(files.filter(Files::isRegularFile).count()).isZero();}
    }
    @Test void enforcesSingleMemberTrailerCrcAndExpansionBounds() throws Exception {
        byte[] valid=SpecInputPackage.compress("abc".getBytes());
        byte[] trailing=Arrays.copyOf(valid,valid.length+1),multiple=new byte[valid.length*2];System.arraycopy(valid,0,multiple,0,valid.length);System.arraycopy(valid,0,multiple,valid.length,valid.length);
        byte[] corrupt=valid.clone();corrupt[corrupt.length-8]^=1;
        for(byte[] gzip:List.of(new byte[]{1,2},trailing,multiple,corrupt))error(()->SpecInputPackage.expand(gzip),4,"SPEC_PACKAGE_INVALID_GZIP");
        byte[] bomb=SpecInputPackage.compress(new byte[SpecInputPackage.MAX_EXPANDED+1]);error(()->SpecInputPackage.expand(bomb),4,"SPEC_INPUT_OVERSIZED");
        error(()->SpecInputPackage.expand(new byte[SpecInputPackage.MAX_COMPRESSED+1]),4,"SPEC_INPUT_OVERSIZED");
    }
    @Test void failureRemovesPreviousOutputsAndTemporaryFiles() throws Exception {
        TestSupport f=new TestSupport(directory);stored=SpecInputPackage.compress(Files.readAllBytes(f.spec));Path out=directory.resolve("fetch");Path file=fetch(f,out);assertThat(file).exists();
        stored=new byte[]{1};error(()->fetch(f,out),4,"SPEC_PACKAGE_INVALID_GZIP");
        try(var files=Files.walk(out)){assertThat(files.filter(Files::isRegularFile).count()).isZero();}
    }
}
