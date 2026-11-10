package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.scope.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class RegistryViewTest {
    @Test void filtersByOwnerAvailabilityPhaseAndAnyPermissionWithDefaultDeny() {
        var a = manifest(); var b = otherOwner(a);
        var registry = new CapabilityRegistry(List.of(a,b), workflows(), Set.of(ImplementationPhase.PHASE_1), new ContractVersionPolicy());
        assertThat(registry.view(scope()).capabilities()).containsExactly(a);
        assertThat(registry.view(scope(OwnerSystem.WM,Set.of("EQP_READ"))).capabilities()).containsExactly(b);
        assertThat(registry.view(scope(OwnerSystem.TC,Set.of())).capabilities()).isEmpty();
        for (Availability availability : List.of(Availability.PLANNED,Availability.UNSUPPORTED,Availability.DISABLED))
            assertThat(registry(modify(a,"availability",availability),new ContractVersionPolicy()).view(scope()).capabilities()).isEmpty();
        assertThat(registry(modify(a,"implementationPhase",ImplementationPhase.PHASE_2),new ContractVersionPolicy()).view(scope()).capabilities()).isEmpty();
        assertThat(registry(modify(a,"requiredPermission",List.of()),new ContractVersionPolicy()).view(scope()).capabilities()).isEmpty();
        var any = modify(a,"requiredPermission",List.of("UNMAPPED", "EQP_READ"));
        assertThat(registry(any,new ContractVersionPolicy()).view(scope()).capabilities()).containsExactly(any);
    }
    @Test void registryAndScopeAreDefensiveAndScopeExpiryBoundaryIsExclusive() {
        Set<String> perms = new HashSet<>(Set.of("EQP_READ")); var scope=scope(OwnerSystem.TC,perms);perms.clear();
        assertThat(scope.perm()).containsExactly("EQP_READ");
        assertThat(scope.validAt(scope.issuedAt())).isTrue();assertThat(scope.validAt(scope.expiresAt())).isFalse();
        assertThatThrownBy(() -> scope.requireOwner(OwnerSystem.WM)).isInstanceOf(IllegalArgumentException.class);
        var view=registry(manifest(),new ContractVersionPolicy()).view(scope);
        assertThatThrownBy(() -> view.capabilities().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
