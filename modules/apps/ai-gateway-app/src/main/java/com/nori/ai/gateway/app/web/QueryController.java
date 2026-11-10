package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.app.job.JobAdmissionService;
import com.nori.ai.gateway.app.security.ServiceTokenAuthFilter;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public final class QueryController {
    private final JobAdmissionService admission;
    private final ContentSanitizer sanitizer;
    public QueryController(JobAdmissionService admission,ContentSanitizer sanitizer) { this.admission=admission;this.sanitizer=sanitizer; }
    @PostMapping("/v1/query")
    public ResponseEntity<JobAdmissionService.Accepted> query(HttpServletRequest request,@RequestBody Map<String,Object> body) { return accept(request,body,null); }
    ResponseEntity<JobAdmissionService.Accepted> accept(HttpServletRequest request,Map<String,Object> body,String conversation) {
        var scope=ApiRequestInput.scope(request);
        if(conversation==null&&body.get("conversationId")!=null)conversation=ApiRequestInput.text(body.get("conversationId"));
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return ResponseEntity.accepted().body(admission.accept(scope,new JobAdmissionService.AdmissionRequest(path,conversation,
            ApiRequestInput.text(body.get("query")),ApiRequestInput.fields(body,scope,sanitizer),request.getHeader("Idempotency-Key"),
            (String)request.getAttribute(ServiceTokenAuthFilter.VERIFIED_DELEGATION_ATTRIBUTE))));
    }
}
