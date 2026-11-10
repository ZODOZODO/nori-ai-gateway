package com.nori.ai.gateway.core.dispatch;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.*;
import java.util.*;

public final class ToolResultContractValidator {
    private final ContractVersionPolicy versions;
    public ToolResultContractValidator(ContractVersionPolicy versions) { this.versions = Objects.requireNonNull(versions); }
    public ToolResultEnvelope validate(ToolResultEnvelope result, CapabilityManifest manifest) {
        boolean valid = result != null && manifest.capabilityId().equals(result.capabilityId())
            && manifest.contractVersion().equals(result.contractVersion()) && manifest.schemaDigest().equals(result.schemaDigest())
            && versions.accepts(result.contractVersion(), result.schemaDigest()) && result.ownerSystem() == manifest.ownerSystem()
            && result.status() != null && result.observedAt() != null && result.toolExecutionId() != null && !result.toolExecutionId().isBlank()
            && result.traceId() != null && !result.traceId().isBlank() && result.metadata() != null
            && result.metadata().sourceRef() != null && result.metadata().sourceRef().belongsTo(manifest.ownerSystem())
            && manifest.sourceAllowlistRef().equals(result.metadata().sourceRef().ref())
            && manifest.sourceFreshnessPolicy().equals(result.metadata().sourceFreshnessPolicy())
            && optionalRef(result.dataRef(), manifest.ownerSystem()) && refs(result.evidenceRefs(), manifest.ownerSystem())
            && refs(result.citations(), manifest.ownerSystem()) && refs(result.composerInputs(), manifest.ownerSystem());
        if (valid) {
            valid = result.blocks().stream().allMatch(b -> optionalRef(b.dataRef(), manifest.ownerSystem())
                && (b.content() == null || SchemaContracts.matches(manifest.outputSchema(), b.content())))
                && result.claims().stream().allMatch(c -> c.verdict() != null && refs(c.evidenceRefs(), manifest.ownerSystem()))
                && result.evidence().stream().allMatch(e -> e.ownerSystem() == manifest.ownerSystem() && e.sourceRef() != null
                    && e.sourceRef().belongsTo(manifest.ownerSystem()) && manifest.sourceAllowlistRef().equals(e.sourceRef().ref())
                    && optionalRef(e.deploymentRef(), manifest.ownerSystem()) && e.actorAccessScope() != null && e.observedAt() != null);
        }
        if (valid && result.status() == ResultStatus.FAILED) valid = result.errorCode() != null;
        if (valid && result.status() == ResultStatus.PARTIAL) valid = !result.missingFields().isEmpty() || !result.metadata().missingRanges().isEmpty();
        if (valid && Set.of(ResultStatus.DENIED, ResultStatus.UNSUPPORTED, ResultStatus.FAILED, ResultStatus.SOURCE_UNAVAILABLE, ResultStatus.TIMEOUT).contains(result.status()))
            valid = result.blocks().isEmpty() && result.claims().isEmpty() && result.evidence().isEmpty() && result.citations().isEmpty()
                && result.composerInputs().isEmpty() && result.dataRef() == null && result.evidenceRefs().isEmpty();
        return valid ? result : failure(manifest, ResultStatus.FAILED, ErrorCode.CONTRACT_INVALID, null);
    }
    public ToolResultEnvelope failure(CapabilityManifest manifest, ResultStatus status, ErrorCode error, Clarification clarification) {
        // 거부된 응답의 소유자·참조·리비전은 복사하지 않아 존재 정보도 외부에 노출하지 않습니다.
        return new ToolResultEnvelope(null, manifest.capabilityId(), manifest.contractVersion(), manifest.schemaDigest(),
            manifest.ownerSystem(), status, null, List.of(), null, null, List.of(), List.of(), error, null, null, clarification,
            List.of(), List.of(), List.of(), List.of(), List.of());
    }
    private boolean optionalRef(OwnedReference ref, OwnerSystem owner) { return ref == null || ref.belongsTo(owner); }
    private boolean refs(List<OwnedReference> refs, OwnerSystem owner) { return refs.stream().allMatch(r -> r.belongsTo(owner)); }
}
