package com.nori.ai.gateway.persistence.audit.auth;

import com.nori.ai.gateway.core.scope.ActorRef;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AuthRejectionAppend(UUID requestId, Optional<OwnerSystem> ownerSystem,
        Optional<ActorRef> actorRef, AuthRejectionReason reason) {
    public AuthRejectionAppend {
        Objects.requireNonNull(requestId, "요청 식별자가 필요합니다");
        Objects.requireNonNull(ownerSystem, "소유 시스템 선택값이 필요합니다");
        Objects.requireNonNull(actorRef, "사용자 선택값이 필요합니다");
        Objects.requireNonNull(reason, "거절 사유가 필요합니다");
        boolean tokenFailure = reason == AuthRejectionReason.SERVICE_TOKEN_MISSING || reason == AuthRejectionReason.SERVICE_TOKEN_INVALID;
        if (tokenFailure ? ownerSystem.isPresent() || actorRef.isPresent()
                : ownerSystem.isEmpty() || actorRef.isPresent() && reason != AuthRejectionReason.SYSTEM_SCOPE_MISMATCH)
            throw new IllegalArgumentException("거절 단계의 신원이 잘못되었습니다");
        if (actorRef.isPresent() && !actorRef.get().value().matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("사용자 참조 형식이 잘못되었습니다");
    }
    public int httpStatus() { return reason.httpStatus(); }
}
