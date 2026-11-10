package com.nori.ai.gateway.app.web;

import com.nori.ai.gateway.app.job.JobAdmissionService;
import com.nori.ai.gateway.core.expiry.ConversationExpiryGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(basePackages="com.nori.ai.gateway.app.web")
public final class ApiExceptionHandler {
    @ExceptionHandler({UniformNotFound.class,ConversationExpiryGuard.ConversationExpired.class})
    ResponseEntity<Void> notFound() { return ResponseEntity.notFound().build(); }
    @ExceptionHandler(JobAdmissionService.Rejected.class)
    ResponseEntity<?> rejected(JobAdmissionService.Rejected failure) {
        var response=ResponseEntity.status(failure.status());
        if(failure.retryAfter()>0)response.header("Retry-After",Integer.toString(failure.retryAfter()));
        return failure.error()==null?response.build():response.body(Map.of("error",failure.error()));
    }
    @ExceptionHandler({IllegalArgumentException.class,java.time.DateTimeException.class})
    ResponseEntity<?> invalid() { return ResponseEntity.badRequest().body(Map.of("message","요청 입력을 확인해 주세요")); }
    @ExceptionHandler({IllegalStateException.class,org.springframework.dao.DataAccessException.class})
    ResponseEntity<?> failed() { return ResponseEntity.internalServerError().body(Map.of("message","요청을 처리할 수 없습니다")); }
}
