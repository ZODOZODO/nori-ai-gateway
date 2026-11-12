package com.nori.ai.gateway.app.hooks;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 관찰 수단(Plan P20 N10 — AC-24-RESULTSCOPE-TURN·ISO-14-USER·AC-24-RETAIN-EXPIRY). 무장 requestKey 의 결속 Job 에 대해
 * {@code {callType, sentinelIndex, matchCount}} 목록만 준다. Prompt 원문·부분 문자열은 반환하지 않는다.
 */
@RestController
@RequestMapping("/__test")
public final class PromptWatchHook {
    private final TestHookRegistry hooks;
    private final LlmScriptRegistry scripts;

    public PromptWatchHook(TestHookRegistry hooks, LlmScriptRegistry scripts) { this.hooks = hooks; this.scripts = scripts; }

    @GetMapping("/prompt-watch/{requestKey}")
    public ResponseEntity<List<Map<String,Object>>> watch(@PathVariable String requestKey) {
        var jobs = hooks.jobsForRequestKey(requestKey);
        if (jobs.size() != 1) return ResponseEntity.notFound().build();
        return scripts.promptWatch(jobs.getFirst())
            .map(rows -> ResponseEntity.ok(rows.stream().map(row -> Map.<String,Object>of("callType", row.callType().name(), "sentinelIndex", row.sentinelIndex(), "matchCount", row.matchCount())).toList()))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
