package com.nori.ai.gateway.acceptance;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class F1SourceChecksTest {
    private com.fasterxml.jackson.databind.JsonNode capture(String job,String state,List<Map<String,Object>> attempts){return CanonicalJson.MAPPER.valueToTree(Map.of("jobId",job,"status","RECORDED","observations",List.of(Map.of("capabilityId","tc.logs.search","status",state,"count",attempts.size(),"attempts",attempts))));}
    @Test void completeZeroIsAcceptedButMissingWrongJobInvalidSelectorAndIncompleteAreNot(){F1SourceChecks.verify(capture("job","COMPLETE",List.of()),"job",Set.of(),true);
        assertThatThrownBy(()->F1SourceChecks.verify(CanonicalJson.MAPPER.valueToTree(Map.of("jobId","job","status","MISSING")),"job",Set.of(),true)).isInstanceOf(org.opentest4j.TestAbortedException.class);
        assertThatThrownBy(()->F1SourceChecks.verify(capture("other","COMPLETE",List.of()),"job",Set.of(),true)).hasMessage("F1_SOURCE_JOB_MISMATCH");
        assertThatThrownBy(()->F1SourceChecks.verify(capture("job","OVERFLOW",List.of()),"job",Set.of(),true)).hasMessage("F1_SOURCE_OBSERVATION_INCOMPLETE");
    }
    @Test void exactClippingAndSplitDigestsAllowActualRetriesAndRejectUnexpectedWindows(){Instant end=Instant.parse("2026-11-09T00:00:00Z"),from=end.minusSeconds(8*3600);var expected=F1SourceChecks.expected(Map.of("tc-gateway",from.plusNanos(1)),from,end,Duration.ofHours(6));assertThat(expected).hasSize(2);
        var attempts=new ArrayList<Map<String,Object>>();for(String digest:expected){attempts.add(Map.of("digest",digest,"state","ALLOWLIST","outcome","FAILED"));attempts.add(Map.of("digest",digest,"state","ALLOWLIST","outcome","SUCCESS"));}F1SourceChecks.verify(capture("job","COMPLETE",attempts),"job",expected,false);
        attempts.add(Map.of("digest","a".repeat(64),"state","ALLOWLIST","outcome","SUCCESS"));assertThatThrownBy(()->F1SourceChecks.verify(capture("job","COMPLETE",attempts),"job",expected,false)).hasMessage("F1_SOURCE_WINDOW_DIGEST_MISMATCH");
        assertThatThrownBy(()->F1SourceChecks.verify(capture("job","COMPLETE",List.of(Map.of("digest","a".repeat(64),"state","NAMESPACE_ONLY","outcome","SUCCESS"))),"job",Set.of("a".repeat(64)),false)).hasMessage("F1_SOURCE_SELECTOR_INVALID");
    }
    @Test void evidenceAndCitationOutsideOwnerOrAppAreRejected(){for(var node:List.of(Map.of("evidence",List.of(Map.of("app","wm-ui-backend"))),Map.of("citations",List.of(Map.of("ref","sources://WM/logs"))),Map.of("evidence",List.of(Map.of("ownerSystem","HRM")))))assertThatThrownBy(()->F1SourceChecks.assertReferences(CanonicalJson.MAPPER.valueToTree(node))).isInstanceOf(AssertionError.class);}
}
