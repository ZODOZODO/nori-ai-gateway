package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.core.workflow.AnswerMode;
import com.nori.ai.gateway.core.workflow.EvidenceNeed;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 검증·qRef 복원을 마친 분류 결과(SPEC §14.5 resolvedTask {@code {t, eqp, from, to, q}} + §28.3 분류 wire v3 의 evidenceNeeds·answerMode).
 * wire qRef 와 정규화 전 wire {@code n} 은 담지 않는다. {@code q} 는 요청 원문 그대로이며 후보가 없을 때만 null 이다.
 * {@code evidenceNeeds} 는 §28.3 ②~④ 결과(S,H,N,I,C,R 순서)이고 {@code serverAdded} 는 그중 ③에서 서버가 더한 코드다.
 */
public record Classification(TaskType taskType, String eqp, String from, String to, String q,
        List<EvidenceNeed> evidenceNeeds, Set<EvidenceNeed> serverAdded, AnswerMode answerMode, String wireVersion) {
    /** resolved Classification 이 기록하는 분류 wire 버전(SPEC §28.3 "v2와 구별"). */
    public static final String WIRE_V3 = "classify-wire-v3";

    public Classification {
        Objects.requireNonNull(taskType, "taskType"); Objects.requireNonNull(answerMode, "answerMode");
        evidenceNeeds = List.copyOf(evidenceNeeds);
        serverAdded = serverAdded.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(serverAdded));
        if (!WIRE_V3.equals(wireVersion)) throw new IllegalArgumentException("분류 wire 버전이 다릅니다");
        if (!evidenceNeeds.equals(List.copyOf(evidenceNeeds.isEmpty() ? EnumSet.noneOf(EvidenceNeed.class) : EnumSet.copyOf(evidenceNeeds)))
            || !evidenceNeeds.containsAll(serverAdded)) throw new IllegalArgumentException("근거 요구가 정규화되지 않았습니다");
    }

    /**
     * JobContext 의 분류 맵(P13 소비자 계약: {@code taskType} 이름과 값이 있는 인자만 + 분류 wire v3 결속 값).
     * {@code evidenceNeeds}·{@code serverAddedNeeds} 는 코드 이름 목록, {@code answerMode} 는 B·D 다.
     */
    public Map<String,Object> jobClassification() {
        Map<String,Object> values = new LinkedHashMap<>();
        values.put("taskType", taskType.name());
        if (eqp != null) values.put("eqp", eqp);
        if (from != null) values.put("from", from);
        if (to != null) values.put("to", to);
        if (q != null) values.put("q", q);
        values.put("evidenceNeeds", evidenceNeeds.stream().map(Enum::name).toList());
        values.put("serverAddedNeeds", EvidenceNeed.valuesInOrder(serverAdded));
        values.put("answerMode", answerMode.name());
        values.put("wireVersion", wireVersion);
        return Map.copyOf(values);
    }

    @Override public String toString() { return "Classification[taskType=" + taskType + "]"; }
}
