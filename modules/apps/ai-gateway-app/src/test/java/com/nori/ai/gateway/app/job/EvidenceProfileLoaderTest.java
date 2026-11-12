package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.registry.CapabilityRegistry;
import com.nori.ai.gateway.core.registry.ContractVersionPolicy;
import com.nori.ai.gateway.core.registry.ImplementationPhase;
import com.nori.ai.gateway.core.registry.ManifestLoader;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import com.nori.ai.gateway.core.workflow.EvidenceNeed;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

/** 운영 TC 승인 근거 프로필과 명시 loader 의 검증 경계(Plan §16.3 R1): 각 사례는 정상 프로필에 결함 하나만 주입한다. */
class EvidenceProfileLoaderTest {
    private final ManifestLoader manifests = new ManifestLoader();
    private final CapabilityRegistry registry = new CapabilityRegistry(manifests.loadClasspath("manifests/tc/index.json"), manifests.loadWorkflowsClasspath("workflows/tc/index.json"),
        Set.of(ImplementationPhase.PHASE_1), new ContractVersionPolicy());
    private final EvidenceProfileLoader loader = new EvidenceProfileLoader(registry);

    private static String original() throws Exception {
        try (var input = EvidenceProfileLoaderTest.class.getClassLoader().getResourceAsStream(EvidenceProfileLoader.TC_PROFILE)) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }
    private String mutate(Consumer<ObjectNode> change) throws Exception {
        var root = (ObjectNode) SchemaContracts.JSON.readTree(original());
        change.accept(root);
        return root.toString();
    }
    private static ObjectNode entry(ObjectNode root, String id) {
        for (var node : root.get("entries")) if (node.get("id").asString().equals(id)) return (ObjectNode) node;
        throw new AssertionError(id);
    }

    @Test void operationalProfileLoadsWithReadOnlyTcCapabilitiesAndDeclaredMissingItems() {
        var profile = loader.loadClasspath(EvidenceProfileLoader.TC_PROFILE, OwnerSystem.TC);
        assertThat(profile.ownerSystem()).isEqualTo(OwnerSystem.TC);
        assertThat(profile.version()).isEqualTo("tc-evidence-profile-v1");
        assertThat(profile.entriesFor(EvidenceNeed.S)).extracting(e -> e.capabilityId()).containsExactly("tc.eqp.getOperationalSnapshot");
        assertThat(profile.entriesFor(EvidenceNeed.S).getFirst().unavailableItems()).as("현재 EqpSnapshot 에 없는 Port·진행 LOT 는 누락으로 선언").containsExactly("Port 목록", "진행 LOT");
        assertThat(profile.entriesFor(EvidenceNeed.C)).isEmpty();
        assertThat(profile.entry("tc.impl.git").dependsOn()).containsExactly("tc.history.logs");
        for (var entry : profile.entries()) assertThat(registry.find(entry.capabilityId()).orElseThrow().ownerSystem()).isEqualTo(OwnerSystem.TC);
    }

    @Test void everySingleDefectIsRejectedAtLoad() throws Exception {
        var cases = new java.util.LinkedHashMap<String,String>();
        cases.put("다른 대상 시스템", mutate(root -> root.put("ownerSystem", "WM")));
        cases.put("schema", mutate(root -> root.put("schema", "evidence-profile-v2")));
        cases.put("최상위 추가 키", mutate(root -> root.put("script", "rm -rf")));
        cases.put("항목 추가 키(SQL)", mutate(root -> entry(root, "tc.state.snapshot").put("sql", "SELECT 1")));
        cases.put("항목 추가 키(URL)", mutate(root -> entry(root, "tc.state.snapshot").put("url", "http://example")));
        cases.put("허용되지 않은 근거 코드", mutate(root -> entry(root, "tc.state.snapshot").put("need", "X")));
        cases.put("C 원천 항목", mutate(root -> entry(root, "tc.state.snapshot").put("need", "C")));
        cases.put("Registry 밖 Capability", mutate(root -> entry(root, "tc.state.snapshot").put("capabilityId", "tc.fake.read")));
        cases.put("타 owner Capability", mutate(root -> entry(root, "tc.state.snapshot").put("capabilityId", "wm.inventory.get")));
        cases.put("승인되지 않은 argument", mutate(root -> ((ObjectNode) entry(root, "tc.state.snapshot").get("inputs").get(0)).put("name", "semanticQuery")));
        cases.put("argument 에 경로", mutate(root -> ((ObjectNode) entry(root, "tc.state.snapshot").get("inputs").get(0)).put("fieldPath", "items")));
        cases.put("등록되지 않은 출력 경로", mutate(root -> ((ObjectNode) entry(root, "tc.impl.git").get("inputs").get(0)).put("fieldPath", "rows")));
        cases.put("선행 근거 아닌 바인딩", mutate(root -> ((ObjectNode) entry(root, "tc.impl.git").get("inputs").get(0)).put("entryId", "tc.history.dlq")));
        cases.put("미선언 선행 근거", mutate(root -> ((ArrayNode) entry(root, "tc.state.snapshot").get("dependsOn")).add("tc.impl.git")));
        cases.put("중복 ID", mutate(root -> entry(root, "tc.history.dlq").put("id", "tc.history.logs")));
        cases.put("required 형식", mutate(root -> entry(root, "tc.state.snapshot").put("required", "yes")));
        cases.put("빈 entries", mutate(root -> root.putArray("entries")));
        cases.put("JSON 형식", "{");
        for (var entry : cases.entrySet())
            assertThatThrownBy(() -> loader.parse(entry.getValue(), OwnerSystem.TC)).as(entry.getKey()).isInstanceOf(EvidenceProfileLoader.InvalidProfile.class);
        assertThatThrownBy(() -> loader.loadClasspath("workflows/tc/missing.json", OwnerSystem.TC)).isInstanceOf(EvidenceProfileLoader.InvalidProfile.class);
    }

    @Test void mutationCapabilitiesAreRejectedEvenWhenRegistered() throws Exception {
        var mutated = manifests.loadClasspath("manifests/tc/index.json").stream().map(m -> m.capabilityId().equals("tc.dlq.list")
            ? new com.nori.ai.gateway.core.registry.CapabilityManifest(m.toolId(), m.capabilityId(), m.contractVersion(), m.ownerSystem(), m.toolKind(), m.sourceAllowlistRef(), m.availability(),
                m.implementationPhase(), m.description(), m.exampleQuestions(), m.riskLevel(), "WRITE", com.nori.ai.gateway.core.registry.SideEffects.SYSTEM_WRITE, m.resourceScope(), m.resourceTypes(),
                m.requiredPermission(), m.requiredActorScope(), m.requiresApproval(), m.supportsAsync(), m.supportsCancel(), m.idempotent(), m.retryPolicy(), m.inputSchemaRef(), m.outputSchemaRef(),
                m.inputSchema(), m.outputSchema(), m.schemaDigest(), m.timeoutMs(), m.maxResourcesPerCall(), m.inputLimits(), m.auditPolicy(), m.adapterVersion(), m.sourceFreshnessPolicy(), m.outputRefType(), m.cost())
            : m).toList();
        var writable = new EvidenceProfileLoader(new CapabilityRegistry(mutated, manifests.loadWorkflowsClasspath("workflows/tc/index.json"), Set.of(ImplementationPhase.PHASE_1), new ContractVersionPolicy()));
        assertThatThrownBy(() -> writable.parse(original(), OwnerSystem.TC)).isInstanceOf(EvidenceProfileLoader.InvalidProfile.class).hasMessageContaining("Mutation");
    }
}
