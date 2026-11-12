package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.result.ErrorCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class QRefContextTest {
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private static ErrorCode rejected(String question) {
        return catchThrowableOfType(QRefContext.Rejected.class, () -> QRefContext.extract(question)).code();
    }

    @Test void ordinaryQuoteKeepsBackslashesAsWrittenAndJsonLiteralDecodesOnceIncludingEscapedQuoteBackslashAndControl() {
        var refs = QRefContext.extract("로그에서 \"C:\\temp\\new\" 와 json:\"say \\\"hi\\\" \\\\ tab\\tend\\u0007\" 찾아줘");
        assertThat(refs.catalog()).containsExactly("Q1", "Q2");
        assertThat(refs.resolve(NODES.stringNode("Q1"))).isEqualTo("C:\\temp\\new").doesNotContain("\t");
        assertThat(refs.resolve(NODES.stringNode("Q2"))).isEqualTo("say \"hi\" \\ tab\tend\u0007");
    }

    @Test void emptyUnclosedInvalidEscapeRawControlAndUnpairedSurrogatesAreRejectedBeforeClassification() {
        assertThat(rejected("빈 \"\" 검색")).isEqualTo(ErrorCode.PLAN_INVALID);
        assertThat(rejected("닫힘 없는 \"검색어")).isEqualTo(ErrorCode.PLAN_INVALID);
        assertThat(rejected("json:\"bad \\q escape\"")).isEqualTo(ErrorCode.PLAN_INVALID);
        assertThat(rejected("json:\"raw\u0001control\"")).isEqualTo(ErrorCode.PLAN_INVALID);
        assertThat(rejected("json:\"lone \\ud800 surrogate\"")).isEqualTo(ErrorCode.PLAN_INVALID);
        assertThat(rejected("인용구 밖 \ud800 짝 없음")).isEqualTo(ErrorCode.PLAN_INVALID);
        assertThat(rejected("json:\"\"")).isEqualTo(ErrorCode.PLAN_INVALID);
    }

    @Test void supplementaryPlaneCountsCodepointsSo256IsAcceptedAnd257IsBudgetExceeded() {
        String emoji = new String(Character.toChars(0x1F600));
        String max = emoji.repeat(256), over = emoji.repeat(257);
        var refs = QRefContext.extract("\"" + max + "\"");
        assertThat(refs.resolve(NODES.stringNode("Q1"))).isEqualTo(max).hasSize(512);
        assertThat(rejected("\"" + over + "\"")).isEqualTo(ErrorCode.INPUT_BUDGET_EXCEEDED);
        // json: 표기의 짝 surrogate escape 도 같은 보조 평면 코드포인트로 복원하고 디코드 뒤 길이로 센다.
        var decoded = QRefContext.extract("json:\"" + "\\ud83d\\ude00".repeat(256) + "\"");
        assertThat(decoded.resolve(NODES.stringNode("Q1"))).isEqualTo(max);
        assertThat(rejected("json:\"" + "\\ud83d\\ude00".repeat(257) + "\"")).isEqualTo(ErrorCode.INPUT_BUDGET_EXCEEDED);
    }

    @Test void repeatedValuesKeepOneReferencePerOccurrenceAndTheCatalogCarriesNoOriginal() {
        var refs = QRefContext.extract("\"TIMEOUT\" 다음 \"TIMEOUT\" 그리고 \"RETRY\"");
        assertThat(refs.catalog()).containsExactly("Q1", "Q2", "Q3").noneMatch(id -> id.contains("TIMEOUT"));
        assertThat(List.of(refs.resolve(NODES.stringNode("Q1")), refs.resolve(NODES.stringNode("Q2")), refs.resolve(NODES.stringNode("Q3")))).containsExactly("TIMEOUT", "TIMEOUT", "RETRY");
        assertThat(refs.toString()).doesNotContain("TIMEOUT");
    }

    @Test void nullIsOnlyForNoCandidatesAndForgedOrForeignReferencesAreNotAccepted() {
        var none = QRefContext.extract("후보 없는 질문");
        assertThat(none.accepts(NODES.nullNode())).isTrue();
        assertThat(none.accepts(NODES.stringNode("Q1"))).isFalse();
        var two = QRefContext.extract("\"a\" \"b\"");
        assertThat(two.accepts(NODES.nullNode())).isFalse();
        for (String forged : List.of("Q0", "Q3", "q1", "Q01", "Q1 ", "Q99999999999", "a")) assertThat(two.accepts(NODES.stringNode(forged))).as(forged).isFalse();
        assertThat(two.accepts(NODES.numberNode(1))).isFalse();
        assertThatThrownBy(() -> two.resolve(NODES.stringNode("Q3"))).isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("a");
    }

    @Test void theSameIdInAnotherRequestResolvesOnlyAgainstItsOwnBinding() {
        var first = QRefContext.extract("\"first-request\"");
        var second = QRefContext.extract("\"second-request\"");
        assertThat(first.resolve(NODES.stringNode("Q1"))).isEqualTo("first-request");
        assertThat(second.resolve(NODES.stringNode("Q1"))).isEqualTo("second-request");
        assertThat(first.boundTo("\"second-request\"")).isFalse();
    }
}
