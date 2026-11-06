package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.app.security.ServiceTokenAuthFilter;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.persistence.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@TestConfiguration(proxyBeanMethods=false)
@Import(P09AdmissionEndpoint.Endpoint.class)
public class P09AdmissionEndpoint {
    @RestController
    public static class Endpoint {
        @org.springframework.beans.factory.annotation.Autowired private P09TestPorts.Observations observations;
        @GetMapping("/__test/harness-observations") public Map<String,Integer> observations(){return Map.of("authorization",observations.authorization.get(),"workflows",observations.workflows.size());}
        private final JobAdmissionService admission;private final JobRepository jobs;private final ResultRepository results;private final JobCancellationService cancellations;
        public Endpoint(JobAdmissionService admission,JobRepository jobs,ResultRepository results,JobCancellationService cancellations){this.admission=admission;this.jobs=jobs;this.results=results;this.cancellations=cancellations;}
        @PostMapping("/v1/query") public ResponseEntity<?> admit(HttpServletRequest request,@RequestBody Map<String,Object> body){
            var accepted=admission.accept(scope(request),new JobAdmissionService.AdmissionRequest("/v1/query",(String)body.get("conversationId"),(String)body.get("query"),Map.of(),request.getHeader("Idempotency-Key"),(String)request.getAttribute(ServiceTokenAuthFilter.VERIFIED_DELEGATION_ATTRIBUTE)));
            return ResponseEntity.accepted().body(accepted);
        }
        @GetMapping("/v1/analyses/{id}") public ResponseEntity<?> find(HttpServletRequest request,@PathVariable String id){
            var job=jobs.find(scope(request),id);if(job.isEmpty())return ResponseEntity.notFound().build();
            var payload=new LinkedHashMap<String,Object>();payload.put("jobId",id);payload.put("jobStatus",job.get().status());
            payload.put("result",results.find(scope(request),id).orElse(null));return ResponseEntity.ok(payload);
        }
        @PostMapping("/v1/analyses/{id}/cancel") public ResponseEntity<?> cancel(HttpServletRequest request,@PathVariable String id){return cancellations.cancel(scope(request),id)?ResponseEntity.noContent().build():ResponseEntity.notFound().build();}
        @ExceptionHandler(JobAdmissionService.Rejected.class) public ResponseEntity<?> rejected(JobAdmissionService.Rejected failure){
            var response=ResponseEntity.status(failure.status());if(failure.retryAfter()>0)response.header("Retry-After",Integer.toString(failure.retryAfter()));
            return failure.error()==null?response.build():response.body(Map.of("error",failure.error()));
        }
        @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> internal(){return ResponseEntity.internalServerError().body(Map.of("message","요청을 처리할 수 없습니다"));}
        private VerifiedSystemScope scope(HttpServletRequest request){return (VerifiedSystemScope)Objects.requireNonNull(request.getAttribute(ServiceTokenAuthFilter.VERIFIED_SCOPE_ATTRIBUTE));}
    }
}
