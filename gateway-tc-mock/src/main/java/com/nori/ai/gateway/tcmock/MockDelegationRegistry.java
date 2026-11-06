package com.nori.ai.gateway.tcmock;

import com.nori.ai.gateway.core.port.TcSourceAuthorizationPort.DelegatedCall;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;

public final class MockDelegationRegistry {
    public static final Duration DELEGATION_LIFETIME = Duration.ofSeconds(300);
    public enum Validity { VALID, INVALID, UNAVAILABLE }
    public enum Registration { REGISTERED, DUPLICATE }
    public record Entry(String sub, Instant issuedAt, Instant expiresAt, boolean revoked) {
        public Entry {
            if (sub == null || sub.isBlank() || issuedAt == null || expiresAt == null || !issuedAt.plus(DELEGATION_LIFETIME).equals(expiresAt))
                throw new IllegalArgumentException("위임 저장 상태가 잘못되었습니다");
        }
    }
    private final Clock clock;
    private final Path stateFile;
    private Map<String, Entry> entries = Map.of();
    private boolean available = true;
    public MockDelegationRegistry(Clock clock) { this(clock, null); }
    public MockDelegationRegistry(Clock clock, Path stateDirectory) {
        this.clock = Objects.requireNonNull(clock);
        stateFile = stateDirectory == null ? null : stateDirectory.resolve("delegations.json");
        if (stateFile != null && Files.exists(stateFile)) load();
    }
    public synchronized Registration register(String sub, String dlg) {
        var call = new DelegatedCall(sub, dlg);
        requireAvailable();
        String hash = hash(call.dlg());
        if (entries.containsKey(hash)) return Registration.DUPLICATE;
        Instant issuedAt = clock.instant();
        Map<String, Entry> next = new HashMap<>(entries);
        next.put(hash, new Entry(sub, issuedAt, issuedAt.plus(DELEGATION_LIFETIME), false));
        replace(next);
        return Registration.REGISTERED;
    }
    public synchronized void revoke(String dlgSha256) {
        if (dlgSha256 == null || !dlgSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("위임 해시 형식이 잘못되었습니다");
        requireAvailable();
        Entry old = entries.get(dlgSha256);
        if (old == null || old.revoked()) return;
        Map<String, Entry> next = new HashMap<>(entries);
        next.put(dlgSha256, new Entry(old.sub(), old.issuedAt(), old.expiresAt(), true));
        replace(next);
    }
    public synchronized Validity validate(DelegatedCall call) {
        if (!available) return Validity.UNAVAILABLE;
        Entry entry = entries.get(hash(call.dlg()));
        Instant now = clock.instant();
        return entry != null && entry.sub().equals(call.sub()) && !entry.revoked()
            && !now.isBefore(entry.issuedAt()) && now.isBefore(entry.expiresAt()) ? Validity.VALID : Validity.INVALID;
    }
    public synchronized Map<String, Entry> snapshot() { requireAvailable(); return entries; }
    public synchronized void setAvailable(boolean available) { this.available = available; }
    private void replace(Map<String, Entry> next) {
        try {
            if (stateFile != null) MockStateFiles.write(stateFile, SchemaContracts.JSON.writeValueAsString(next));
            entries = Map.copyOf(next);
        } catch (IOException | RuntimeException failure) { available = false; throw new IllegalStateException("위임 상태를 저장할 수 없습니다"); }
    }
    private void load() {
        try {
            var root = SchemaContracts.parse(Files.readString(stateFile));
            if (!root.isObject()) throw new IllegalArgumentException("위임 저장 상태가 잘못되었습니다");
            Map<String, Entry> loaded = new HashMap<>();
            for (var item : root.properties()) {
                var node = item.getValue();
                if (!item.getKey().matches("[0-9a-f]{64}") || !node.isObject() || node.size() != 4
                        || !node.has("sub") || !node.get("sub").isString() || !node.has("issuedAt") || !node.get("issuedAt").isString()
                        || !node.has("expiresAt") || !node.get("expiresAt").isString() || !node.has("revoked") || !node.get("revoked").isBoolean())
                    throw new IllegalArgumentException("위임 저장 상태가 잘못되었습니다");
                loaded.put(item.getKey(), new Entry(node.get("sub").asString(), Instant.parse(node.get("issuedAt").asString()),
                    Instant.parse(node.get("expiresAt").asString()), node.get("revoked").asBoolean()));
            }
            entries = Map.copyOf(loaded);
        } catch (IOException | RuntimeException failure) { available = false; }
    }
    private void requireAvailable() { if (!available) throw new IllegalStateException("위임 저장소를 사용할 수 없습니다"); }
    public static String hash(String dlg) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dlg.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("위임 해시를 계산할 수 없습니다"); }
    }
}
