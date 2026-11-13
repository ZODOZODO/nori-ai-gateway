package com.nori.ai.gateway.acceptance.suite;

import com.nori.ai.gateway.acceptance.F2Runtime;
import org.junit.jupiter.api.*;

/**
 * F2 첫 suite(Plan P21 ①). 순서 고정: LLM_LIVE 지연·비용 leaf → 그 Job 의 원시 지연 표본(MEASUREMENT, leaf 아님) → LLM_STUB leaf → egress → readiness stub leaf.
 * readiness stub leaf 는 추론 서버를 건드리지 않고 Gateway 의 판정 입력만 고정했다가 OFF 로 되돌린다. STUB PASS 를 LIVE 로 대신하지 않는다.
 */
@Tag("suite-F2") @Tag("stage-F2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public final class F2Suite {
    @Test @Order(1) @Tag("AC-22-LIVE") @Tag("label-MOCK_API") @Tag("label-LLM_LIVE") void ac_22_live() { F2Runtime.run("AC-22-LIVE"); }
    @Test @Order(2) @Tag("AC-29") @Tag("label-MOCK_API") @Tag("label-LLM_LIVE") void ac_29() { F2Runtime.run("AC-29"); }
    @Test @Order(3) @Tag("AC-38-F2") @Tag("label-MOCK_API") @Tag("label-LLM_LIVE") void ac_38_f2() { F2Runtime.run("AC-38-F2"); }
    @Test @Order(4) @Tag("AC-31") @Tag("label-MOCK_API") @Tag("label-LLM_LIVE") void ac_31() { F2Runtime.run("AC-31"); }
    @Test @Order(5) @Tag("measurement-f2-llm-latency-samples") void f2LlmLatencySamples() { F2Runtime.recordLatencySamples(); }
    @Test @Order(6) @Tag("AC-22-STUB-TOOL") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_22_stub_tool() { F2Runtime.run("AC-22-STUB-TOOL"); }
    @Test @Order(7) @Tag("AC-22-STUB-SYSTEM") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_22_stub_system() { F2Runtime.run("AC-22-STUB-SYSTEM"); }
    @Test @Order(8) @Tag("AC-22-GQ-HELP") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_22_gq_help() { F2Runtime.run("AC-22-GQ-HELP"); }
    @Test @Order(9) @Tag("AC-22-GQ-BYPASS-DATA") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_22_gq_bypass_data() { F2Runtime.run("AC-22-GQ-BYPASS-DATA"); }
    @Test @Order(10) @Tag("AC-22-GQ-BYPASS-XSYS") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_22_gq_bypass_xsys() { F2Runtime.run("AC-22-GQ-BYPASS-XSYS"); }
    @Test @Order(11) @Tag("AC-30-EQ") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_30_eq() { F2Runtime.run("AC-30-EQ"); }
    @Test @Order(12) @Tag("AC-30-WM") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_30_wm() { F2Runtime.run("AC-30-WM"); }
    @Test @Order(13) @Tag("AC-30-ABSENT") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_30_absent() { F2Runtime.run("AC-30-ABSENT"); }
    @Test @Order(14) @Tag("AC-32-SCHEMA") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_32_schema() { F2Runtime.run("AC-32-SCHEMA"); }
    @Test @Order(15) @Tag("AC-32-LENGTH") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_32_length() { F2Runtime.run("AC-32-LENGTH"); }
    @Test @Order(16) @Tag("AC-32-RETRYOK") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_32_retryok() { F2Runtime.run("AC-32-RETRYOK"); }
    @Test @Order(17) @Tag("AC-32-NEEDS") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_32_needs() { F2Runtime.run("AC-32-NEEDS"); }
    @Test @Order(18) @Tag("AC-43-CLS") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_43_cls() { F2Runtime.run("AC-43-CLS"); }
    @Test @Order(19) @Tag("AC-43-COMP") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_43_comp() { F2Runtime.run("AC-43-COMP"); }
    @Test @Order(20) @Tag("AC-35-EGRESS") @Tag("label-MOCK_API") void ac_35_egress() { F2Runtime.run("AC-35-EGRESS"); }
    @Test @Order(21) @Tag("AC-33-CTX") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_33_ctx() { F2Runtime.run("AC-33-CTX"); }
    @Test @Order(22) @Tag("AC-33-DIGEST") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_33_digest() { F2Runtime.run("AC-33-DIGEST"); }
    @Test @Order(23) @Tag("AC-33-PERSIST") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_33_persist() { F2Runtime.run("AC-33-PERSIST"); }
}
