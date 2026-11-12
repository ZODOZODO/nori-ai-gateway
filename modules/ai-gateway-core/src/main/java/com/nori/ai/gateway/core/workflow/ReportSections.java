package com.nori.ai.gateway.core.workflow;

import com.nori.ai.gateway.core.blocks.UiSchemaValidator;
import com.nori.ai.gateway.core.registry.SchemaContracts;
import com.nori.ai.gateway.core.result.ResultBlock;
import com.nori.ai.gateway.core.scope.OwnerSystem;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 상세 요청(answerMode D)의 구조화 근거 보고를 기존 {@code table} 블록 하나로 투영한다(SPEC §28.8, Plan §16.3 R4).
 * 새 Renderer 블록 타입·공용 API 최상위 필드를 만들지 않고 {@link UiSchemaValidator} 로 검증한다. 행은 요구 근거마다 하나이며
 * 구역(관측 사실·정상 조건·구현 버전·비교·조치·미확인), 수집 상태, 근거 단계, 최근 관측 시각, 현재 READ 가 제공하지 않는 요청 항목을 담는다.
 * 원인 가설·조치를 창작하지 않으며 수집하지 못한 근거는 미확인으로만 표시한다. 행의 근거는 같은 결과의 단계 결과에서 온 것이라 결과 Source 의존성을 그대로 상속한다.
 */
public final class ReportSections {
    static final List<String> COLUMNS = List.of("section", "evidence", "status", "sources", "observedAt", "missingItems", "serverAdded");
    private final UiSchemaValidator validator;

    public ReportSections(UiSchemaValidator validator) { this.validator = Objects.requireNonNull(validator, "validator"); }

    public ResultBlock project(List<EvidenceCoverage> coverage, OwnerSystem owner) {
        var content = SchemaContracts.JSON.createObjectNode();
        var columns = content.putArray("columns"); COLUMNS.forEach(columns::add);
        var rows = content.putArray("rows");
        for (EvidenceCoverage value : coverage) {
            var row = rows.addObject();
            row.put("section", section(value));
            row.put("evidence", label(value.need()));
            row.put("status", value.status().name());
            var sources = row.putArray("sources"); value.sources().forEach(source -> sources.add(source.stepId()));
            Instant latest = value.sources().stream().map(EvidenceCoverage.Source::observedAt).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
            if (latest == null) row.putNull("observedAt"); else row.put("observedAt", latest.toString());
            var missing = row.putArray("missingItems");
            // 수집하지 못했으면 이 근거가 채울 보고 항목 전부가, 수집했어도 현재 READ 가 제공하지 않는 항목은 누락으로 남는다.
            if (!value.collected()) value.reportItems().forEach(missing::add);
            value.unavailableItems().forEach(missing::add);
            row.put("serverAdded", value.serverAdded());
        }
        var block = new ResultBlock("table", null, content);
        validator.validate(block, owner);
        return block;
    }

    private static String section(EvidenceCoverage value) {
        if (value.need() == EvidenceNeed.C) return "비교";
        if (!value.collected()) return "미확인";
        return switch (value.need()) {
            case S, H -> "관측 사실";
            case N -> "정상 조건";
            case I -> "구현 버전";
            case R -> "확인·조치 절차";
            case C -> "비교";
        };
    }

    private static String label(EvidenceNeed need) {
        return switch (need) {
            case S -> "현재 상태";
            case H -> "실제 발생 이력";
            case N -> "정상 절차·선행 조건";
            case I -> "구현·적용 버전";
            case C -> "기대와 실제 비교";
            case R -> "확인·조치 절차";
        };
    }
}
