package com.nori.ai.gateway.app.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public final class JobEventStreamController {
    private final ResultResponseAssembler responses;private final SseHub hub;
    public JobEventStreamController(ResultResponseAssembler responses,SseHub hub) {this.responses=responses;this.hub=hub;}
    @GetMapping(value="/v1/analyses/{jobId}/events",produces="text/event-stream")
    public SseEmitter events(HttpServletRequest request,@PathVariable String jobId,@RequestHeader(value="Last-Event-ID",required=false)String lastEventId) {
        var scope=ApiRequestInput.scope(request);var access=responses.assemble(scope,jobId);responses.assertLive(access);
        return hub.connect(scope,jobId,access.conversation().expiresAt(),lastEventId);
    }
}
