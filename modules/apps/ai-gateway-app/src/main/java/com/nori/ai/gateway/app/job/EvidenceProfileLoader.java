package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.registry.CapabilityManifest;
import com.nori.ai.gateway.core.registry.CapabilityRegistry;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.registry.SideEffects;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.workflow.EvidenceNeed;
import com.nori.ai.gateway.core.workflow.EvidencePlanProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 시스템별 승인 근거 프로필을 기존 {@code workflows/tc/index.json} 과 분리해 명시적으로 읽는다(Plan §16.3 R1, 자원 {@code workflows/tc/evidence-profile-v1.json}).
 * 닫힌 키만 허용하고 owner·허용 근거 코드·Capability 존재·타 owner·Mutation·argument 이름·선행 근거 출력 경로·의존 DAG·중복 ID 를 검사한다.
 * 형식 위반은 기동 실패다(임의 script·SpEL·SQL·URL·repo·Source 경로 필드는 키 집합 밖이라 거부된다).
 */
public final class EvidenceProfileLoader {
    public static final String TC_PROFILE = "workflows/tc/evidence-profile-v1.json";
    private static final Set<String> ROOT_KEYS = Set.of("schema", "ownerSystem", "version", "entries");
    private static final Set<String> ENTRY_KEYS = Set.of("id", "need", "capabilityId", "required", "inputs", "dependsOn", "reportItems", "unavailableItems");
    private static final Set<String> ARGUMENT_KEYS = Set.of("name", "source"), FIELD_KEYS = Set.of("name", "source", "entryId", "fieldPath");

    public static final class InvalidProfile extends IllegalStateException {
        InvalidProfile(String reason) { super("근거 프로필이 잘못되었습니다: " + reason); }
    }

    private final CapabilityRegistry registry;

    public EvidenceProfileLoader(CapabilityRegistry registry) { this.registry = Objects.requireNonNull(registry, "registry"); }

