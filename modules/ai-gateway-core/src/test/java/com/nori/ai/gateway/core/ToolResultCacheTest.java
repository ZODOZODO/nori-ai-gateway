package com.nori.ai.gateway.core;

import com.nori.ai.gateway.core.authz.*;
import com.nori.ai.gateway.core.cache.ToolResultCache;
import com.nori.ai.gateway.core.expiry.*;
import com.nori.ai.gateway.core.job.*;
import com.nori.ai.gateway.core.plan.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.result.*;
import com.nori.ai.gateway.core.scope.VerifiedSystemScope;
import com.nori.ai.gateway.core.seam.PlanInjectionSeam;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.nori.ai.gateway.core.DomainFixtures.*;

class ToolResultCacheTest {
    @Test void knownGitRevisionCanHitAndAChangedRevisionMissesWithoutWeakeningCurrentAuthorization() {
        var fixture=new WorkflowAuthorizationPreparationTest.Fixture();var git=modify(fixture.manifest,"toolKind","GIT");var registry=registry(git,new ContractVersionPolicy());var validator=validator(registry,new PlanInjectionSeam.NoOp());
        var prepared=(PlanValidator.Prepared)validator.prepare(plan(step()),fixture.scope,fixture.resolver);var store=new Store();var cache=new ToolResultCache(store,validator,new ResultAccessEvaluator(),(permissions,documents)->Map.of(),new ExpiryCheckpoint(new ConversationExpiryGuard(),CLOCK),CLOCK);var job=job(fixture,"conversation");
        var inputs=new ToolResultCache.CacheInputs(Optional.empty(),Optional.of("a".repeat(40)),git.sourceFreshnessPolicy());var value=new ToolResultCache.CachedValue(ID,SchemaContracts.JSON.valueToTree(result(git)),new ResultSourceDeps(List.of(new ResultSourceDeps.Source(git.sourceAllowlistRef(),Set.of("EQP_READ"))),List.of()),NOW.plusSeconds(60));
        var put=prepared.newAttempt(step(),fixture.scope,StepAttempt.Purpose.CACHE_PUT);assertThat(cache.put(prepared,put,fixture.grant(put),job,inputs,value)).isTrue();var lookup=prepared.newAttempt(step(),fixture.scope,StepAttempt.Purpose.CACHE_LOOKUP);assertThat(cache.find(prepared,lookup,fixture.grant(lookup),job,inputs)).isPresent();
        var changed=new ToolResultCache.CacheInputs(Optional.empty(),Optional.of("c".repeat(40)),git.sourceFreshnessPolicy());lookup=prepared.newAttempt(step(),fixture.scope,StepAttempt.Purpose.CACHE_LOOKUP);assertThat(cache.find(prepared,lookup,fixture.grant(lookup),job,changed)).isEmpty();assertThat(fixture.authCalls).hasValue(3);assertThat(store.calls).hasSize(3);
    }
    static final class Store implements ToolResultCache.Store {
        final Map<String,ToolResultCache.CachedValue> values=new HashMap<>();final List<List<String>> calls=new ArrayList<>();
        public Optional<ToolResultCache.CachedValue> find(VerifiedSystemScope scope,String conversation,String key,String pdg){calls.add(List.of("find",conversation,key,pdg));return Optional.ofNullable(values.get(key));}
        public boolean put(VerifiedSystemScope scope,String conversation,String key,String pdg,ToolResultCache.CachedValue value){calls.add(List.of("put",conversation,key,pdg));values.put(key,value);return true;}
    }
    private JobContext job(WorkflowAuthorizationPreparationTest.Fixture f,String conversation){return new JobContext("request",conversation,f.scope,"d".repeat(22),NOW.plusSeconds(1000),new CancellationToken(),InferenceMode.DISABLED,Map.of("taskType","LIVE_QUERY"),new JobRequestInput("message",1,"질문",Map.of()));}
    private ToolResultCache cache(WorkflowAuthorizationPreparationTest.Fixture f,Store store){return new ToolResultCache(store,f.validator,new ResultAccessEvaluator(),(perm,documents)->Map.of(),new ExpiryCheckpoint(new ConversationExpiryGuard(),CLOCK),CLOCK);}
    private ToolResultCache.CacheInputs inputs(){return new ToolResultCache.CacheInputs(Optional.empty(),Optional.empty(),"LIVE");}
    @Test void currentPdgDrivesSameConversationFindPutAndChangedCurrentPdgMisses() {
        var f=new WorkflowAuthorizationPreparationTest.Fixture();var prepared=f.prepared();var store=new Store();var cache=cache(f,store);var job=job(f,"conversation");
        var value=new ToolResultCache.CachedValue(ID,SchemaContracts.JSON.valueToTree(result(f.manifest)),new ResultSourceDeps(List.of(new ResultSourceDeps.Source(f.manifest.sourceAllowlistRef(),Set.of("EQP_READ"))),List.of()),NOW.plusSeconds(600));
        var put=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.CACHE_PUT);assertThat(cache.put(prepared,put,f.grant(put),job,inputs(),value)).isTrue();
        var find=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.CACHE_LOOKUP);assertThat(cache.find(prepared,find,f.grant(find),job,inputs())).isPresent();
        assertThat(store.calls.get(0).get(2)).isEqualTo(store.calls.get(1).get(2));assertThat(store.calls.get(0)).contains("conversation","b".repeat(64));assertThat(store.values.values().iterator().next().expiresAt()).isEqualTo(NOW.plusSeconds(60));
        f.outcome.set(new com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.Allowed(Set.of("EQP_READ"),"c".repeat(64)));
        find=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.CACHE_LOOKUP);assertThat(cache.find(prepared,find,f.grant(find),job,inputs())).isEmpty();assertThat(store.calls.get(2).get(2)).isNotEqualTo(store.calls.get(0).get(2));
    }
    @Test void conversationMismatchAndPurposeSwapHaveNoStoreAccessAndReuseFails() {
        var f=new WorkflowAuthorizationPreparationTest.Fixture();var prepared=f.prepared();var store=new Store();var cache=cache(f,store);var attempt=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.CACHE_LOOKUP);var grant=f.grant(attempt);
        assertThatThrownBy(()->cache.find(prepared,attempt,grant,job(f,"other"),inputs())).isInstanceOf(IllegalArgumentException.class);assertThat(store.calls).isEmpty();
        var put=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.CACHE_PUT);var putGrant=f.grant(put);
        assertThatThrownBy(()->cache.find(prepared,put,putGrant,job(f,"conversation"),inputs())).isInstanceOf(IllegalArgumentException.class);assertThat(store.calls).isEmpty();
        cache.find(prepared,attempt,grant,job(f,"conversation"),inputs());assertThatThrownBy(()->cache.find(prepared,attempt,grant,job(f,"conversation"),inputs())).isInstanceOf(IllegalArgumentException.class);assertThat(store.calls).hasSize(1);
    }
    @Test void separateConversationsAndContractDigestsProduceDifferentKeys() {
        var f=new WorkflowAuthorizationPreparationTest.Fixture();var store=new Store();var cache=cache(f,store);var first=f.prepared();var attempt=first.newAttempt(step(),f.scope,StepAttempt.Purpose.CACHE_LOOKUP);cache.find(first,attempt,f.grant(attempt),job(f,"conversation"),inputs());
        var otherPlan=new PlanEnvelope("request","other","질문",List.of(),TaskType.LIVE_QUERY,List.of(step()));var other=(PlanValidator.Prepared)f.validator.prepare(otherPlan,f.scope,f.resolver);attempt=other.newAttempt(step(),f.scope,StepAttempt.Purpose.CACHE_LOOKUP);cache.find(other,attempt,f.grant(attempt),job(f,"other"),inputs());
        var tree=(tools.jackson.databind.node.ObjectNode)SchemaContracts.JSON.valueToTree(f.manifest);((tools.jackson.databind.node.ObjectNode)tree.get("outputSchema")).put("description","다른 계약");tree.put("schemaDigest",SchemaContracts.digest(tree.get("inputSchema"),tree.get("outputSchema")));var changed=new ManifestLoader().decode(tree);
        var registry=registry(changed,new ContractVersionPolicy());var validator=validator(registry,new PlanInjectionSeam.NoOp());var different=(PlanValidator.Prepared)validator.prepare(plan(step()),f.scope,f.resolver);attempt=different.newAttempt(step(),f.scope,StepAttempt.Purpose.CACHE_LOOKUP);cache.find(different,attempt,f.grant(attempt),job(f,"conversation"),inputs());
        assertThat(store.calls.stream().map(call->call.get(2)).distinct()).hasSize(3);
    }
    @Test void cancellationAndExpiryStopStoreBeforeReceiptConsumption() {
        var f=new WorkflowAuthorizationPreparationTest.Fixture();var prepared=f.prepared();var store=new Store();var cache=cache(f,store);var job=job(f,"conversation");job.cancellation().signal(CancellationToken.Reason.CANCELLED);
        var attempt=prepared.newAttempt(step(),f.scope,StepAttempt.Purpose.CACHE_LOOKUP);var grant=f.grant(attempt);assertThatThrownBy(()->cache.find(prepared,attempt,grant,job,inputs())).isInstanceOf(IllegalStateException.class);assertThat(store.calls).isEmpty();assertThat(grant.matches(attempt)).isTrue();
        var expired=new JobContext(job.jobId(),job.conversationId(),job.scope(),job.delegation(),NOW,new CancellationToken(),job.inferenceMode(),job.classification(),job.requestInput());assertThatThrownBy(()->cache.find(prepared,attempt,grant,expired,inputs())).isInstanceOf(ConversationExpiryGuard.ConversationExpired.class);assertThat(store.calls).isEmpty();
    }
}
