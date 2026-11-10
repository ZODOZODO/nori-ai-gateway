package com.nori.ai.gateway.core.registry;

import java.util.*;
public final class ContractVersionPolicy {
    private static final Set<String> SUPPORTED = Set.of("1.0");
    private final Set<String> registeredDigests = new HashSet<>();
    public boolean supports(String version) { return SUPPORTED.contains(version); }
    synchronized void register(CapabilityManifest manifest) {
        if (!supports(manifest.contractVersion())) throw new IllegalArgumentException("지원하지 않는 계약 버전입니다");
        registeredDigests.add(manifest.schemaDigest());
    }
    public synchronized boolean accepts(String version, String digest) {
        return version != null && supports(version) && digest != null && registeredDigests.contains(digest);
    }
}