    public EvidencePlanProfile loadClasspath(String path, OwnerSystem expectedOwner) {
        try (var input = EvidenceProfileLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new InvalidProfile("자원이 없습니다(" + path + ")");
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8), expectedOwner);
        } catch (IOException failure) { throw new InvalidProfile("자원을 읽을 수 없습니다(" + path + ")"); }
    }

    public EvidencePlanProfile parse(String text, OwnerSystem expectedOwner) {
        final JsonNode root;
        try { root = SchemaContracts.JSON.readTree(text); }
        catch (RuntimeException failure) { throw new InvalidProfile("JSON 형식"); }
        keys(root, ROOT_KEYS, "최상위");
        if (!"evidence-profile-v1".equals(string(root, "schema"))) throw new InvalidProfile("schema");
        OwnerSystem owner = owner(string(root, "ownerSystem"));
        if (owner != expectedOwner) throw new InvalidProfile("ownerSystem 이 대상 시스템과 다릅니다");
        if (!root.get("entries").isArray()) throw new InvalidProfile("entries");
        Map<String,CapabilityManifest> capabilityOf = new HashMap<>();
        List<EvidencePlanProfile.Entry> entries = new ArrayList<>();
        for (JsonNode node : root.get("entries")) {
            keys(node, ENTRY_KEYS, "항목");
            String id = string(node, "id");
            final EvidenceNeed need;
            try { need = EvidenceNeed.valueOf(string(node, "need")); }
            catch (IllegalArgumentException unknown) { throw new InvalidProfile("허용되지 않은 근거 코드(" + id + ")"); }
            if (need == EvidenceNeed.C) throw new InvalidProfile("C 는 파생 비교라 원천 항목이 될 수 없습니다(" + id + ")");
            String capabilityId = string(node, "capabilityId");
            CapabilityManifest manifest = registry.find(capabilityId).orElseThrow(() -> new InvalidProfile("Registry 에 없는 Capability(" + id + ")"));
            if (manifest.ownerSystem() != owner) throw new InvalidProfile("타 owner Capability(" + id + ")");
            if (!"READ".equals(manifest.operation()) || manifest.sideEffects() != SideEffects.NONE) throw new InvalidProfile("Mutation Capability(" + id + ")");
            if (!node.get("required").isBoolean()) throw new InvalidProfile("required(" + id + ")");
            List<String> dependsOn = strings(node, "dependsOn");
            for (String dependency : dependsOn) if (!capabilityOf.containsKey(dependency)) throw new InvalidProfile("선행 근거가 앞에 없습니다 — 순환 또는 미선언(" + id + ")");
            List<EvidencePlanProfile.Input> inputs = new ArrayList<>();
            if (!node.get("inputs").isArray()) throw new InvalidProfile("inputs(" + id + ")");
            for (JsonNode input : node.get("inputs")) inputs.add(input(input, id, manifest, dependsOn, capabilityOf));
            if (capabilityOf.put(id, manifest) != null) throw new InvalidProfile("중복 ID(" + id + ")");
            try { entries.add(new EvidencePlanProfile.Entry(id, need, capabilityId, node.get("required").asBoolean(), inputs, dependsOn, strings(node, "reportItems"), strings(node, "unavailableItems"))); }
            catch (IllegalArgumentException invalid) { throw new InvalidProfile("항목 형식(" + id + ")"); }
        }
        try { return new EvidencePlanProfile(owner, string(root, "version"), entries); }
        catch (IllegalArgumentException invalid) { throw new InvalidProfile("프로필 구조"); }
    }

    private static EvidencePlanProfile.Input input(JsonNode input, String id, CapabilityManifest manifest, List<String> dependsOn, Map<String,CapabilityManifest> capabilityOf) {
        if (!input.isObject()) throw new InvalidProfile("입력 바인딩(" + id + ")");
        String source = string(input, "source"), name = string(input, "name");
        if (!manifest.inputSchema().path("properties").has(name)) throw new InvalidProfile("승인되지 않은 argument 이름(" + id + ")");
        switch (source) {
            case "ARGUMENT" -> { keys(input, ARGUMENT_KEYS, "입력 바인딩"); return new EvidencePlanProfile.Input(name, EvidencePlanProfile.InputSource.ARGUMENT, null, null); }
            case "STEP_FIELD" -> {
                keys(input, FIELD_KEYS, "입력 바인딩");
                String entry = string(input, "entryId"), field = string(input, "fieldPath");
                if (!dependsOn.contains(entry)) throw new InvalidProfile("바인딩 출처가 선행 근거가 아닙니다(" + id + ")");
                if (!capabilityOf.get(entry).outputSchema().path("properties").has(field)) throw new InvalidProfile("등록되지 않은 출력 경로(" + id + ")");
                return new EvidencePlanProfile.Input(name, EvidencePlanProfile.InputSource.STEP_FIELD, entry, field);
            }
            default -> throw new InvalidProfile("입력 바인딩 출처(" + id + ")");
        }
    }

    private static OwnerSystem owner(String value) {
        try { return OwnerSystem.valueOf(value); }
        catch (IllegalArgumentException unknown) { throw new InvalidProfile("ownerSystem"); }
    }

    private static void keys(JsonNode node, Set<String> allowed, String where) {
        if (node == null || !node.isObject() || !Set.copyOf(node.propertyNames()).equals(allowed)) throw new InvalidProfile(where + " 키 집합");
    }

    private static String string(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isString() || value.asString().isBlank()) throw new InvalidProfile(key);
        return value.asString();
    }

    private static List<String> strings(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isArray()) throw new InvalidProfile(key);
        List<String> values = new ArrayList<>();
        for (JsonNode item : value) { if (!item.isString() || item.asString().isBlank()) throw new InvalidProfile(key); values.add(item.asString()); }
        return List.copyOf(values);
    }
}
