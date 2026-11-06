package com.nori.ai.gateway.tcmock;

import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.DelegatedCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class MockDelegationRegistryTest {
    @TempDir Path directory;
    private final Instant now = Instant.parse("2026-11-06T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final DelegatedCall call = new DelegatedCall("actor", "d".repeat(22));
    @Test void registrationHashOnlyDuplicateAndAbsoluteTtlSurviveReload() throws Exception {
        var original = new MockDelegationRegistry(clock, directory);
        assertThat(original.register(call.sub(), call.dlg())).isEqualTo(MockDelegationRegistry.Registration.REGISTERED);
        assertThat(original.register(call.sub(), call.dlg())).isEqualTo(MockDelegationRegistry.Registration.DUPLICATE);
        var loaded = new MockDelegationRegistry(Clock.offset(clock, Duration.ofSeconds(299)), directory);
        assertThat(loaded.validate(call)).isEqualTo(MockDelegationRegistry.Validity.VALID);
        assertThat(loaded.snapshot()).isEqualTo(original.snapshot());
        assertThat(new MockDelegationRegistry(Clock.offset(clock, Duration.ofSeconds(300)), directory).validate(call)).isEqualTo(MockDelegationRegistry.Validity.INVALID);
        assertThat(Files.readString(directory.resolve("delegations.json"))).doesNotContain(call.dlg()).contains(MockDelegationRegistry.hash(call.dlg()));
        assertThat(Files.list(directory).map(p -> p.getFileName().toString()).toList()).containsExactly("delegations.json");
    }
    @Test void revokeSurvivesReloadAndNeverExtendsExpiry() {
        var original = new MockDelegationRegistry(clock, directory); original.register(call.sub(), call.dlg());
        var before = original.snapshot().get(MockDelegationRegistry.hash(call.dlg()));
        original.revoke(MockDelegationRegistry.hash(call.dlg()));
        var loaded = new MockDelegationRegistry(clock, directory);
        assertThat(loaded.validate(call)).isEqualTo(MockDelegationRegistry.Validity.INVALID);
        assertThat(loaded.snapshot().get(MockDelegationRegistry.hash(call.dlg())).expiresAt()).isEqualTo(before.expiresAt());
        assertThat(loaded.register(call.sub(), call.dlg())).isEqualTo(MockDelegationRegistry.Registration.DUPLICATE);
    }
    @Test void unreadableCorruptOrInvalidTtlStateIsUnavailable() throws Exception {
        Files.writeString(directory.resolve("delegations.json"), "not-json");
        assertThat(new MockDelegationRegistry(clock, directory).validate(call)).isEqualTo(MockDelegationRegistry.Validity.UNAVAILABLE);
        Files.writeString(directory.resolve("delegations.json"), "{\"" + MockDelegationRegistry.hash(call.dlg()) + "\":{\"sub\":\"actor\",\"issuedAt\":\"2026-11-06T00:00:00Z\",\"expiresAt\":\"2026-11-06T00:10:00Z\",\"revoked\":false}}");
        assertThat(new MockDelegationRegistry(clock, directory).validate(call)).isEqualTo(MockDelegationRegistry.Validity.UNAVAILABLE);
    }
    @Test void failedDurableWriteMakesStoreUnavailable() throws Exception {
        Path file = directory.resolve("not-directory"); Files.writeString(file, "blocked");
        var store = new MockDelegationRegistry(clock, file);
        assertThatThrownBy(() -> store.register(call.sub(), call.dlg())).isInstanceOf(IllegalStateException.class);
        assertThat(store.validate(call)).isEqualTo(MockDelegationRegistry.Validity.UNAVAILABLE);
    }
    @Test void scenarioSelectionSurvivesReloadAndUnknownIdDoesNotMutate() {
        var store = new MockScenarioStore(directory); store.select("logout");
        assertThat(new MockScenarioStore(directory).current().id()).isEqualTo("logout");
        assertThatThrownBy(() -> store.select("absent")).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.current().id()).isEqualTo("logout");
    }
}
