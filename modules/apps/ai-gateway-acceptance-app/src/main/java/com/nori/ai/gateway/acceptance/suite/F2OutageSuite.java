package com.nori.ai.gateway.acceptance.suite;

import com.nori.ai.gateway.acceptance.F2Runtime;
import org.junit.jupiter.api.*;

/**
 * suite {@code F2-OUTAGE}(Plan P21 ⓑ·ⓒ, ① 의 LIVE 측정 뒤). 한 회차는 {@code AIGW_OUTAGE_PHASE} 하나만 실행한다 — launcher 가 {@code outage-<phase>} 태그로 고른다.
 * 둘 다 Gateway readiness stub hook 수단(라벨 LLM_STUB)이며 추론 서버를 재시작·중지하지 않는다.
 */
@Tag("suite-F2-OUTAGE") @Tag("stage-F2")
public final class F2OutageSuite {
    @Test @Tag("AC-44") @Tag("outage-STOPPED") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_44() { F2Runtime.run("AC-44"); }
    @Test @Tag("AC-33-LOADING") @Tag("outage-LOADING") @Tag("label-MOCK_API") @Tag("label-LLM_STUB") void ac_33_loading() { F2Runtime.run("AC-33-LOADING"); }
}
