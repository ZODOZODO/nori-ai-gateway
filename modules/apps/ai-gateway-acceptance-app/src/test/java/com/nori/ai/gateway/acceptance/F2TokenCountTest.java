package com.nori.ai.gateway.acceptance;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** AC-32-NEEDS ⓞ 의 하네스 계수가 수용 배포본 classpath 의 pin tokenizer 로 실제 동작하는지 확인한다(원격 다운로드 0). */
class F2TokenCountTest {
    @Test void maximumClassificationWireIsCountedWithThePinnedTokenizerWithinTheAnswerLimit() {
        String eqp = "설비\"경계\\" + "가".repeat(54) + "EQP0";
        var node = CanonicalJson.MAPPER.createObjectNode().put("t", "INC").put("eqp", eqp).put("from", "2026-10-03T00:00:00.123456789+09:00").put("to", "2026-10-04T00:00:00.123456789+09:00").put("qRef", "Q1");
        var codes = node.putArray("n"); List.of("S", "H", "N", "I", "C", "R").forEach(codes::add); node.put("a", "D");
        assertThat(eqp.codePointCount(0, eqp.length())).isEqualTo(64);
        var count = F2Runtime.countTokens(node.toString());
        assertThat(count).hasValueSatisfying(value -> assertThat(value).isBetween(1, 256));
        assertThat(F2Runtime.countTokens("EQP-001")).hasValueSatisfying(value -> assertThat(value).isLessThan(count.orElseThrow()));
    }
}
