package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.app.job.JobCancellationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public final class AnalysisController {
    private final QueryController query;private final ResultResponseAssembler responses;private final JobCancellationService cancellations;
    public AnalysisController(QueryController query,ResultResponseAssembler responses,JobCancellationService cancellations) { this.query=query;this.responses=responses;this.cancellations=cancellations; }
    @PostMapping("/v1/analyses") public ResponseEntity<?> create(HttpServletRequest request,@RequestBody Map<String,Object> body) { return query.accept(request,body,null); }
    @GetMapping("/v1/analyses/{jobId}") public Map<String,Object> find(HttpServletRequest request,@PathVariable String jobId) {
        var access=responses.assemble(ApiRequestInput.scope(request),jobId);responses.assertLive(access);return access.response();
    }
    @PostMapping("/v1/analyses/{jobId}/cancel") public ResponseEntity<Void> cancel(HttpServletRequest request,@PathVariable String jobId) {
        var scope=ApiRequestInput.scope(request);var access=responses.assemble(scope,jobId);responses.assertLive(access);
        if(!cancellations.cancel(scope,jobId))throw new UniformNotFound();responses.assertLive(access);return ResponseEntity.noContent().build();
    }
}
