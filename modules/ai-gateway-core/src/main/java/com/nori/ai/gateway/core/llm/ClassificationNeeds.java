package com.nori.ai.gateway.core.llm;

import com.nori.ai.gateway.core.registry.TaskType;
import com.nori.ai.gateway.core.workflow.AnswerMode;
import com.nori.ai.gateway.core.workflow.EvidenceNeed;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 분류 wire v3 {@code n}·{@code a} 의 결정론 절차(SPEC §28.3 ①~④, Plan §16.3 R1 "AC-32-NEEDS 판정 규칙").
 * ① 거부 사유는 기존 재시도 요지 코드로만 돌려주며, 신규 위반끼리는 타입 → enum → 형식 순서다. ②정규화·③서버 보완·④결속은 {@link #resolve}.
 */
public final class ClassificationNeeds {
    private ClassificationNeeds() {}

    /** ④ 결속 값: S,H,N,I,C,R 순서의 최종 evidenceNeeds 와 ③에서 서버가 더한 코드. */
    public record Resolved(List<EvidenceNeed> evidenceNeeds, Set<EvidenceNeed> serverAdded) {
        public Resolved {
            evidenceNeeds = List.copyOf(evidenceNeeds);
            serverAdded = serverAdded.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(serverAdded));
            if (!evidenceNeeds.containsAll(serverAdded)) throw new IllegalArgumentException("서버 보완 코드가 결과에 없습니다");
        }
    }

    /**
     * ① 신규 필드 검사. 기존 v2 필드 검사(키 집합·t·qRef·eqp/from/to)를 통과한 뒤에만 부른다.
     * @return 첫 위반의 재시도 요지 코드, 통과면 null
     */
    static String violation(String taskCode, JsonNode n, JsonNode a) {
        Objects.requireNonNull(taskCode, "taskCode");
        // 타입(ⓒ): n 은 문자열 배열이어야 한다.
        if (n == null || !n.isArray()) return "TYPE_INVALID";
        for (JsonNode code : n) if (!code.isString()) return "TYPE_INVALID";
        // enum(ⓓ·ⓗ): 여섯 코드와 B·D 밖의 값.
        for (JsonNode code : n) if (code(code.asString()) == null) return "ENUM_INVALID";
        if (a == null || !a.isString() || !(a.asString().equals("B") || a.asString().equals("D"))) return "ENUM_INVALID";
        // 형식(ⓔ·ⓕ·ⓖ): 중복, GQ/UNS 의 비어 있지 않은 n, KQ/LQ/INC 의 빈 n.
        Set<String> seen = new HashSet<>();
        for (JsonNode code : n) if (!seen.add(code.asString())) return "FORMAT_INVALID";
        boolean noEvidence = taskCode.equals("GQ") || taskCode.equals("UNS");
        if (noEvidence && !n.isEmpty()) return "FORMAT_INVALID";
        if (!noEvidence && n.isEmpty()) return "FORMAT_INVALID";
        return null;
    }

    /** ①을 통과한 wire 값의 집합 n₀. */
    static Set<EvidenceNeed> codes(JsonNode n) {
        EnumSet<EvidenceNeed> codes = EnumSet.noneOf(EvidenceNeed.class);
        for (JsonNode code : n) codes.add(Objects.requireNonNull(code(code.asString()), "검증되지 않은 근거 코드"));
        return codes;
    }

    static AnswerMode answerMode(JsonNode a) { return AnswerMode.valueOf(a.asString()); }

    /** ②~④: n₀ 를 정규화하고 상위 작업 최소 근거(단계 A)와 의존 근거(단계 B)를 추가만 한다. 같은 입력은 같은 결과다. */
    public static Resolved resolve(TaskType taskType, Set<EvidenceNeed> wire) {
        Objects.requireNonNull(taskType, "taskType");
        EnumSet<EvidenceNeed> needs = wire.isEmpty() ? EnumSet.noneOf(EvidenceNeed.class) : EnumSet.copyOf(wire);
        EnumSet<EvidenceNeed> added = EnumSet.noneOf(EvidenceNeed.class);
        // 단계 A(n₀·t): KQ=N 또는 I, LQ=S, INC=H. INC 의 S/N/I 선택은 질문 의미라 보완하지 않는다.
        if (taskType == TaskType.KNOWLEDGE_QUERY && !needs.contains(EvidenceNeed.N) && !needs.contains(EvidenceNeed.I)) add(needs, added, EvidenceNeed.N);
        if (taskType == TaskType.LIVE_QUERY && !needs.contains(EvidenceNeed.S)) add(needs, added, EvidenceNeed.S);
        if (taskType == TaskType.INCIDENT_ANALYSIS && !needs.contains(EvidenceNeed.H)) add(needs, added, EvidenceNeed.H);
        // 단계 B(n₁): C 는 기대(N/I)와 실제(S/H) 양쪽, R 은 적용 가능한 N 이 필요하다. 보완은 C·R 을 더하지 않으므로 한 번이 최종이다.
        boolean comparison = needs.contains(EvidenceNeed.C), action = needs.contains(EvidenceNeed.R);
        boolean expected = needs.contains(EvidenceNeed.N) || needs.contains(EvidenceNeed.I);
        boolean actual = needs.contains(EvidenceNeed.S) || needs.contains(EvidenceNeed.H);
        if (comparison && !expected) { add(needs, added, EvidenceNeed.N); add(needs, added, EvidenceNeed.I); }
        if (comparison && !actual) add(needs, added, EvidenceNeed.S);
        if (action && !needs.contains(EvidenceNeed.N)) add(needs, added, EvidenceNeed.N);
        return new Resolved(new ArrayList<>(needs), added);
    }

    private static void add(EnumSet<EvidenceNeed> needs, EnumSet<EvidenceNeed> added, EvidenceNeed need) {
        if (needs.add(need)) added.add(need);
    }

    private static EvidenceNeed code(String value) {
        for (EvidenceNeed need : EvidenceNeed.values()) if (need.name().equals(value)) return need;
        return null;
    }
}
