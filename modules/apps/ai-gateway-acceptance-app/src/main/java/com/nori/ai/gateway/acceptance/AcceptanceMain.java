package com.nori.ai.gateway.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.*;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

/**
 * 배포 수용 Job 진입점. 첫 단계는 {@link SpecInputVerifier} 이고 실패하면 시험을 하나도 실행하지 않고 RUN ABORTED 1건·exit 3 이다.
 * 일치하면 {@code aigw.acceptance.suite} 의 시험만 JUnit Platform Launcher 로 순차 실행하고, OTR 은 tmpfs 에만 쓴 뒤 정제 레코드만 블록으로 낸다.
 * {@code -OUTAGE} suite 는 {@code AIGW_OUTAGE_PHASE}(STOPPED·LOADING) 의 {@code outage-<phase>} 태그 시험만 실행하고 모든 레코드에 {@code outagePhase} 를 싣는다(gate 의 component 단계 대조).
 * 종료 코드: 0 실행 시험 전부 성공(skip·abort 0), 1 실패·skip·abort·하네스 오류, 2 입력 오류, 3 SPEC 입력 실패, 4 실행 시험 0.
 * 프로세스 stdout 은 증거 블록 전용이다: 시험·라이브러리가 System.out/err 에 쓰는 내용과 예외 원문은 버린다.
 */
public final class AcceptanceMain {
    public static final String SUITE_PACKAGE = "com.nori.ai.gateway.acceptance.suite";
    /** JUnit Platform Launcher 경계. suite 태그 시험(-OUTAGE 는 그 단계 태그까지)을 실행해 OTR 을 그 디렉터리에 쓴다. */
    @FunctionalInterface public interface SuiteRunner { void run(String suite, Path otrDirectory); }

    private final AcceptanceConfig config;
    private final PrintStream out;
    private final SuiteRunner runner;
    private final Path temporary;

    public AcceptanceMain(AcceptanceConfig config, PrintStream out, SuiteRunner runner, Path temporary) {
        this.config = config; this.out = out; this.runner = runner; this.temporary = temporary;
    }

    public static void main(String[] args) {
        // 하네스 로거 기본 OFF: 블록 밖 출력에 원문이 섞이지 않게 한다.
        java.util.logging.LogManager.getLogManager().reset();
        int exit = isolated(System.out, evidence -> {
            var properties = new HashMap<String,String>();
            System.getProperties().stringPropertyNames().stream().filter(name -> name.startsWith("aigw.")).forEach(name -> properties.put(name, System.getProperty(name)));
            var config = new AcceptanceConfig(System.getenv(), properties);
            Path temporary = Path.of(System.getProperty("java.io.tmpdir"));
            if (args.length == 0) return new AcceptanceMain(config, evidence, (suite, otr) -> launch(tags(suite, config.outagePhase()), otr), temporary).runSuite();
            if (args.length == 2 && args[0].equals("--mode") && args[1].equals("rubric-view")) return RubricViewMode.run(config, evidence);
            return 2;
        });
        System.exit(exit);
    }

