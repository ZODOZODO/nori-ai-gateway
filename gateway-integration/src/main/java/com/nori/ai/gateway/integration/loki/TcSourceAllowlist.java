package com.nori.ai.gateway.integration.loki;

import com.fasterxml.jackson.databind.*;
import com.nori.ai.gateway.integration.gitlab.GitLabSourcePolicy;
import java.io.IOException;
import java.util.*;

/** 선언 파일의 소유권·선택자를 검증한 뒤 승인된 GitLab 범위만 투영합니다. */
public final class TcSourceAllowlist {
    private TcSourceAllowlist() {}
    public static GitLabSourcePolicy parse(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            if (root == null || !"TC".equals(root.path("ownerSystem").asText())
                    || !"tc".equals(root.path("loki").path("namespace").asText())
                    || !"sources://TC/logs".equals(root.path("loki").path("sourceAllowlistRef").asText())
                    || !"sources://TC/trace".equals(root.path("tempo").path("sourceAllowlistRef").asText())
                    || !"sources://TC/git".equals(root.path("gitlab").path("sourceAllowlistRef").asText())
                    || !strings(root.path("loki").path("apps")).equals(Set.copyOf(LokiSourcePolicy.APPS))
                    || !strings(root.path("tempo").path("services")).equals(Set.copyOf(LokiSourcePolicy.APPS)))
                throw invalid();
            JsonNode gitlab = root.path("gitlab");
            if (!gitlab.path("repositories").isArray() || !gitlab.path("deployLineage").isArray()) throw invalid();
            List<GitLabSourcePolicy.Repository> repositories = new ArrayList<>();
            for (JsonNode repo : gitlab.path("repositories")) repositories.add(new GitLabSourcePolicy.Repository(
                positiveId(repo.path("projectId")), text(repo, "sourceRepo"), strings(repo.path("refs")), strings(repo.path("pathPrefixes")), text(repo, "approvalRef")));
            List<GitLabSourcePolicy.LineageEntry> lineage = new ArrayList<>();
            for (JsonNode entry : gitlab.path("deployLineage")) lineage.add(new GitLabSourcePolicy.LineageEntry(text(entry, "service"), positiveId(entry.path("execProjectId")),
                text(entry, "packageJobName"), text(entry, "sourceRepo"), positiveId(entry.path("siblingProjectId")), text(entry, "method"), text(entry, "approvalRef")));
            return new GitLabSourcePolicy(repositories, lineage);
        } catch (IOException | RuntimeException failure) { throw invalid(); }
    }
    private static Set<String> strings(JsonNode node) {
        if (!node.isArray()) throw invalid();
        Set<String> values = new HashSet<>();
        for (JsonNode value : node) if (!value.isTextual() || value.asText().isBlank() || !values.add(value.asText())) throw invalid();
        return Set.copyOf(values);
    }
    private static long positiveId(JsonNode node) { if (!node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() <= 0) throw invalid(); return node.asLong(); }
    private static String text(JsonNode node, String name) { if (!node.path(name).isTextual() || node.path(name).asText().isBlank()) throw invalid(); return node.path(name).asText(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("TC Source 허용 목록이 잘못되었습니다"); }
}
