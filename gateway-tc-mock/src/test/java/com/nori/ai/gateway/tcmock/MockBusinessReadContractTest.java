package com.nori.ai.gateway.tcmock;

import com.nori.ai.gateway.core.authz.*;
import com.nori.ai.gateway.core.port.TcBusinessReadPort.*;
import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.*;
import com.nori.ai.gateway.core.registry.*;
import com.nori.ai.gateway.core.retention.*;
import com.nori.ai.gateway.core.result.*;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MockBusinessReadContractTest {
    private final Instant now = Instant.parse("2026-11-06T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final DelegatedCall call = new DelegatedCall("actor", "d".repeat(22));
    private final MockDelegationRegistry registry = new MockDelegationRegistry(clock);
    private final MockScenarioStore scenarios = new MockScenarioStore();
    private final MockCallCounter calls = new MockCallCounter();
    private final MockTcSourceAuthorizationAdapter authorization = new MockTcSourceAuthorizationAdapter(registry, scenarios, clock, calls);
    private final List<CapabilityManifest> manifests = new ManifestLoader().loadClasspath("manifests/tc/index.json");
    private final MockTcBusinessReadAdapter adapter = new MockTcBusinessReadAdapter(scenarios, authorization, clock, calls, manifests);
    private final TimeWindow window = new TimeWindow(now.minusSeconds(3000), now.plusSeconds(1));
    @BeforeEach void register() { registry.register(call.sub(), call.dlg()); }
    @Test void canonicalManifestDigestsAndMockPermissionsMatchActualResources() throws Exception {
        for (var manifest : manifests) assertThat(adapter.schemaDigest(manifest.toolId())).isEqualTo(manifest.schemaDigest());
        try (var input = getClass().getResourceAsStream("/config/source-permissions/tc-v1.json")) {
            assertThat(input).isNotNull();
            var mapping = SourcePermissionMap.fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            for (var manifest : manifests) {
                var entry = mapping.find(manifest.sourceAllowlistRef()).orElseThrow();
                if (!manifest.requiredPermission().isEmpty()) assertThat(entry.requiredPermCodes()).containsExactlyInAnyOrderElementsOf(manifest.requiredPermission());
            }
            assertThat(mapping.find("sources://TC/event-backend").orElseThrow().mockOnly()).isTrue();
        }
    }
    @Test void eqpFourStatesObservedTimeAndReadModelHierarchyRoundTrip() {
        var result = adapter.getEqpSnapshot(call, "EQP-001");
        assertThat(result.status()).isEqualTo(ResultStatus.SUCCESS); assertThat(result.observedAt()).isEqualTo(now);
        assertThat(result.data()).isEqualTo(scenarios.current().eqpSnapshot());
        var absent = adapter.getEqpSnapshot(call, "absent");
        assertThat(absent.status()).isEqualTo(ResultStatus.EMPTY); assertThat(absent.data()).isNull();
        assertThat(adapter.readModel(call, Optional.empty()).data().getFirst().children()).hasSize(1);
        assertThat(adapter.readModel(call, Optional.of("absent")).status()).isEqualTo(ResultStatus.EMPTY);
    }
    @Test void workLotStatesNeverInventCurrentLot() {
        var active = adapter.getActiveWorkLots(call, "EQP-001"); assertThat(active.data().currentLot()).contains("LOT-001");
        for (String id : List.of("lot-multi", "lot-ended", "lot-unknown")) {
            scenarios.select(id); var result = adapter.getActiveWorkLots(call, "EQP-001");
            assertThat(result.data().currentLot()).isEmpty(); assertThat(result.status()).isEqualTo(ResultStatus.PARTIAL);
            assertThat(result.missingFields()).contains("currentLot");
        }
    }
    @Test void emptyManifestPermissionDoesNotBypassCurrentEventAuthorization() {
        assertThat(manifests.stream().filter(m -> m.toolId().equals("tc.event.getRecent")).findFirst().orElseThrow().requiredPermission()).isEmpty();
        assertThat(adapter.getRecentEvents(call, "EQP-001", window).status()).isEqualTo(ResultStatus.SUCCESS);
        scenarios.select("permission-revoked");
        assertThat(adapter.getRecentEvents(call, "EQP-001", window).status()).isEqualTo(ResultStatus.DENIED);
        assertThat(calls.count("getRecentEvents")).isEqualTo(1);
    }
    @Test void invalidDelegationDeniedUnavailableAndEmptyGateCannotExecuteSource() {
        assertThat(authorization.authorize(call, Set.of())).isInstanceOf(Allowed.class);
        scenarios.select("permission-revoked");
        assertThat(adapter.getCommLogs(call, "EQP-001", "gateway", window).status()).isEqualTo(ResultStatus.DENIED);
        scenarios.select("logout"); assertThat(adapter.getEqpSnapshot(call, "EQP-001").status()).isEqualTo(ResultStatus.DENIED);
        scenarios.select("authz-down"); assertThat(adapter.getEqpSnapshot(call, "EQP-001").status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
        assertThat(calls.count("getEqpSnapshot")).isZero(); assertThat(calls.count("getCommLogs")).isZero();
    }
    @Test void malformedWireDeniesSourceExecutionWithUnavailableStatus() {
        var broken = new MockTcBusinessReadAdapter(scenarios, (c, requested) -> authorization.project("{}", requested), clock, calls, manifests);
        assertThat(broken.getEqpSnapshot(call, "EQP-001").status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
        var thrown = new MockTcBusinessReadAdapter(scenarios, (c, requested) -> { throw new IllegalStateException("통신 실패"); }, clock, calls, manifests);
        assertThat(thrown.getEqpSnapshot(call, "EQP-001").status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
        var badDigest = new MockTcBusinessReadAdapter(scenarios, (c, requested) -> new Allowed(Set.of(), "bad"), clock, calls, manifests);
        assertThat(badDigest.getEqpSnapshot(call, "EQP-001").status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
        assertThat(calls.count("getEqpSnapshot")).isZero();
    }
    @Test void unverifiedEmptyAndHitAlwaysWarnAndDlqLossIsExplicit() {
        assertThat(adapter.getCommLogs(call, "EQP-001", "gateway", window).warnings()).contains(Warning.RETENTION_UNVERIFIED);
        assertThat(adapter.listDlq(call, "gateway", window).warnings()).contains(Warning.RETENTION_UNVERIFIED);
        scenarios.select("empty");
        var empty = adapter.getCommLogs(call, "EQP-001", "gateway", window);
        assertThat(empty.status()).isEqualTo(ResultStatus.EMPTY); assertThat(empty.warnings()).contains(Warning.RETENTION_UNVERIFIED);
        scenarios.select("dlq-partial");
        assertThat(adapter.listDlq(call, "gateway", window).missingFields()).containsExactly("decodeFailures=1");
        scenarios.select("dlq-down");
        assertThat(adapter.listDlq(call, "gateway", window).status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
        assertThat(adapter.listDlq(call, "business", window).status()).isEqualTo(ResultStatus.SUCCESS);
    }
    @Test void trimUnknownRangeIsSeparateFromTimeMissingRangeAndOutsideDoesNotCallSource() {
        scenarios.select("comm-trim");
        var trimmed = adapter.getCommLogs(call, "EQP-001", "gateway", window);
        assertThat(trimmed.status()).isEqualTo(ResultStatus.SUCCESS);
        assertThat(trimmed.retentionMeta().unverifiedRanges()).containsExactly(new TimeWindow(window.startAt(), now.minusSeconds(600)));
        assertThat(trimmed.retentionMeta().missingRanges()).isEmpty();
        long before = calls.count("getCommLogs");
        var outside = adapter.getCommLogs(call, "EQP-001", "gateway", new TimeWindow(now.minusSeconds(7200), now.minusSeconds(3600)));
        assertThat(outside.status()).isEqualTo(ResultStatus.OUT_OF_RETENTION); assertThat(calls.count("getCommLogs")).isEqualTo(before);
        scenarios.select("comm-down");
        assertThat(adapter.getCommLogs(call, "EQP-001", "business", window).status()).isEqualTo(ResultStatus.SOURCE_UNAVAILABLE);
    }
    @Test void incompatibleContractAndUnknownResourceTypeFailClosed() {
        scenarios.select("contract-v2");
        assertThat(adapter.getEqpSnapshot(call, "EQP-001").status()).isEqualTo(ResultStatus.FAILED);
        assertThat(adapter.resolveResource(call, "UNKNOWN", "ref").status()).isEqualTo(ResultStatus.DENIED);
    }
    @Test void advancingClockKeepsItemExactlyOnTheFixedQueryBoundary() {
        scenarios.select("comm-trim");
        Instant itemAt = scenarios.current().commLogs().get("gateway").items().getFirst().occurredAt();
        Clock advancing = new Clock() {
            private Instant value = itemAt.plusSeconds(3600);
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() {
                Instant current = value; value = value.plusNanos(1); return current;
            }
        };
        var boundaryAdapter = new MockTcBusinessReadAdapter(scenarios, (c, required) -> new Allowed(required, "a".repeat(64)),
            advancing, calls, List.of());
        var result = boundaryAdapter.getCommLogs(call, "EQP-001", "gateway", new TimeWindow(itemAt, itemAt.plusSeconds(60)));
        assertThat(result.status()).isEqualTo(ResultStatus.SUCCESS);
        assertThat(result.data().items()).containsExactly(scenarios.current().commLogs().get("gateway").items().getFirst());
        assertThat(result.retentionMeta().missingRanges()).isEmpty();
        assertThat(calls.count("getCommLogs")).isEqualTo(1);
    }
    @Test void resourceResolverRequiresDelegationAndReturnsOnlyMatchingTypedReference() {
        assertThat(adapter.resolveResource(call, "EQP", "EQP-001").data()).containsExactly(new Resource("EQP", "EQP-001"));
        assertThat(adapter.resolveResource(call, "EQP", "other").status()).isEqualTo(ResultStatus.EMPTY);
        assertThat(adapter.resolveResource(new DelegatedCall("other", call.dlg()), "EQP", "EQP-001").status()).isEqualTo(ResultStatus.DENIED);
    }
}
