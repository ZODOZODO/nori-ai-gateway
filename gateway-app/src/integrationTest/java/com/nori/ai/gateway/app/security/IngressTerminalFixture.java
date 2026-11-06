package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.persistence.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@TestConfiguration(proxyBeanMethods = false)
@Import({IngressTerminalFixture.Terminal.class,IngressTerminalFixture.Counters.class})
public class IngressTerminalFixture {
    public static class Counters {
        final AtomicInteger terminal = new AtomicInteger(), downstreamJob = new AtomicInteger(), source = new AtomicInteger(),
            cache = new AtomicInteger(), tool = new AtomicInteger(), llm = new AtomicInteger(), roundTrips = new AtomicInteger();
        void reset() { for (var counter : List.of(terminal,downstreamJob,source,cache,tool,llm,roundTrips)) counter.set(0); }
        int downstream() { return terminal.get() + downstreamJob.get() + source.get() + cache.get() + tool.get() + llm.get() + roundTrips.get(); }
    }
    @RestController
    public static class Terminal {
        private final ConversationRepository conversations;
        private final JobRepository jobs;
        private final CacheRepository cache;
        private final Clock clock;
        private final Counters counters;
        Terminal(ConversationRepository conversations,JobRepository jobs,CacheRepository cache,Clock clock,Counters counters) {
            this.conversations = conversations; this.jobs = jobs; this.cache = cache; this.clock = clock; this.counters = counters;
        }
        @PostMapping({"/v1/query","/v1/conversations/{id}/messages","/v1/analyses"})
        ResponseEntity<Map<String,Object>> job(HttpServletRequest request,@RequestBody Map<String,Object> body) { return terminal(request,body,true); }
        @PostMapping("/v1/conversations")
        ResponseEntity<Map<String,Object>> conversation(HttpServletRequest request,@RequestBody Map<String,Object> body) { return terminal(request,body,false); }
        private ResponseEntity<Map<String,Object>> terminal(HttpServletRequest request,Map<String,Object> body,boolean job) {
            counters.terminal.incrementAndGet();
            var scope = (VerifiedSystemScope)Objects.requireNonNull(request.getAttribute(ServiceTokenAuthFilter.VERIFIED_SCOPE_ATTRIBUTE));
            String conversation = UUID.randomUUID().toString(); String jobId = UUID.randomUUID().toString();
            if (job) counters.downstreamJob.incrementAndGet();
            if (!conversations.insert(scope,conversation) || conversations.find(scope,conversation).isEmpty()) throw new IllegalStateException("시험 대화 왕복에 실패했습니다");
            if (job) {
                String delegation = (String)Objects.requireNonNull(request.getAttribute(ServiceTokenAuthFilter.VERIFIED_DELEGATION_ATTRIBUTE));
                if (!jobs.insert(scope,new JobRepository.NewJob(jobId,conversation,null,"fixture","B"),delegation) || jobs.find(scope,jobId).isEmpty()) throw new IllegalStateException("시험 작업 왕복에 실패했습니다");
            }
            counters.source.incrementAndGet();
            if (!cache.put(scope,conversation,"fixture",new CacheRepository.Entry("fixture","{}","[]",clock.instant().plusSeconds(30)))) throw new IllegalStateException("시험 캐시 저장에 실패했습니다");
            counters.cache.incrementAndGet();
            if (cache.find(scope,conversation,"fixture").isEmpty()) throw new IllegalStateException("시험 캐시 조회에 실패했습니다");
            counters.roundTrips.incrementAndGet();
            return ResponseEntity.accepted().body(Map.of("bodyRead",Objects.equals(body.get("query"),"본문 재독 확인"),"conversationId",conversation,"jobCreated",job));
        }
    }
}