    /**
     * 본문 실행 동안 System.out/err 를 버리는 스트림으로 바꾸고 증거 스트림만 넘긴다. 예상하지 못한 예외는 원문 없이
     * RUN ABORTED({@code ACCEPTANCE_HARNESS_FAILED}) 1건·exit 1 로 바꾼다. 끝나면 원래 스트림을 되돌린다.
     */
    static int isolated(PrintStream evidence, java.util.function.ToIntFunction<PrintStream> body) {
        PrintStream originalOut = System.out, originalErr = System.err;
        var discard = new PrintStream(java.io.OutputStream.nullOutputStream());
        System.setOut(discard); System.setErr(discard);
        try { return body.applyAsInt(evidence); }
        catch (Throwable unexpected) {
            var context = new EvidenceBlockWriter.Context(null, null, UUID.randomUUID().toString(), null, null, null);
            EvidenceBlockWriter.write(evidence, List.of(EvidenceBlockWriter.record(context, Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", "ACCEPTANCE_HARNESS_FAILED"))));
            return 1;
        } finally { evidence.flush(); System.setOut(originalOut); System.setErr(originalErr); }
    }

    public int runSuite() {
        String suite = config.suite().orElse(null);
        String stage = suite == null ? null : AcceptanceConfig.stageOf(suite).orElseThrow();
        String runId = UUID.randomUUID().toString();
        String parent = config.parentRunId().orElse(null);
        JsonNode identity = config.buildIdentity().orElse(null);
        boolean outage = suite != null && suite.endsWith("-OUTAGE");
        String phase = outage ? config.outagePhase().orElse(null) : null;
        String specSha;
        try { specSha = SpecInputVerifier.verify(config.specGzipFile().orElse(null), config.expectedSpecSha256().orElse(null)); }
        catch (SpecInputVerifier.Failure failure) {
            var context = new EvidenceBlockWriter.Context(stage, suite, runId, parent, null, identity);
            EvidenceBlockWriter.write(out, List.of(phased(EvidenceBlockWriter.record(context, Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", failure.code())), phase)));
            return 3;
        }
        var context = new EvidenceBlockWriter.Context(stage, suite, runId, parent, specSha, identity);
        if (suite == null || parent == null || identity == null || outage && phase == null) {
            EvidenceBlockWriter.write(out, List.of(phased(EvidenceBlockWriter.record(context, Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", "ACCEPTANCE_INPUT_INVALID")), phase)));
            return 2;
        }
        EvidenceBlockWriter.write(out, List.of(phased(EvidenceBlockWriter.record(context, Map.of("kind", "RUN", "status", "STARTED")), phase)));
        Path otr;
        try { otr = Files.createTempDirectory(temporary, "aigw-otr-"); } catch (IOException e) { throw new IllegalStateException("OTR_DIRECTORY"); }
        try {
            F1RubricTargets.clear(); F2RubricTargets.clear(); F2LiveSamples.clear(); AcceptanceMeasurements.clear();
            EvidenceBlockWriter.Extraction extraction;
            try {
                runner.run(suite, otr);
                extraction = EvidenceBlockWriter.extract(otr, context);
            } catch (IllegalStateException invalid) {
                // OTR 결손·무효(중복 finished·미완 결과 등)는 성공으로 덮지 않는다. 사유는 고정 코드만.
                String reason = invalid.getMessage() != null && invalid.getMessage().matches("OTR_[A-Z_]{1,40}") ? invalid.getMessage() : "LAUNCHER_FAILED";
                EvidenceBlockWriter.write(out, List.of(phased(EvidenceBlockWriter.record(context, Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", reason)), phase)));
                return 1;
            }
            if (extraction.executed() == 0) {
                EvidenceBlockWriter.write(out, List.of(phased(EvidenceBlockWriter.record(context, Map.of("kind", "RUN", "status", "ABORTED", "reasonCode", "NO_TESTS_EXECUTED")), phase)));
                return 4;
            }
            var records = new ArrayList<ObjectNode>();
            for (var leaf : extraction.leaves()) records.add(phased(leaf, phase));
            // 측정 시험(F3-PROBE 등)이 모은 정제 값만 MEASUREMENT 레코드로 낸다(leaf·벡터 0).
            for (var measurement : AcceptanceMeasurements.snapshot()) {
                var fields = new LinkedHashMap<String,Object>();
                fields.put("kind", "MEASUREMENT"); fields.put("measurementId", measurement.measurementId()); fields.put("values", measurement.values());
                records.add(phased(EvidenceBlockWriter.record(context, fields), phase));
            }
            var completed = new LinkedHashMap<String,Object>();
            completed.put("kind", "RUN"); completed.put("status", "COMPLETED"); completed.put("executedTestCount", extraction.executed());
            completed.put("values", Map.of("leafRecordCount", extraction.leaves().size(), "passed", extraction.passed(), "failed", extraction.failed(), "skipped", extraction.skipped(), "aborted", extraction.aborted()));
            if (suite.equals("F1")) completed.put("rubricTargets", F1RubricTargets.snapshot());
            if (suite.equals("F2")) completed.put("rubricTargets", F2RubricTargets.snapshot());
            records.add(phased(EvidenceBlockWriter.record(context, completed), phase));
            EvidenceBlockWriter.write(out, records);
            // skip·abort 만 있는 실행도 성공이 아니다(leaf 는 NOT_RUN 으로 남는다).
            return extraction.failed() == 0 && extraction.aborted() == 0 && extraction.skipped() == 0 ? 0 : 1;
        } finally { F1RubricTargets.clear(); F2RubricTargets.clear(); F2LiveSamples.clear(); AcceptanceMeasurements.clear(); delete(otr); }
    }

    /** -OUTAGE 회차 레코드에 단계를 싣고 다시 봉인한다. 단계가 없으면 그대로다. */
    static ObjectNode phased(ObjectNode record, String phase) {
        if (phase == null) return record;
        var copy = record.deepCopy(); copy.put("outagePhase", phase);
        return CanonicalJson.seal(copy);
    }
    /** launcher 태그 식: suite 태그, -OUTAGE 는 그 단계 태그와의 AND. */
    static String tags(String suite, Optional<String> phase) {
        return suite.endsWith("-OUTAGE") ? "suite-" + suite + " & outage-" + phase.orElseThrow(() -> new IllegalStateException("OUTAGE_PHASE_MISSING")) : "suite-" + suite;
    }

    static void launch(String tagExpression, Path otrDirectory) {
        var request = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectPackage(SUITE_PACKAGE))
            .filters(TagFilter.includeTags(tagExpression))
            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
            .configurationParameter("junit.platform.reporting.open.xml.enabled", "true")
            .configurationParameter("junit.platform.reporting.output.dir", otrDirectory.toString())
            .build();
        LauncherFactory.create().execute(request);
    }

    private static void delete(Path directory) {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException e) { throw new IllegalStateException("OTR_CLEANUP_FAILED"); }
    }
}
