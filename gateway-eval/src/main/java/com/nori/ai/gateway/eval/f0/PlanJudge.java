package com.nori.ai.gateway.eval.f0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nori.ai.gateway.eval.io.Json;
import java.util.*;

/**
 * 판정 결과를 원문 없이 다시 판정 가능한 정제 필드로 바꾼다(t·각 인자 값의 SHA·알려진 capability ID).
 * Path A 는 SPEC §5.3 ⓐ 결정론 backstop 을 적용한다: 계획에 IMPLEMENTED 가 아닌·미등록 capability 가 있으면 실효 taskType = UNS.
 */
public final class PlanJudge {
    private PlanJudge() {}
    public static ObjectNode sanitize(ClassificationJudge.Verdict verdict, RegistryFixture fixture, MessageAssembler.Kind kind) {
        ObjectNode predicted = Json.MAPPER.createObjectNode();
        predicted.put("t", verdict.t());
        ObjectNode fields = predicted.putObject("argSha256");
        for (String field : GoldenSet.ARG_FIELDS) fields.put(field, ResourceFixture.valueSha(verdict.args().get(field)));
        String eqp = verdict.args().get("eqp");
        predicted.put("eqpResource", eqp == null ? "NULL" : fixture.resources().eqpExists(eqp) ? "EXISTS" : "ABSENT");
        String effective = verdict.t();
        if (kind == MessageAssembler.Kind.PLAN) {
            ArrayNode steps = predicted.putArray("steps"); int unknown = 0, inactive = 0;
            for (String step : verdict.steps()) {
                if (!fixture.known(step)) unknown++;
                else { steps.add(step); if (!fixture.implemented(step)) inactive++; }
            }
            predicted.put("unknownStepCount", unknown); predicted.put("inactiveStepCount", inactive);
            if (unknown + inactive > 0) effective = "UNS";
        }
        predicted.put("effectiveT", effective);
        return predicted;
    }
    /** 기대 workflow 선택 판정: Path B 는 실효 t 의 고정 workflow, Path A 는 같은 workflow 의 t 와 계획 단계의 로그 Tool 포함 여부(AC-38 "TC 로그/코드 분석"). */
    public static boolean workflowMatches(JsonNode predicted, String expectedWorkflow, RegistryFixture fixture, String path) {
        if (predicted == null || predicted.isNull() || predicted.isMissingNode()) return false;
        String effective = predicted.path("effectiveT").asText();
        if (!Objects.equals(fixture.workflowFor(effective), expectedWorkflow)) return false;
        if (!path.equals("A") || expectedWorkflow == null) return true;
        Set<String> required = new HashSet<>(fixture.stepsFor(effective)); Set<String> actual = new HashSet<>(); predicted.path("steps").forEach(s -> actual.add(s.asText()));
        return !expectedWorkflow.equals("INCIDENT_ANALYSIS") || (actual.contains("tc.logs.search") && required.contains("tc.logs.search"));
    }
}
