package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.persistence.result.JobAuditReader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public final class AuditQueryController {
    private final ResultResponseAssembler responses;private final JobAuditReader audit;
    public AuditQueryController(ResultResponseAssembler responses,JobAuditReader audit) { this.responses=responses;this.audit=audit; }
    @GetMapping("/v1/audit/jobs/{jobId}") public Object find(HttpServletRequest request,@PathVariable String jobId) {
        var scope=ApiRequestInput.scope(request);var access=responses.assemble(scope,jobId);
        if(!access.allowed()){responses.assertLive(access);return access.response();}
        var result=audit.find(scope,jobId);responses.assertLive(access);return result;
    }
}
