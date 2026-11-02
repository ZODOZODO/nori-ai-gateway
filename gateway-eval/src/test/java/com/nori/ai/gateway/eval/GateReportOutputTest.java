package com.nori.ai.gateway.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** gate --out: 파생 보고서는 측정 회차 원시 디렉터리·evidence 밖에 새로만 쓰고, 생략하면 기존 경로 동작을 유지합니다. */
class GateReportOutputTest {
    @TempDir Path directory;

    int gate(TestSupport t, ByteArrayOutputStream err, String... extra) {
        List<String> args = new ArrayList<>(List.of("gate", "--stage", "F0", "--parent-run-id", "R1")); args.addAll(List.of(extra));
        return new EvalCli(Map.of("AIGW_SPEC_FILE", t.spec.toString()), new PrintStream(new ByteArrayOutputStream()), new PrintStream(err, true, StandardCharsets.UTF_8), t.root).execute(args.toArray(String[]::new));
    }

    @Test void derivedReportIsWrittenOnlyOutsideRawRunTreesAndEvidenceWithoutOverwriting() throws Exception {
        TestSupport t = new TestSupport(directory);
        Files.createDirectories(t.root.resolve("eval/f0/results/R1")); Files.writeString(t.root.resolve("eval/f0/results/R1/run-binding.json"), "{}");
        for (String invalid : List.of("eval/f0/results/R1/gate-report.md", "eval/f0/results/R1/sub/gate.md", "eval/f0/results/gate.md", "eval/f0/results/gate-reports/x/R1.md", "evidence/F0/R1.md", "evidence/F0/GATE-REPORT.md", "../outside/R1.md", "eval/f0/results/gate-reports/R1.txt")) {
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            assertThat(gate(t, err, "--out", invalid)).as(invalid).isEqualTo(2);
            assertThat(err.toString(StandardCharsets.UTF_8).strip()).isEqualTo("GATE_REPORT_PATH_INVALID");
        }
        assertThat(t.root.resolve("evidence")).doesNotExist();
        try (var walk = Files.walk(t.root.resolve("eval/f0/results/R1"))) { assertThat(walk.filter(Files::isRegularFile).toList()).hasSize(1); }

        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertThat(gate(t, err, "--out", "eval/f0/results/gate-reports/R1.md")).isZero();
        Path report = t.root.resolve("eval/f0/results/gate-reports/R1.md");
        assertThat(Files.readString(report, StandardCharsets.UTF_8)).contains("단계: F0", "부모 회차: R1");
        byte[] before = Files.readAllBytes(report);
        assertThat(gate(t, err, "--out", "eval/f0/results/gate-reports/R1.md")).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("OUTPUT_EXISTS");
        assertThat(Files.readAllBytes(report)).isEqualTo(before);
        assertThat(t.root.resolve("evidence/F0/GATE-REPORT.md")).doesNotExist();

        assertThat(gate(t, new ByteArrayOutputStream(), "--out", "docs/gates/f0/R1-gate.md")).isZero();
        // --out 생략 시 기존 정본 경로 동작(이 경로만 덮어쓰기)을 유지합니다.
        assertThat(gate(t, new ByteArrayOutputStream())).isZero();
        assertThat(t.root.resolve("evidence/F0/GATE-REPORT.md")).exists();
    }
}
