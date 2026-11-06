package com.nori.ai.gateway.app.ops;

import com.nori.ai.gateway.app.hooks.TestHookRegistry;
import com.nori.ai.gateway.app.job.JobAdmissionService;
import com.nori.ai.gateway.app.job.P09QueueSupport;
import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.tcmock.MockDelegationRegistry;
import com.nori.ai.gateway.tcmock.verify.ResidueScanner;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.assertj.core.api.Assertions.*;

/** AC-41 로컬 구성 요소: Job 1건 terminal 뒤 DB 전 테이블·로그·감사·이벤트·응답·Evidence에 위임 sentinel이 남지 않는지 검사한다. */
@Tag("stage-F1") @Tag("label-MOCK_API")
@ExtendWith(OutputCaptureExtension.class)
class ResidueIT extends P09QueueSupport {
    @Test @Tag("AC-41")
    void terminalJobLeavesNoDelegationSentinelInAnyStoredOrEmittedLocation(CapturedOutput output) throws Exception {
        String delegation = dlg();
        hooks().arm("actor", "residue", new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY, Set.of(), null, false));
        context.getBean(MockDelegationRegistry.class).register("actor", delegation);
        var accepted = inRequestSpan(()->context.getBean(JobAdmissionService.class).accept(scope("actor"), new JobAdmissionService.AdmissionRequest("/v1/query", null, "질문", Map.of(), "residue", delegation)));
        await(() -> !Set.of("QUEUED", "RUNNING").contains(state(accepted.jobId())));
        var sources = new TreeMap<String,String>();
        for (String table : jdbc().sql("SELECT table_schema || '.' || table_name FROM information_schema.tables WHERE table_schema IN ('gw','kn') AND table_type = 'BASE TABLE' ORDER BY 1").query(String.class).list())
            sources.put("db:" + table, jdbc().sql("SELECT coalesce(string_agg(t::text, E'\\n'), '') FROM " + table + " t").query(String.class).single());
        assertThat(sources).containsKeys("db:gw.job", "db:gw.job_event", "db:gw.audit_event", "db:gw.evidence", "db:gw.result");
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/v1/analyses/" + accepted.jobId())).header("Authorization", "Bearer " + serviceToken)
            .header("X-Actor-Assertion", signer.sign(claims("actor", null), clock().instant())).GET().build(), HttpResponse.BodyHandlers.ofString());
        sources.put("response:analysis", response.statusCode() + " " + response.body());
        sources.put("log:captured", output.getAll());
        var report = ResidueScanner.scan(sources, Set.of(delegation));
        assertThat(report.matchCount()).as(report.toString()).isZero();

        // 검사기가 실제 인코딩 경로를 찾는지 같은 sentinel의 양성 대조로 확인합니다.
        String jwsPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"dlg\":\"" + delegation + "\"}").getBytes(StandardCharsets.UTF_8));
        var control = ResidueScanner.scan(Map.of("jws", "eyJhbGciOiJFUzI1NiJ9." + jwsPayload + ".c2lnbmF0dXJl", "url", "a=" + URLEncoder.encode("x " + delegation, StandardCharsets.UTF_8),
            "json", "{\"value\":\"" + delegation + "\"}"), Set.of(delegation));
        assertThat(control.matches()).extracting(ResidueScanner.Match::location).contains("jws", "url", "json");
    }
}
