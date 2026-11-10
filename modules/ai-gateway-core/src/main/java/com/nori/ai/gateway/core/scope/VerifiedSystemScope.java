package com.nori.ai.gateway.core.scope;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record VerifiedSystemScope(OwnerSystem boundSystem, String authenticatedServiceRef, ActorRef actorRef,
        String sub, Set<String> perm, String pdg, Instant issuedAt, Instant expiresAt, String requestId) {
    public VerifiedSystemScope {
        Objects.requireNonNull(boundSystem, "소유 시스템이 필요합니다");
        Objects.requireNonNull(actorRef, "사용자 참조가 필요합니다");
        perm = Set.copyOf(perm);
        for (String value : new String[]{authenticatedServiceRef, sub, pdg, requestId}) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("인증 범위가 누락되었습니다");
        }
        // 기존 생성자 계약을 유지하되 서명된 sub와 실행 사용자 참조가 갈라지지 않게 합니다.
        if (!actorRef.value().equals(sub)) throw new IllegalArgumentException("사용자 참조와 인증 주체가 일치하지 않습니다");
        if (issuedAt == null || expiresAt == null || !issuedAt.isBefore(expiresAt)) throw new IllegalArgumentException("인증 유효 기간이 잘못되었습니다");
    }
    public void requireOwner(OwnerSystem owner) {
        if (owner != boundSystem) throw new IllegalArgumentException("소유 시스템이 일치하지 않습니다");
    }
    public boolean validAt(Instant now) { return !now.isBefore(issuedAt) && now.isBefore(expiresAt); }
}
