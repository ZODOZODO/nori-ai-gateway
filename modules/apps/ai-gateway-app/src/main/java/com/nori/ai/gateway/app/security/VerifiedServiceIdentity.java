package com.nori.ai.gateway.app.security;

import com.nori.ai.gateway.core.scope.OwnerSystem;
import java.util.Objects;

record VerifiedServiceIdentity(OwnerSystem ownerSystem, String authenticatedServiceRef) {
    VerifiedServiceIdentity { Objects.requireNonNull(ownerSystem); Objects.requireNonNull(authenticatedServiceRef); }
}
