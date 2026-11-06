package com.nori.ai.gateway.persistence.retention;

import com.nori.ai.gateway.core.port.SourceRetentionReader;
import java.util.Objects;
import java.util.Optional;

/** 저장된 실제 탐침 관측만 P11 보존 판정에 공급한다. 관측이 없으면 비어 있다. */
public final class SourceRetentionReaderAdapter implements SourceRetentionReader {
    private final SourceRetentionProbeRepository probes;
    public SourceRetentionReaderAdapter(SourceRetentionProbeRepository probes) { this.probes = Objects.requireNonNull(probes); }
    @Override public Optional<VerifiedOldest> find(String sourceId) {
        return probes.find(sourceId).filter(probe -> probe.verifiedOldestAt() != null)
            .map(probe -> new VerifiedOldest(probe.verifiedOldestAt(), probe.probedAt()));
    }
}
