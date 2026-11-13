package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.nori.ai.gateway.tcmock.PdgCanonical;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * F2 수용 고정 입력. actor 단언은 F1 과 같은 권한 목록을 쓰되 sub 만 분리하고, 자유 서술 fixture 는 사전등록 {@code rubric/f2-v1.json} 에서만 읽는다.
 * 기대 Capability 목록은 배포본 Manifest 와 같은지 단위 시험(F2FixtureTest)이 결속한다.
 */
public final class F2Fixture {
    public static final String SUB = "f2-acceptance";
    public static final String RUBRIC_RESOURCE = "rubric/f2-v1.json";
    public static final Set<String> FREE_TEXT_LEAVES = Set.of("AC-22-LIVE", "AC-22-GQ-BYPASS-DATA", "AC-22-GQ-BYPASS-XSYS");
    /** boundSystem=TC Registry 의 IMPLEMENTED Capability(Manifest index 순서). AC-22-LIVE 실행 Tool 상한. */
    public static final List<String> IMPLEMENTED = List.of("tc.eqp.getOperationalSnapshot", "tc.commLogs.search", "tc.event.getRecent", "tc.dlq.list", "tc.logs.search", "tc.trace.search", "tc.git.searchCode", "tc.model.read");
    /** IMPLEMENTED 중 F2 actor 권한으로 인가되는 Capability(requiredPermission 비어 있는 tc.event.getRecent 제외). AC-22-GQ-HELP 표 행 기대값. */
    public static final List<String> GQ_HELP_ROWS = List.of("tc.eqp.getOperationalSnapshot", "tc.commLogs.search", "tc.dlq.list", "tc.logs.search", "tc.trace.search", "tc.git.searchCode", "tc.model.read");
    /** 첫 GQ 행 Manifest description 앞 40자. Composer 입력 근거가 Manifest 문구라는 관찰 sentinel. */
    public static final String GQ_MANIFEST_SNIPPET = "설비 운영 상태 조회 (Gateway Mock 단계 계약, 실제 TC 배";
    public static final List<String> PLANNED = List.of("tc.knowledge.search", "tc.knowledge.getSource");
    public record FreeText(String fixtureId, String leafId, String question, Optional<String> composerScript, List<Pattern> forbidden) {
        @Override public String toString() { return "F2FreeText[" + fixtureId + "]"; }
    }
    private static final List<FreeText> FIXTURES = load();
    private F2Fixture() {}

    public static Map<String,Object> claims() {
        return Map.of("iss", "tc-ui-backend", "aud", "ai-gateway", "sys", "TC", "sub", SUB, "scope", "READ",
            "perm", F1Fixture.PERMISSIONS, "pdg", PdgCanonical.compute("mock-v1", F1Fixture.PERMISSIONS));
    }
    public static String rubricVersion() { return "f2-v1"; }
    public static List<FreeText> fixtures(String leaf) { return FIXTURES.stream().filter(fixture -> fixture.leafId().equals(leaf)).toList(); }
    /** 사전등록 fixture 와 leaf 가 정확히 맞는 대상만 원래 actor 로 재조회한다. */
    public static Optional<Map<String,Object>> claimsFor(RubricViewer.Target target) {
        return FIXTURES.stream().anyMatch(fixture -> fixture.fixtureId().equals(target.fixtureId()) && fixture.leafId().equals(target.leafId())) ? Optional.of(claims()) : Optional.empty();
    }
    /** 금지 claim 표현에 걸린 fixture 서술은 자동 FAIL 이다(걸리지 않음은 PASS 근거가 아니다). */
    public static boolean forbiddenHit(FreeText fixture, String narrative) { return fixture.forbidden().stream().anyMatch(pattern -> pattern.matcher(narrative).find()); }

    private static List<FreeText> load() {
        try (var input = F2Fixture.class.getClassLoader().getResourceAsStream(RUBRIC_RESOURCE)) {
            if (input == null) throw new IllegalStateException("F2_RUBRIC_MISSING");
            JsonNode root = CanonicalJson.MAPPER.readTree(input.readAllBytes());
            if (!root.path("rubricVersion").asText().equals("f2-v1") || !root.path("rubric").isArray() || root.path("rubric").isEmpty()) throw new IllegalStateException("F2_RUBRIC_INVALID");
            var values = new ArrayList<FreeText>(); var ids = new HashSet<String>();
            for (JsonNode node : root.path("fixtures")) {
                String id = node.path("fixtureId").asText(), leaf = node.path("leafId").asText(), question = node.path("question").asText();
                if (!id.matches("f2-[a-z0-9-]{1,60}") || !ids.add(id) || !FREE_TEXT_LEAVES.contains(leaf) || question.isBlank() || !node.path("forbiddenPatterns").isArray() || node.path("forbiddenPatterns").isEmpty()) throw new IllegalStateException("F2_RUBRIC_INVALID");
                var patterns = new ArrayList<Pattern>(); node.path("forbiddenPatterns").forEach(pattern -> patterns.add(Pattern.compile(pattern.asText())));
                Optional<String> script = node.hasNonNull("composerScript") ? Optional.of(node.path("composerScript").asText()) : Optional.empty();
                if (leaf.startsWith("AC-22-GQ-") != script.isPresent()) throw new IllegalStateException("F2_RUBRIC_INVALID");
                values.add(new FreeText(id, leaf, question, script, List.copyOf(patterns)));
            }
            for (String leaf : FREE_TEXT_LEAVES) if (values.stream().noneMatch(value -> value.leafId().equals(leaf))) throw new IllegalStateException("F2_RUBRIC_INVALID");
            return List.copyOf(values);
        } catch (IOException e) { throw new IllegalStateException("F2_RUBRIC_INVALID"); }
    }
}
