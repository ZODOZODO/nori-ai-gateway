package com.nori.ai.gateway.app.hooks;

import com.nori.ai.gateway.core.authz.SourcePermissionMap;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.scope.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;

class SeamRegistryTest {
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"),ZoneOffset.UTC);
    private final TestHookRegistry hooks=new TestHookRegistry(clock,new PausePointRegistry());
    private final ManifestLoader loader=new ManifestLoader();
    private final SourcePermissionMap permissions=new SourcePermissionMap(Map.of("sources://TC/logs",new SourcePermissionMap.Entry(Set.of("AI_LOG_READ"),true)));
    private final SeamRegistry seams=new SeamRegistry(hooks,loader,permissions);
    private VerifiedSystemScope scope(String job){return new VerifiedSystemScope(OwnerSystem.TC,"job-worker",new ActorRef("actor"),"actor",Set.of(),"a".repeat(64),clock.instant(),clock.instant().plusSeconds(60),job);}
    private void bind(String key,String job,SeamRegistry.Directive directive,int completion){
        hooks.arm("actor",key,new TestHookRegistry.Scenario(TaskType.GENERAL_QUERY,Set.of(),null,false,Optional.of(directive)));
        TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);
        try{hooks.bindOnAdmission("actor",key,job);for(var sync:TransactionSynchronizationManager.getSynchronizations())sync.afterCompletion(completion);}
        finally{TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false);}
    }
    @Test void immutableJobDirectiveRepeatsWithoutLeakingToSameActorsNextJobOrLegacyCall() {
        var manifest=loader.loadClasspath("manifests/tc/index.json").stream().filter(m->m.capabilityId().equals("tc.eqp.getOperationalSnapshot")).findFirst().orElseThrow();
        bind("one","job-one",new SeamRegistry.Directive(SeamRegistry.Kind.PLANNED,manifest.capabilityId(),null,null),TransactionSynchronization.STATUS_COMMITTED);
        assertThat(seams.inspect(manifest,scope("job-one")).availability()).isEqualTo(Availability.PLANNED);
        assertThat(seams.inspect(manifest,scope("job-one")).availability()).isEqualTo(Availability.PLANNED);
        assertThat(seams.inspect(manifest,scope("job-two"))).isSameAs(manifest);assertThat(seams.inspect(manifest)).isSameAs(manifest);assertThat(manifest.availability()).isEqualTo(Availability.IMPLEMENTED);
    }
    @Test void scopedMapDropUsesExistingBindingAndRollbackAndResetRemoveOnlyHookState() {
        var directive=new SeamRegistry.Directive(SeamRegistry.Kind.PERMISSION_MAP_DROP,null,"sources://TC/logs",null);
        bind("rollback","job-rollback",directive,TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(seams.permissionMappingExcluded("sources://TC/logs",scope("job-rollback"))).isFalse();
        assertThat(hooks.armed("actor","rollback")).isPresent();
        bind("committed","job",directive,TransactionSynchronization.STATUS_COMMITTED);
        assertThat(seams.permissionMappingExcluded("sources://TC/logs",scope("job"))).isTrue();
        assertThat(seams.permissionMappingExcluded("sources://TC/logs",scope("next"))).isFalse();assertThat(permissions.find("sources://TC/logs")).isPresent();
        assertThat(hooks.resetIfIdle(()->false)).isFalse();assertThat(seams.permissionMappingExcluded("sources://TC/logs",scope("job"))).isTrue();
        assertThat(hooks.resetIfIdle(()->true)).isTrue();assertThat(seams.permissionMappingExcluded("sources://TC/logs",scope("job"))).isFalse();
    }
    @Test void invalidDirectiveCannotInventSourcesCapabilitiesOrOwners() {
        for(var directive:List.of(new SeamRegistry.Directive(SeamRegistry.Kind.PERMISSION_MAP_DROP,null,"unregistered",null),
            new SeamRegistry.Directive(SeamRegistry.Kind.PLANNED,null,null,null),new SeamRegistry.Directive(SeamRegistry.Kind.SOURCE_ALLOWLIST_REF_OVERRIDE,"tc.eqp.getOperationalSnapshot",null,OwnerSystem.TC)))
            assertThatThrownBy(()->seams.validate(directive)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void everyClosedDirectiveProjectsOnlyTheMatchingTargetWithoutMutatingOriginal() {
        var manifest=loader.loadClasspath("manifests/tc/index.json").stream().filter(m->m.capabilityId().equals("tc.eqp.getOperationalSnapshot")).findFirst().orElseThrow();
        var target=new com.nori.ai.gateway.core.dispatch.DispatchTarget(manifest,List.of(new com.nori.ai.gateway.core.plan.ResourceResolver.ResourceState(OwnerSystem.TC,"EQP",true)),
            new com.nori.ai.gateway.core.plan.ResourceResolver.SourceBinding(OwnerSystem.TC,manifest.sourceAllowlistRef(),true));
        for(var kind:List.of(SeamRegistry.Kind.DISABLED,SeamRegistry.Kind.PHASE_INACTIVE,SeamRegistry.Kind.SIDE_EFFECTS,
                SeamRegistry.Kind.OWNER_OVERRIDE,SeamRegistry.Kind.RESOURCE_OWNER_OVERRIDE,SeamRegistry.Kind.SOURCE_ALLOWLIST_REF_OVERRIDE)){
            boolean owner=kind==SeamRegistry.Kind.OWNER_OVERRIDE||kind==SeamRegistry.Kind.RESOURCE_OWNER_OVERRIDE||kind==SeamRegistry.Kind.SOURCE_ALLOWLIST_REF_OVERRIDE;
            String job=kind.name();var directive=new SeamRegistry.Directive(kind,manifest.capabilityId(),null,owner?OwnerSystem.WM:null);seams.validate(directive);
            bind(job,job,directive,TransactionSynchronization.STATUS_COMMITTED);
            var projected=seams.inspect(manifest,scope(job));var dispatched=seams.inspect(target,scope(job));
            switch(kind){
                case DISABLED -> assertThat(projected.availability()).isEqualTo(Availability.DISABLED);
                case PHASE_INACTIVE -> assertThat(projected.implementationPhase()).isEqualTo(ImplementationPhase.PHASE_2);
                case SIDE_EFFECTS -> assertThat(projected.requiresApproval()).isTrue();
                case OWNER_OVERRIDE -> {assertThat(projected.ownerSystem()).isEqualTo(OwnerSystem.WM);assertThat(dispatched.tool().ownerSystem()).isEqualTo(OwnerSystem.WM);}
                case RESOURCE_OWNER_OVERRIDE -> assertThat(dispatched.resources()).singleElement().satisfies(resource->assertThat(resource.ownerSystem()).isEqualTo(OwnerSystem.WM));
                case SOURCE_ALLOWLIST_REF_OVERRIDE -> {assertThat(projected).isSameAs(manifest);assertThat(dispatched.source().ownerSystem()).isEqualTo(OwnerSystem.WM);assertThat(dispatched.source().sourceAllowlistRef()).startsWith("sources://WM/");}
                default -> throw new AssertionError("시험 대상이 잘못되었습니다");
            }
            assertThat(seams.inspect(manifest,scope("unbound"))).isSameAs(manifest);assertThat(seams.inspect(target,scope("unbound"))).isSameAs(target);
        }
        assertThat(manifest.ownerSystem()).isEqualTo(OwnerSystem.TC);assertThat(manifest.availability()).isEqualTo(Availability.IMPLEMENTED);
        assertThat(manifest.requiresApproval()).isFalse();assertThat(target.resources().getFirst().ownerSystem()).isEqualTo(OwnerSystem.TC);assertThat(target.source().ownerSystem()).isEqualTo(OwnerSystem.TC);
    }
    @Test void dispatchOnlyOwnerOverrideKeepsPlanOriginalAndScopesToOneJob() {
        var manifest=loader.loadClasspath("manifests/tc/index.json").stream().filter(m->m.capabilityId().equals("tc.commLogs.search")).findFirst().orElseThrow();
        bind("dispatch","job",new SeamRegistry.Directive(SeamRegistry.Kind.DISPATCH_OWNER_OVERRIDE,manifest.capabilityId(),null,OwnerSystem.WM),TransactionSynchronization.STATUS_COMMITTED);
        assertThat(seams.inspect(manifest,scope("job"))).isSameAs(manifest);
        var target=new com.nori.ai.gateway.core.dispatch.DispatchTarget(manifest,List.of(),new com.nori.ai.gateway.core.plan.ResourceResolver.SourceBinding(OwnerSystem.TC,manifest.sourceAllowlistRef(),true));
        assertThat(seams.inspect(target,scope("job")).tool().ownerSystem()).isEqualTo(OwnerSystem.WM);
        assertThat(seams.inspect(target,scope("next"))).isSameAs(target);
        assertThatThrownBy(()->seams.validate(new SeamRegistry.Directive(SeamRegistry.Kind.DISPATCH_OWNER_OVERRIDE,manifest.capabilityId(),null,OwnerSystem.TC))).isInstanceOf(IllegalArgumentException.class);
    }
}
