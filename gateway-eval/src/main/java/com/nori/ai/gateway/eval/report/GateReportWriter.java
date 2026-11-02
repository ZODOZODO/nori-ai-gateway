package com.nori.ai.gateway.eval.report;

import com.nori.ai.gateway.eval.EvalException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;

public final class GateReportWriter {
    public void write(Path output, GateEvaluator.GateReport report) {
        try { Files.createDirectories(output.toAbsolutePath().getParent()); Files.writeString(output, render(report), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new EvalException(5, "OUTPUT_WRITE_FAILED"); }
    }
    /** 회차별 파생 보고서: 이미 있으면 덮어쓰지 않습니다. */
    public void writeNew(Path output, GateEvaluator.GateReport report) {
        try { Files.createDirectories(output.toAbsolutePath().getParent()); Files.writeString(output, render(report), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
        catch (FileAlreadyExistsException e) { throw new EvalException(2, "OUTPUT_EXISTS"); }
        catch (IOException e) { throw new EvalException(5, "OUTPUT_WRITE_FAILED"); }
    }
    private static String render(GateEvaluator.GateReport report) {
        StringBuilder text = new StringBuilder("# 단계 게이트 판정\n\n");
        text.append("단계: ").append(report.stage()).append(" · 부모 회차: ").append(report.parentRunId()).append(" · 기준선 seq: ").append(report.baselineSeq()).append(" · 판정: ").append(report.result()).append("\n\n");
        text.append("| leaf | component | 판정 | 선택 회차 | 사유 |\n|---|---|---|---|---|\n");
        for (var leaf : report.leaves()) for (var component : leaf.components()) text.append('|').append(leaf.leafId()).append('|').append(component.componentId()).append('|').append(component.result()).append('|').append(component.runId()).append('|').append(component.reasonCode()).append("|\n");
        text.append("\n| 상위 ID | 현재 단계 | 전체 범위 |\n|---|---|---|\n");
        report.allParents().forEach((id, verdict) -> text.append('|').append(id).append('|').append(report.stageParents().getOrDefault(id, "NOT_RUN")).append('|').append(verdict).append("|\n"));
        text.append("\n다른 부모 회차 정보 레코드: ").append(report.informationalRecordCount()).append("건. 후속 단계 NOT_RUN은 현재 단계 판정에 포함하지 않습니다.\n");
        return text.toString();
    }
}
