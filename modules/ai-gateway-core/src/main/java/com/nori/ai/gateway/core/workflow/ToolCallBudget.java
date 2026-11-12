package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.registry.ToolKind;
import java.util.HashMap;
import java.util.Map;

/**
 * 한 Job 의 Tool 호출 한도(SPEC §28.8: 전체 12·같은 Tool 3·Knowledge search 2·근거 보완 round 1). 실제 dispatcher 직전에 검사하며 보완 호출도 같은 한도에 포함한다.
 * Job 180s·만료·취소는 기존 ExpiryCheckpoint·JobWorker 가드가 같은 지점에서 맡는다. 요청별 가변 값이므로 빈이 아니다.
 */
public final class ToolCallBudget {
    public static final int TOTAL = 12, SAME_TOOL = 3, KNOWLEDGE_SEARCH = 2, SUPPLEMENT_ROUNDS = 1;
    private final Map<String,Integer> perTool = new HashMap<>();
    private final int totalLimit, sameToolLimit, knowledgeLimit, roundLimit;
    private int total, knowledge, rounds;

    /** SPEC §28.8 기본 한도. */
    public ToolCallBudget() { this(TOTAL, SAME_TOOL, KNOWLEDGE_SEARCH, SUPPLEMENT_ROUNDS); }

    /** 한도 경계 시험용 명시 한도. 운영 경로는 기본 한도만 쓴다. */
    public ToolCallBudget(int totalLimit, int sameToolLimit, int knowledgeLimit, int roundLimit) {
        if (totalLimit < 0 || sameToolLimit < 0 || knowledgeLimit < 0 || roundLimit < 0) throw new IllegalArgumentException("호출 한도가 잘못되었습니다");
        this.totalLimit = totalLimit; this.sameToolLimit = sameToolLimit; this.knowledgeLimit = knowledgeLimit; this.roundLimit = roundLimit;
    }

    /** 호출할 수 있으면 세고 true, 한도를 넘으면 세지 않고 false. */
    public boolean tryAcquire(String capabilityId, ToolKind kind) {
        int same = perTool.getOrDefault(capabilityId, 0);
        boolean search = kind == ToolKind.KNOWLEDGE && capabilityId.endsWith(".knowledge.search");
        if (total >= totalLimit || same >= sameToolLimit || search && knowledge >= knowledgeLimit) return false;
        total++; perTool.put(capabilityId, same + 1); if (search) knowledge++;
        return true;
    }

    /** 근거 보완 round 를 시작할 수 있으면 세고 true. */
    public boolean tryStartSupplementRound() {
        if (rounds >= roundLimit) return false;
        rounds++;
        return true;
    }

    public int total() { return total; }
}
