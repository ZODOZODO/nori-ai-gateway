package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.app.hooks.PausePointRegistry;
import com.nori.ai.gateway.persistence.repository.JobEventRepository;
import java.net.http.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class SseApiIT extends ApiWebSupport {
    @Test @Tag("AC-24-JOB") void actualAdmissionDispatcherAndWorkflowPublishQueuedStartedTerminalInOrderAndReplay() throws Exception {
        var job=admit("actor","lifecycle",Set.of(PausePointRegistry.Point.TOOL_IN_FLIGHT));await(()->observations().workflows.size()==1);
        var response=stream(job.jobId(),"actor",null);assertThat(response.statusCode()).isEqualTo(200);
        pauses().release(PausePointRegistry.Point.TOOL_IN_FLIGHT,job.jobId());
        var lines=new ArrayList<String>();
        try(var input=new BufferedReader(new InputStreamReader(response.body(),java.nio.charset.StandardCharsets.UTF_8))) {
            for(String line;(line=input.readLine())!=null;){lines.add(line);if(line.isEmpty()&&lines.stream().anyMatch(value->value.equals("event:job.completed")))break;}
        }
        assertThat(lines.stream().filter(value->value.startsWith("event:"))).containsExactly("event:job.queued","event:job.started","event:job.completed");
        assertThat(String.join("\n",lines)).doesNotContain("answer","inputQuestion","requestFields","질문");
        var events=context.getBean(JobEventRepository.class).findAfter(scope("actor"),job.jobId(),0);assertThat(events.stream().map(JobEventRepository.Event::type)).containsExactly("job.queued","job.started","job.completed");
        var resumed=stream(job.jobId(),"actor",Long.toString(events.get(1).id()));assertThat(resumed.statusCode()).isEqualTo(200);
        var replayed=new ArrayList<String>();
        try(var input=new BufferedReader(new InputStreamReader(resumed.body(),java.nio.charset.StandardCharsets.UTF_8))) {
            for(String line;(line=input.readLine())!=null;){replayed.add(line);if(line.isEmpty()&&replayed.stream().anyMatch(value->value.equals("event:job.completed")))break;}
        }
        assertThat(replayed.stream().filter(value->value.startsWith("event:"))).containsExactly("event:job.completed");
    }
    @Test @Tag("ISO-08-CONNECT") @Tag("ISO-08-RECONNECT") @Tag("ISO-08-EVENT")
    void replayValidatesExactJobActorAndCursorAndNeverLeaksSuppressedOrAnswerFields() throws Exception {
        pauses().hold(PausePointRegistry.Point.DISPATCH_HOLD,"*");var first=accept("actor","first","질문",null,dlg());var other=accept("other","other","질문",null,dlg());
        var events=context.getBean(JobEventRepository.class);
        assertThat(events.insert(scope("actor"),first.jobId(),"tool.started","{\"toolId\":\"tc.test\",\"answer\":\"PRIVATE\",\"status\":\"RUNNING\"}",false)).isTrue();
        assertThat(events.insert(scope("actor"),first.jobId(),"job.cancelled","{\"answer\":\"SUPPRESSED\"}",true)).isTrue();
        var rows=events.findAfter(scope("actor"),first.jobId(),0);String firstId=Long.toString(rows.getFirst().id());
        var replay=context.getBean(EventReplayService.class);assertThat(replay.cursor(scope("actor"),first.jobId(),firstId)).isEqualTo(rows.getFirst().id());
        assertThatThrownBy(()->replay.cursor(scope("actor"),other.jobId(),firstId)).isInstanceOf(UniformNotFound.class);
        assertThatThrownBy(()->replay.cursor(scope("other"),first.jobId(),firstId)).isInstanceOf(UniformNotFound.class);
        for(String cursor:List.of("bad","0",Long.toString(events.findAfter(scope("other"),other.jobId(),0).getFirst().id()))) {
            var response=stream(first.jobId(),"actor",cursor);assertThat(response.statusCode()).isEqualTo(404);try(var body=response.body()){assertThat(body.readAllBytes()).isEmpty();}
        }
        assertThat(stream(first.jobId(),"other",null).statusCode()).isEqualTo(404);
        var response=stream(first.jobId(),"actor",firstId);assertThat(response.statusCode()).isEqualTo(200);
        try(var input=new BufferedReader(new InputStreamReader(response.body(),java.nio.charset.StandardCharsets.UTF_8))) {
            var lines=new ArrayList<String>();for(String line;(line=input.readLine())!=null;) {lines.add(line);if(line.isEmpty()&&String.join("\n",lines).contains("tool.started"))break;}
            assertThat(String.join("\n",lines)).contains("tool.started","tc.test").doesNotContain("PRIVATE","SUPPRESSED","answer");
        }
    }
    private HttpResponse<InputStream> stream(String job,String actor,String cursor)throws Exception {
        var builder=HttpRequest.newBuilder(uri("/v1/analyses/"+job+"/events")).timeout(java.time.Duration.ofSeconds(10))
            .header("Authorization","Bearer "+serviceToken).header("X-Actor-Assertion",signer.sign(claims(actor,null),clock().instant()));
        if(cursor!=null)builder.header("Last-Event-ID",cursor);return HttpClient.newHttpClient().send(builder.GET().build(),HttpResponse.BodyHandlers.ofInputStream());
    }
}
