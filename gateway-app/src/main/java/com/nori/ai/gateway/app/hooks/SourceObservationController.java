package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.persistence.queue.QueueJobRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;

/** 기존 hook 토큰 필터 안에서 완료된 실제 Job의 건수·digest만 반환합니다. */
@RestController
@org.springframework.context.annotation.Profile("!prod")
public final class SourceObservationController {
    private final SourceRequestProbe probe;private final QueueJobRepository jobs;
    public SourceObservationController(SourceRequestProbe probe,QueueJobRepository jobs){this.probe=probe;this.jobs=jobs;}
    @GetMapping("/__test/source-observations/{jobId}") public ResponseEntity<?> read(@PathVariable String jobId){
        if(!jobId.matches("[A-Za-z0-9_:-]{1,160}"))return ResponseEntity.badRequest().build();
        var snapshot=probe.snapshot(jobId);if(snapshot.status().equals("MISSING"))return ResponseEntity.notFound().build();
        if(jobs.nonterminal().stream().anyMatch(job->job.jobId().equals(jobId)))return ResponseEntity.status(409).body(Map.of("jobId",jobId,"status","RUNNING"));
        return ResponseEntity.ok(snapshot);
    }
}
