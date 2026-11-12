package com.nori.ai.gateway.app.job;

import com.nori.ai.gateway.core.budget.PromptDraft;
import com.nori.ai.gateway.core.job.JobContext;
import com.nori.ai.gateway.core.llm.ComposerService;
import com.nori.ai.gateway.core.registry.CapabilityRegistry;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.registry.ToolKind;
import com.nori.ai.gateway.core.result.ResultBlock;
import com.nori.ai.gateway.core.sanitize.ContentSanitizer;
import com.nori.ai.gateway.core.workflow.LogSummarizer;
import com.nori.ai.gateway.core.workflow.WorkflowOutcome;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Composer 결정론 입력(Plan P20). Source 응답 텍스트는 ContentSanitizer(mask-v1) 적용분만 넣는다 — 이후 PromptReducer·예산 assert 는 ComposerService·Client 몫이다.
 * GENERAL_QUERY 는 Registry view 도움말 표(Manifest 문구)만 넣는다. 분류 wire v3 실행은 요구 근거별 Coverage 를 함께 넣는다. LOG 단계의 원시 줄은 축소 3단계 대상으로 따로 두고 결정론 요약은 유지한다.
 */
final class ComposerInputs {
    private ComposerInputs() {}

    static ComposerService.Input build(JobContext job, WorkflowOutcome outcome, boolean registryView, CapabilityRegistry registry, ContentSanitizer sanitizer, LogSummarizer summarizer) {
        List<ComposerService.EvidenceDigest> evidence = new ArrayList<>();
        for (var value : outcome.evidence())
            evidence.add(new ComposerService.EvidenceDigest(WorkflowContent.text(value.sourceType(), sanitizer),
                value.eventTime() == null ? null : value.eventTime().toString(), value.observedAt() == null ? null : value.observedAt().toString()));
        List<PromptDraft.ToolResult> tools = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        if (registryView) {
            var help = SchemaContracts.JSON.createArrayNode();
            for (ResultBlock block : outcome.blocks()) if (block.content() != null) help.add(WorkflowContent.mask(block.content(), sanitizer));
            String text = canonical(help);
            tools.add(new PromptDraft.ToolResult("registry-view", text, text));
        }
        for (var stage : outcome.stages()) {
            var result = stage.result();
            var manifest = registry.find(result.capabilityId()).orElse(null);
            if (manifest == null || result.blocks().isEmpty()) continue;
            var contents = SchemaContracts.JSON.createArrayNode();
            var required = SchemaContracts.JSON.createArrayNode();
            for (ResultBlock block : result.blocks()) {
                if (block.content() == null) continue;
                JsonNode masked = WorkflowContent.mask(block.content(), sanitizer);
                if (manifest.toolKind() == ToolKind.LOG) masked = withoutLogText(masked, lines);
                contents.add(masked);
                required.add(requiredOnly(masked, manifest.outputSchema()));
            }
            tools.add(new PromptDraft.ToolResult(stage.stepId(), canonical(contents), canonical(required)));
        }
        String summary = lines.isEmpty() ? "" : canonical(SchemaContracts.JSON.valueToTree(summarizer.extract(lines)));
        return new ComposerService.Input(evidence, tools, summary, lines, job.requestInput().maskedQuestion(), coverage(outcome));
    }

    /** 요구 근거별 수집 상태(Plan §16.3 R4). 수집하지 못한 근거는 채울 보고 항목 전부, 수집한 근거도 현재 READ 미제공 항목을 누락으로 넘긴다. */
    private static List<ComposerService.CoverageDigest> coverage(WorkflowOutcome outcome) {
        List<ComposerService.CoverageDigest> values = new ArrayList<>();
        for (var value : outcome.coverage()) {
            List<String> missing = new ArrayList<>();
            if (!value.collected()) missing.addAll(value.reportItems());
            missing.addAll(value.unavailableItems());
            values.add(new ComposerService.CoverageDigest(value.need().name(), value.status().name(), value.sources().stream().map(source -> source.stepId()).toList(), missing));
        }
        return List.copyOf(values);
    }

    /** 원시 로그 줄은 따로 모으고(축소 3단계) 단계 결과에서는 뺀다. */
    private static JsonNode withoutLogText(JsonNode content, List<String> lines) {
        if (!content.isObject() || !content.path("items").isArray()) return content;
        ObjectNode copy = (ObjectNode) content.deepCopy();
        for (JsonNode item : (ArrayNode) copy.get("items")) if (item.isObject() && item.path("text").isString()) lines.add(((ObjectNode) item).remove("text").asString());
        return copy;
    }

    /** outputSchema 의 {@code required} 를 따라 필수 필드만 남긴다(축소 4단계). required 가 없는 객체는 그대로 둔다. */
    static JsonNode requiredOnly(JsonNode value, JsonNode schema) {
        if (schema == null || !schema.isObject()) return value;
        if (value.isObject() && schema.path("required").isArray()) {
            ObjectNode projected = SchemaContracts.JSON.createObjectNode();
            for (JsonNode name : schema.get("required")) {
                String key = name.asString();
                if (value.has(key)) projected.set(key, requiredOnly(value.get(key), schema.path("properties").path(key)));
            }
            return projected;
        }
        if (value.isArray() && schema.path("items").isObject()) {
            ArrayNode projected = SchemaContracts.JSON.createArrayNode();
            for (JsonNode item : value) projected.add(requiredOnly(item, schema.get("items")));
            return projected;
        }
        return value;
    }

    private static String canonical(JsonNode node) { return SchemaContracts.canonicalize(SchemaContracts.JSON.writeValueAsString(node)); }
}
