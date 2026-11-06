package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.evidence.EvidenceAssembler;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.ActorRef;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class EvidenceVerdictTest {
    @Test void onlyCompleteDirectRuleEvidenceWithVerifiedDeploymentCanBeConfirmed() {
        var assembler=new EvidenceAssembler();assertThat(assembler.verdict(true,true,false,true)).isEqualTo(Verdict.CONFIRMED);assertThat(assembler.verdict(true,true,false,false)).isEqualTo(Verdict.PROBABLE);assertThat(assembler.verdict(false,true,false,true)).isEqualTo(Verdict.PROBABLE);assertThat(assembler.verdict(true,false,false,true)).isEqualTo(Verdict.INSUFFICIENT_EVIDENCE);assertThat(assembler.verdict(true,false,true,true)).isEqualTo(Verdict.CONFLICTING_EVIDENCE);
        var evidence=result(manifest()).evidence().getFirst();assertThat(assembler.validate(List.of(evidence),scope())).containsExactly(evidence);
        var other=new Evidence(evidence.evidenceId(),evidence.ownerSystem(),evidence.sourceType(),evidence.sourceRef(),evidence.eventTime(),evidence.observedAt(),evidence.traceId(),null,evidence.verificationStatus(),new ActorRef("other"));assertThatThrownBy(()->assembler.validate(List.of(other),scope())).isInstanceOf(IllegalArgumentException.class);
    }
}
