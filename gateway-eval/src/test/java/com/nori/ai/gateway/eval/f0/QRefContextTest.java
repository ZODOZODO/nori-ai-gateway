package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.EvalException;
import com.nori.ai.gateway.eval.io.Json;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class QRefContextTest {
    static ObjectNode output(String ref) {
        return Json.MAPPER.createObjectNode().put("t", "INC").putNull("eqp").putNull("from").putNull("to").put("qRef", ref);
    }
    static ClassificationJudge.Verdict judge(JsonNode value, QRefContext references) {
        return ClassificationJudge.judge(F0ArgumentContractTest.response(value), 128, MessageAssembler.Kind.CLASSIFY, true, "+09:00", references);
    }
    @Test void ordinaryPathsAreRawAndExplicitJsonPreservesNestedEscapingAndCodepoints() {
        String path = "C:\\temp\\new\\u0001";
        QRefContext raw = QRefContext.extract("검색 \"" + path + "\"");
        assertThat(judge(output("Q1"), raw).args().get("q")).isEqualTo(path);
        String complex = "\"\\\u0001😀".repeat(40);
        QRefContext encoded = QRefContext.extract("검색 json:" + Json.canonicalString(Json.MAPPER.valueToTree(complex)));
        assertThat(judge(output("Q1"), encoded).args().get("q")).isEqualTo(complex);
        assertThat(encoded.toString()).doesNotContain(complex);
    }
    @Test void referencesResolveOnlyWithinTheCurrentRequestAndEveryOccurrenceIsSelectable() {
        QRefContext first = QRefContext.extract("\"first\" 또는 \"second\" 또는 \"first\"");
        assertThat(judge(output("Q2"), first).args().get("q")).isEqualTo("second");
        assertThat(judge(output("Q3"), first).args().get("q")).isEqualTo("first");
        QRefContext next = QRefContext.extract("\"next\"");
        assertThat(judge(output("Q1"), next).args().get("q")).isEqualTo("next");
        assertThat(judge(output("Q2"), next).errorCode()).isEqualTo("QREF_INVALID");
        assertThat(judge(output(null), first).valid()).isFalse();
        assertThat(judge(output(null), QRefContext.extract("없음")).args()).containsEntry("q", null);
        for (String invalid : List.of("Q0", "Q01", "q1", "Q999999999999999", "invented")) assertThat(judge(output(invalid), next).valid()).isFalse();
        assertThat(judge(output("Q1"), QRefContext.extract("없음")).valid()).isFalse();
        assertThat(judge(output("Q1").put("q", "raw"), next).errorCode()).isEqualTo("SCHEMA_KEYS_INVALID");
        ObjectNode omitted = output("Q1"); omitted.remove("qRef");
        assertThat(judge(omitted, next).errorCode()).isEqualTo("SCHEMA_KEYS_INVALID");
        assertThat(judge(output("Q1").put("qRef", 1), next).valid()).isFalse();
    }
    @Test void malformedEmptyOversizedAndUnpairedUnicodeInputsFailBeforeAnyModelCall() {
        for (String input : List.of("\"unfinished", "\"\"", "json:\"\\z\"", "json:\"\u0001\"", "json:\"\\ud800\"", "\"" + "😀".repeat(257) + "\""))
            assertThatThrownBy(() -> QRefContext.extract(input)).isInstanceOf(EvalException.class);
        assertThat(judge(output("Q1"), QRefContext.extract("\"" + "😀".repeat(256) + "\"")).args().get("q")).isEqualTo("😀".repeat(256));
    }
    @Test void versionedCallsCannotFallBackToLegacyRawQueryWithoutARequestBinding() {
        assertThat(ClassificationJudge.judge(F0ArgumentContractTest.response(F0ArgumentContractTest.output(null, null, "raw")), 128, MessageAssembler.Kind.CLASSIFY, true, "+09:00").errorCode()).isEqualTo("QREF_REQUEST_MISSING");
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> ClassificationJudge.execute(messages -> { calls.incrementAndGet(); return F0ArgumentContractTest.response(output("Q1")); }, List.of(), 128, MessageAssembler.Kind.CLASSIFY, true, "+09:00")).isInstanceOf(EvalException.class);
        assertThat(calls).hasValue(0);
    }
    @Test void staleBindingsCannotBeAppliedToAnotherQuestionOrCatalogBeforeTheFirstCall() {
        QRefContext stale = QRefContext.extract("\"first\"");
        QRefContext current = QRefContext.extract("\"second\"");
        RegistryFixture fixture = RegistryFixture.load(java.nio.file.Path.of(System.getProperty("aigw.rootDir"), "eval/golden/tc-registry-fixture-v1.json"));
        MessageAssembler assembler = MessageAssembler.load(java.nio.file.Path.of(System.getProperty("aigw.rootDir"), "eval/f0/v2/prompts/classify-v2.txt"), fixture);
        assertThatThrownBy(() -> assembler.messages("\"second\"", Json.MAPPER.nullNode(), stale)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("QREF_REQUEST_MISMATCH"));
        List<Map<String,String>> messages = assembler.messages("\"second\"", Json.MAPPER.nullNode(), current);
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> ClassificationJudge.execute(actual -> { calls.incrementAndGet(); return F0ArgumentContractTest.response(output("Q1")); }, messages, 128, MessageAssembler.Kind.CLASSIFY, true, "+09:00", stale)).isInstanceOf(EvalException.class);
        assertThat(calls).hasValue(0);
    }
    @Test void retryUsesTheSameCatalogAndOnlyOneRetryWithoutRawResponseEcho() {
        QRefContext refs = QRefContext.extract("\"private-original\"");
        ObjectNode user = Json.MAPPER.createObjectNode().put("question", "\"private-original\""); user.set("qRefCatalog", refs.catalog());
        List<Map<String,String>> messages = List.of(Map.of("role", "user", "content", Json.canonicalString(user)));
        AtomicInteger calls = new AtomicInteger();
        ClassificationJudge.Outcome outcome = ClassificationJudge.execute(actual -> {
            assertThat(actual.getFirst()).isEqualTo(messages.getFirst());
            if (calls.getAndIncrement() == 0) return F0ArgumentContractTest.response(output("Q2"));
            assertThat(actual).hasSize(2);
            assertThat(actual.getLast().get("content")).doesNotContain("private-original", "Q2");
            return F0ArgumentContractTest.response(output("Q1"));
        }, messages, 128, MessageAssembler.Kind.CLASSIFY, true, "+09:00", refs);
        assertThat(calls).hasValue(2); assertThat(outcome.status()).isEqualTo("OK");
        assertThat(outcome.verdict().args().get("q")).isEqualTo("private-original");
        assertThat(outcome.errorCodes()).containsExactly("QREF_INVALID");
    }
    /** 요청 본문 JSON을 디코드한 뒤의 질문 문자열로 시험합니다(외부 \\uD800 escape가 실제 짝 없는 surrogate가 되는 경로). */
    static String decodedQuestion(String requestJson) { return Json.parse(requestJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)).path("question").textValue(); }
    @Test void unpairedSurrogatesFromDecodedRequestsAreRejectedWithoutLossyReplacementOrEcho() {
        String lone = decodedQuestion("{\"question\":\"검색 json:\\\"a\\ud800b\\\" 끝\"}");
        assertThat(lone).contains("\uD800");
        String lowOutside = decodedQuestion("{\"question\":\"검색 \\udc00 \\\"ok\\\"\"}");
        for (String input : List.of(lone, lowOutside, decodedQuestion("{\"question\":\"검색 \\\"a\\udc00\\\"\"}"),
                decodedQuestion("{\"question\":\"검색 json:\\\"\\\\ud800\\\"\"}"), decodedQuestion("{\"question\":\"검색 json:\\\"\\\\udc00x\\\"\"}")))
            assertThatThrownBy(() -> QRefContext.extract(input)).isInstanceOfSatisfying(EvalException.class, e -> {
                assertThat(e.getMessage()).isEqualTo("QUERY_LITERAL_INVALID"); assertThat(e.getMessage()).doesNotContain("검색");
            });
        // '?' 치환본과 같은 SHA로 다른 요청을 통과시키던 결속도 거부합니다.
        QRefContext replaced = QRefContext.extract("검색 json:\"a?b\" 끝");
        assertThatThrownBy(() -> replaced.requireQuestionMatch(lone)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("QREF_REQUEST_MISMATCH"));
        ObjectNode user = Json.MAPPER.createObjectNode().put("question", "검색 json:\"a?b\" 끝"); user.set("qRefCatalog", replaced.catalog());
        String content = Json.canonicalString(user).replace("a?b", "a\uD800b");
        assertThatThrownBy(() -> replaced.requireMessagesMatch(List.of(Map.of("role", "user", "content", content)))).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("QREF_REQUEST_MISMATCH"));
    }
    @Test void validPairsEscapedPairsAndRawBackslashesSurviveDecodedRequestsExactly() {
        assertThat(judge(output("Q1"), QRefContext.extract(decodedQuestion("{\"question\":\"검색 json:\\\"\\\\ud83d\\\\ude00가\\\"\"}"))).args().get("q")).isEqualTo("😀가");
        assertThat(judge(output("Q1"), QRefContext.extract(decodedQuestion("{\"question\":\"검색 \\\"\\ud83d\\ude00\\\"\"}"))).args().get("q")).isEqualTo("😀");
        assertThat(judge(output("Q1"), QRefContext.extract(decodedQuestion("{\"question\":\"검색 \\\"C:\\\\temp\\\\new\\\\u0041\\\"\"}"))).args().get("q")).isEqualTo("C:\\temp\\new\\u0041");
    }
    @Test void bindingsFromAnotherRequestOrCatalogAreRejectedEvenWithTheSameCandidateCount() {
        QRefContext first = QRefContext.extract("\"a\" 그리고 \"b\""), second = QRefContext.extract("\"c\" 그리고 \"d\"");
        ObjectNode user = Json.MAPPER.createObjectNode().put("question", "\"c\" 그리고 \"d\""); user.set("qRefCatalog", second.catalog());
        List<Map<String,String>> messages = List.of(Map.of("role", "user", "content", Json.canonicalString(user)));
        second.requireMessagesMatch(messages);
        assertThatThrownBy(() -> first.requireMessagesMatch(messages)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("QREF_REQUEST_MISMATCH"));
        user.set("qRefCatalog", Json.MAPPER.createArrayNode().add("Q1"));
        List<Map<String,String>> shrunk = List.of(Map.of("role", "user", "content", Json.canonicalString(user)));
        assertThatThrownBy(() -> second.requireMessagesMatch(shrunk)).isInstanceOfSatisfying(EvalException.class, e -> assertThat(e.getMessage()).isEqualTo("QREF_REQUEST_MISMATCH"));
    }
}
