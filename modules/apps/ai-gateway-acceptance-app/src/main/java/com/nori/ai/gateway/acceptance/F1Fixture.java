package com.nori.ai.gateway.acceptance;

import com.nori.ai.gateway.tcmock.PdgCanonical;
import java.util.*;

/** rubric 재조회에서도 원래 actor·권한을 재현하며 요청 외부에서 claims를 주입하지 않습니다. */
public final class F1Fixture {
    public static final List<String> PERMISSIONS = List.of("EQP_READ", "COMM_LOG_READ", "DLQ_READ", "MODEL_READ", "AI_WORKLOT_READ", "AI_EVENT_READ", "AI_LOG_READ", "AI_TRACE_READ", "AI_CODE_READ", "AI_DOC_READ");
    public static final Set<String> RUBRIC_IDS = Set.of("f1-retention-empty", "f1-retention-hit");
    private F1Fixture() {}
    public static Map<String,Object> claims() {
        return Map.of("iss", "tc-ui-backend", "aud", "ai-gateway", "sys", "TC", "sub", "f1-acceptance", "scope", "READ",
            "perm", PERMISSIONS, "pdg", PdgCanonical.compute("mock-v1", PERMISSIONS));
    }
    public static Optional<Map<String,Object>> claimsFor(RubricViewer.Target target) {
        String expected = switch (target.leafId()) { case "AC-46-EMPTY" -> "f1-retention-empty"; case "AC-46-HIT" -> "f1-retention-hit"; default -> ""; };
        return expected.equals(target.fixtureId()) ? Optional.of(claims()) : Optional.empty();
    }
}
