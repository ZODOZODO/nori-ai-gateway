package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.registry.TaskType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 검증·qRef 복원을 마친 분류 결과(SPEC §14.5 resolvedTask {@code {t, eqp, from, to, q}}). wire qRef 는 담지 않는다.
 * {@code q} 는 요청 원문 그대로이며 후보가 없을 때만 null 이다.
 */
public record Classification(TaskType taskType, String eqp, String from, String to, String q) {
    public Classification { Objects.requireNonNull(taskType, "taskType"); }

    /** JobContext 의 분류 맵(P13 소비자 계약: {@code taskType} 이름과 값이 있는 인자만). */
    public Map<String,Object> jobClassification() {
        Map<String,Object> values = new LinkedHashMap<>();
        values.put("taskType", taskType.name());
        if (eqp != null) values.put("eqp", eqp);
        if (from != null) values.put("from", from);
        if (to != null) values.put("to", to);
        if (q != null) values.put("q", q);
        return Map.copyOf(values);
    }

    @Override public String toString() { return "Classification[taskType=" + taskType + "]"; }
}
