package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/** 골든 기대값·경계 케이스 기대 JSON 으로 정답을 돌려주는 stub 응답기(LLM_STUB — 실측 아님). */
final class StubAnswers {
    private StubAnswers() {}
    private static boolean versioned(JsonNode body) {
        for (JsonNode message : body.path("messages")) {
            if (message.path("role").asText().equals("user") && message.path("content").asText().contains("\"qRefCatalog\"")) return true;
        }
        return false;
    }
    static Function<JsonNode,JsonNode> correct(Path root) {
        RegistryFixture fixture = RegistryFixture.load(root.resolve("eval/golden/tc-registry-fixture-v1.json"));
        GoldenSet golden = GoldenSet.load(root.resolve("eval/golden/tc-f0-v1.jsonl"), fixture);
        Map<String,String> cases = new HashMap<>();
        for (JsonNode c : BoundaryCases.load(root.resolve("eval/f0/output-boundary-cases.jsonl"))) cases.put(c.path("question").asText(), c.path("expectedJson").asText());
        return body -> {
            String question = OllamaStub.question(body);
            if (cases.containsKey(question)) {
                JsonNode answer = Json.parse(cases.get(question).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (versioned(body)) answer = QRefContext.extract(question).wireExpected(answer);
                return OllamaStub.chatResponse(Json.canonicalString(answer), "stop", 300, 40);
            }
            GoldenSet.Question q = golden.questions().stream().filter(x -> x.question().equals(question)).findFirst().orElse(null);
            if (q == null) return OllamaStub.chatResponse("{}", "stop", 300, 2);
            ObjectNode answer = Json.MAPPER.createObjectNode(); answer.put("t", q.expected().t());
            for (String f : GoldenSet.ARG_FIELDS) { String v = q.expected().field(f); if (v == null) answer.putNull(f); else answer.put(f, v); }
            if (body.path("max_tokens").asInt() == 160) { var steps = answer.putArray("steps"); fixture.stepsFor(q.expected().t()).forEach(steps::add); }
            if (versioned(body)) answer = QRefContext.extract(question).wireExpected(answer);
            return OllamaStub.chatResponse(Json.canonicalString(answer), "stop", 300, 35);
        };
    }
}
