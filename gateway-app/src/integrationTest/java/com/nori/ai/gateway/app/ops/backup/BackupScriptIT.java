package com.nori.ai.gateway.app.ops.backup;

import com.nori.ai.gateway.integration.testing.DockerRequiredExtension;
import com.nori.ai.gateway.tcmock.verify.ResidueScanner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/**
 * Plan P15 ⑥ BackupScriptIT(M-6·N15). {@code deploy/docker/Dockerfile-backup} 을 그대로 빌드한 이미지 안에서
 * {@code /opt/aigw/aigw-backup.sh}·{@code aigw-restore.sh} 를 실행한다. 목적지는 같은 유형(s3)의 SeaweedFS 4.35 S3 컨테이너다.
 * 로컬 candidate PG·로컬 시험 도구 pin 결과는 LOCAL_VALIDATED 이며 승인 digest·승인 목적지 재시험을 대체하지 않는다.
 */
@ExtendWith(DockerRequiredExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackupScriptIT {
    static final String SEAWEED_IMAGE = "chrislusf/seaweedfs:4.35@sha256:6f10079ef97a3fc24574725911e138b9f02eb397aeb69e6b0b9dd09b09d0e0bd";
    // 로컬 시험 pin(승인값 아님): rclone 은 공식 SHA256SUMS 와 일치, age 는 공식 release 자산의 로컬 계산값.
    static final Map<String,String> TOOL_PINS = Map.of("AGE_VERSION", "1.2.1", "AGE_SHA256", "7df45a6cc87d4da11cc03a539a7470c15b1041ab2b396af088fe9990f7c79d50",
        "RCLONE_VERSION", "1.68.2", "RCLONE_SHA256", "0e6fa18051e67fc600d803a2dcb10ddedb092247fc6eee61be97f64ec080a13c", "BACKUP_DEST_TYPE", "s3");
    static final String DLG_SENTINEL = "P15DLGSENTINEL" + UUID.randomUUID().toString().replace("-", "");
    static final String DESTINATION = "http://aigw-backup-s3:8333/aigw-backup/verify";
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PDG = "a".repeat(64);
    /** P10 접수 입력과 같은 형태의 비단순 요청 필드(Context·시간 구간·리소스·옵션). */
    static final Map<String,Object> REQUEST_FIELDS = com.nori.ai.gateway.core.job.JobRequestInput.immutableFields(Map.of(
        "context", Map.of("timezone", "Asia/Seoul", "referenceTime", "2026-11-07T09:00:00+09:00",
            "timeWindow", Map.of("from", "2026-11-07T08:00:00+09:00", "to", "2026-11-07T09:00:00+09:00")),
        "resourceRefs", List.of(Map.of("type", "EQP", "ref", "EQP-P15-01")),
        "options", Map.of("allowTools", true, "allowKnowledge", false)));

    final Path root = Path.of(System.getProperty("aigw.rootDir"));
    final String pgImage = System.getProperty("aigw.pgTestImage");
    final Map<String,String> passwords = Map.of("postgres", secret(), "aigw_migrator", secret(), "aigw_app", secret(), "aigw_backup", secret());
    final String s3Key = "it-" + secret().substring(0, 12), s3Secret = secret();
    Network network;
    GenericContainer<?> source, seaweed, runner;
    String recipient, identity, firstBundle;
    final List<GenericContainer<?>> targets = new ArrayList<>();

    @BeforeAll void start() throws Exception {
        assertThat(pgImage).matches("[^\s/@]+(?:/[^\s/@]+)*:[^\s/@:]+@sha256:[0-9a-f]{64}");
        // 이미지 context 로 읽는 바이트 = Gradle 이 시험 지문에 결속한 바이트(aigw.*Sha256).
        for (var bound : Map.of("backupDockerfileSha256", "deploy/docker/Dockerfile-backup", "backupScriptSha256", "ops/backup/aigw-backup.sh",
                "restoreScriptSha256", "ops/backup/aigw-restore.sh", "backupJobColumnsSha256", "ops/backup/job-columns-v1.txt").entrySet())
            assertThat(sha256(Files.readAllBytes(root.resolve(bound.getValue())))).as(bound.getValue()).isEqualTo(System.getProperty("aigw." + bound.getKey()));
        network = Network.newNetwork();
        String approved = System.getProperty("aigw.backupImage", "");
        String backupImage;
        if (!approved.isEmpty()) {
            // CI 가 push 한 승인 digest 이미지를 그대로 쓴다. tag·다른 registry 는 승인 이미지로 받지 않는다.
            assertThat(approved).matches("harbor\\.nori\\.local/nori-tc/ai-gateway-backup@sha256:[0-9a-f]{64}");
            backupImage = approved;
            System.out.println("P15_BACKUP_IMAGE source=approved-digest image=" + approved);
        } else {
            String[] pg = pgImage.split("@sha256:");
            var image = new ImageFromDockerfile("aigw-backup-it-" + UUID.randomUUID().toString().substring(0, 8), true)
                .withDockerfilePath("deploy/docker/Dockerfile-backup")
                .withFileFromPath("deploy/docker/Dockerfile-backup", root.resolve("deploy/docker/Dockerfile-backup"));
            for (String file : List.of("aigw-backup.sh", "aigw-restore.sh", "job-columns-v1.txt"))
                image.withFileFromPath("ops/backup/" + file, root.resolve("ops/backup/" + file));
            image.withBuildArg("PG_IMAGE_REPOSITORY", pg[0].substring(0, pg[0].lastIndexOf(':'))).withBuildArg("PG_IMAGE_DIGEST", pg[1]);
            TOOL_PINS.forEach(image::withBuildArg);
            backupImage = image.get();
            System.out.println("P15_BACKUP_IMAGE source=deploy/docker/Dockerfile-backup local-build pg=" + pgImage + " pins=" + new TreeMap<>(TOOL_PINS));
        }
        source = postgres("ai-gateway-db", 5432);
        org.flywaydb.core.Flyway.configure().dataSource(jdbc(source, 5432), "aigw_migrator", passwords.get("aigw_migrator"))
            .defaultSchema("gw_flyway").createSchemas(true).locations("classpath:db/migration/gateway").load().migrate();
        seaweed = new GenericContainer<>(SEAWEED_IMAGE).withNetwork(network).withNetworkAliases("aigw-backup-s3")
            .withCommand("server", "-s3", "-s3.port=8333", "-dir=/data").withExposedPorts(8333)
            .waitingFor(Wait.forListeningPorts(8333).withStartupTimeout(Duration.ofMinutes(2)));
        seaweed.start();
        keys();
        runner = new GenericContainer<>(backupImage).withNetwork(network).withCommand("sleep", "infinity")
            .withEnv(Map.of("PGHOST", "ai-gateway-db", "PGPORT", "5432", "PGUSER", "aigw_backup", "PGDATABASE", "ai_gateway", "PGPASSWORD", passwords.get("aigw_backup"),
                "AIGW_BACKUP_DESTINATION", DESTINATION, "AIGW_BACKUP_AGE_RECIPIENT", recipient, "AIGW_BACKUP_RETENTION_DAYS", "7",
                "AIGW_BACKUP_CREDENTIALS_DIR", "/run/aigw/backup-destination", "AIGW_RESTORE_PGPASSWORD", passwords.get("aigw_migrator")))
            .withTmpFs(Map.of("/work/plain", "rw,mode=0700,uid=999,gid=999,size=64m", "/work/enc", "rw,mode=0700,uid=999,gid=999"))
            .withCopyToContainer(Transferable.of(s3Key.getBytes(StandardCharsets.UTF_8), 0444), "/run/aigw/backup-destination/access-key")
            .withCopyToContainer(Transferable.of(s3Secret.getBytes(StandardCharsets.UTF_8), 0444), "/run/aigw/backup-destination/secret-key")
            .withCopyToContainer(Transferable.of((identity + "\n").getBytes(StandardCharsets.UTF_8), 0444), "/run/age/identity");
        runner.start();
        assertThat(exec(runner, "id", "-u").getStdout().strip()).isEqualTo("999");
        assertThat(exec(runner, "printenv", "AIGW_BACKUP_DEST_TYPE").getStdout().strip())
            .as("이 시험의 목적지는 s3 유형(SeaweedFS)뿐이다. sftp 승인 이미지는 OpenSSH 시험 서버가 필요하며 여기서 다루지 않는다").isEqualTo("s3");
        assertThat(s3("mkdir", "aigwdest:aigw-backup").getExitCode()).isZero();
        seed();
    }
    @AfterAll void stop() {
        for (var container : targets) container.stop();
        for (var container : Arrays.asList(runner, seaweed, source)) if (container != null) container.stop();
        if (network != null) network.close();
    }

    @Test @Order(1) void backupUnderConcurrentWritesRestoresIntoIsolatedTarget() throws Exception {
        long sequenceBefore = scalar(source, "aigw_app", "SELECT last_value FROM gw.job_seq_seq");
        long jobsBefore = scalar(source, "aigw_app", "SELECT count(*) FROM gw.job");
        // 원본의 독립 기대값: 표별 행 내용 지문(job 은 delegation_id 제외)·행 수. audit_event 는 동시 쓰기 대상이라 아래에서 따로 본다.
        Map<String,String> sourceDigests = tableDigests(source, 5432);
        Map<String,Long> sourceCounts = tableCounts(source, 5432);
        long auditBefore = sourceCounts.get("gw.audit_event");
        var writing = new AtomicBoolean(true); var committed = new AtomicInteger(); var failure = new AtomicReference<Throwable>();
        var writer = new Thread(() -> {
            try (var connection = DriverManager.getConnection(jdbc(source, 5432), "aigw_app", passwords.get("aigw_app"))) {
                while (writing.get()) {
                    try (var statement = connection.prepareStatement("INSERT INTO gw.audit_event(recorded_at, owner_system, actor_ref, request_id, event_type) VALUES (now(), 'TC', 'writer', ?, 'P15_BACKUP_PROBE')")) {
                        statement.setString(1, "req-" + (committed.get() + 1));
                        // autocommit: executeUpdate 가 1 을 돌려준 뒤에만 commit 된 쓰기로 센다.
                        if (statement.executeUpdate() != 1) throw new IllegalStateException("INSERT 행 수");
                        committed.incrementAndGet();
                    }
                    Thread.sleep(20);
                }
            } catch (Throwable error) { failure.set(error); }
        });
        writer.start();
        Container.ExecResult backup;
        int committedAtStart, committedAtEnd;
        try {
            committedAtStart = committed.get();
            backup = exec(runner, "/opt/aigw/aigw-backup.sh");
            committedAtEnd = committed.get();
        } finally {
            writing.set(false);
            writer.join(Duration.ofSeconds(30));
            assertThat(writer.isAlive()).as("writer 종료").isFalse();
        }
        assertThat(failure.get()).as("동시 쓰기 실패").isNull();
        assertThat(committedAtEnd - committedAtStart).as("백업 실행 중 commit 된 동시 쓰기").isPositive();
        assertThat(backup.getExitCode()).as("backup stderr=%s", backup.getStderr()).isZero();
        String bundle = backup.getStdout().strip().replaceFirst("^BACKUP_SUCCESS bundle=", "");
        assertThat(bundle).matches("aigw-[0-9]{8}T[0-9]{6}Z\\.bundle");
        firstBundle = bundle;
        assertThat(latestOutcome()).isEqualTo("SUCCESS");
        assertThat(exec(runner, "find", "/work", "-mindepth", "2").getStdout()).isBlank();
        assertThat(s3("lsf", "aigwdest:aigw-backup/verify").getStdout()).contains(bundle);

        // 복호 스트림 전체에 dlg sentinel 이 없다(ResidueScanner 규칙).
        assertThat(s3("copyto", "aigwdest:aigw-backup/verify/" + bundle, "/tmp/restore/" + bundle).getExitCode()).isZero();
        var decrypted = exec(runner, "sh", "-c", "set -e; d=$(mktemp -d); tar -xf /tmp/restore/" + bundle + " -C $d; age -d -i /run/age/identity $d/manifest.json.age;"
            + " age -d -i /run/age/identity $d/main.dump.age | pg_restore -f -; age -d -i /run/age/identity $d/job.copy.age; rm -rf $d");
        assertThat(decrypted.getExitCode()).isZero();
        assertThat(decrypted.getStdout()).contains("\"delegationExcluded\":true", "CREATE TABLE gw.job");
        assertThat(ResidueScanner.scan(Map.of("bundle", decrypted.getStdout()), Set.of(DLG_SENTINEL)).matchCount()).isZero();

        // N15: bootstrap 만 된 빈 대상에 aigw_migrator 로 복원.
        var target = postgres("ai-gateway-db-restore", 15432);
        var restore = restore("/tmp/restore/" + bundle, "/tmp/receipt-1");
        assertThat(restore.getExitCode()).as("restore stderr=%s", restore.getStderr()).isZero();
        String receiptText = exec(runner, "cat", "/tmp/receipt-1/restore-receipt.json").getStdout();
        JsonNode receipt = JSON.readTree(receiptText);
        for (String secret : passwords.values()) assertThat(receiptText).doesNotContain(secret);
        assertThat(receiptText).doesNotContain(identity, DLG_SENTINEL, s3Secret);
        assertThat(receipt.path("jobRowCount").asLong()).isEqualTo(jobsBefore);
        var excluded = new ArrayList<String>();
        receipt.path("tocExcluded").forEach(node -> excluded.add(node.asString()));
        System.out.println("P15_TOC_EXCLUDED " + excluded);
        // 제외 항목은 bootstrap 소유 항목뿐(확장 vector·public 스키마·pg_control_system() ACL).
        for (String entry : excluded)
            assertThat(entry).matches("(EXTENSION - vector|COMMENT - EXTENSION vector|ACL - SCHEMA public|COMMENT - SCHEMA public|ACL pg_catalog FUNCTION pg_control_system\\(\\)) .*");
        assertThat(excluded).anyMatch(entry -> entry.startsWith("EXTENSION - vector "));

        // 원본 독립 기대값과 대조: 정적 표는 행 내용 지문·수 일치, audit_event 는 snapshot 일관성(commit 순서의 접두사).
        Map<String,String> restoredDigests = tableDigests(target, 15432);
        Map<String,Long> restoredCounts = tableCounts(target, 15432);
        assertThat(restoredCounts.keySet()).isEqualTo(sourceCounts.keySet());
        for (String table : sourceDigests.keySet()) {
            if (table.equals("gw.audit_event")) continue;
            assertThat(restoredDigests.get(table)).as(table).isEqualTo(sourceDigests.get(table));
            assertThat(restoredCounts.get(table)).as(table).isEqualTo(sourceCounts.get(table));
            assertThat(receipt.path("tableRowCounts").path(table).asLong(-1)).as("receipt " + table).isEqualTo(sourceCounts.get(table));
        }
        long probes = scalar(target, 15432, "aigw_migrator", "SELECT count(*) FROM gw.audit_event WHERE event_type = 'P15_BACKUP_PROBE'");
        assertThat(restoredCounts.get("gw.audit_event")).isEqualTo(auditBefore + probes);
        assertThat(probes).isBetween((long) committedAtStart, (long) committedAtEnd);
        assertThat(scalar(target, 15432, "aigw_migrator", "SELECT count(*) FROM gw.audit_event WHERE event_type = 'P15_BACKUP_PROBE' AND request_id NOT IN"
            + " (SELECT 'req-' || g FROM generate_series(1, " + probes + ") g)")).as("snapshot 은 commit 순서의 접두사").isZero();
        assertThat(scalar(target, 15432, "aigw_migrator", "SELECT count(*) FROM gw.job WHERE delegation_id IS NOT NULL")).isZero();
        assertThat(scalar(target, 15432, "aigw_migrator", "SELECT count(*) FROM gw.job j LEFT JOIN gw.message m ON m.id = j.request_message_id AND m.conversation_id = j.conversation_id"
            + " WHERE m.id IS NULL")).as("Job → 요청 Message 연관").isZero();
        // 덤프의 SEQUENCE SET 이 원래 counter 를 복원한다(setval 보정 0).
        assertThat(scalar(target, 15432, "aigw_migrator", "SELECT last_value FROM gw.job_seq_seq")).isEqualTo(sequenceBefore);
        // P10 요청 입력은 실제 reader(JobRepository.findRequestInput, owner/actor scope)로 읽어 원본과 같아야 한다.
        for (String job : List.of("job-done", "job-running", "job-queued")) {
            var original = requestInput(source, 5432, job);
            var restored = requestInput(target, 15432, job);
            assertThat(restored.messageId()).isEqualTo(original.messageId());
            assertThat(restored.maskedQuestion()).isEqualTo(original.maskedQuestion());
            assertThat(restored.requestFields()).isEqualTo(original.requestFields()).isEqualTo(REQUEST_FIELDS);
            assertThat(restored.selectedResourceRefs()).hasSize(1);
        }
        try (var connection = DriverManager.getConnection(jdbc(target, 15432), "aigw_app", passwords.get("aigw_app"))) {
            connection.setAutoCommit(false);
            insertJob(connection, "job-after-restore", "SUCCEEDED", null);
            connection.commit();
        }
        assertThat(scalar(target, 15432, "aigw_migrator", "SELECT seq FROM gw.job WHERE id = 'job-after-restore'")).isGreaterThan(sequenceBefore);

        // 비어 있지 않은 DB 에는 쓰지 않는다.
        Map<String,String> beforeReject = tableDigests(target, 15432);
        var again = restore("/tmp/restore/" + bundle, "/tmp/receipt-2");
        assertThat(again.getExitCode()).isEqualTo(7);
        assertThat(again.getStderr()).contains("RESTORE_TARGET_REJECTED:not_empty");
        assertThat(tableDigests(target, 15432)).as("거부 뒤 쓰기 0").isEqualTo(beforeReject);
        assertThat(exec(runner, "test", "-e", "/tmp/receipt-2/restore-receipt.json").getExitCode()).isNotZero();
    }

    @Test @Order(2) void failedDestinationRecordsFailedAndUnknownOverrideIsRejected() throws Exception {
        var metrics = new BackupStatusMetrics(JdbcClient.create(new DriverManagerDataSource(jdbc(source, 5432), "aigw_app", passwords.get("aigw_app"))), new SimpleMeterRegistry());
        long rows = scalar(source, "aigw_backup", "SELECT count(*) FROM gw.backup_run");
        var probe = exec(runner, "sh", "-c", "AIGW_BACKUP_DEST_OVERRIDE=unreachable-probe exec /opt/aigw/aigw-backup.sh");
        assertThat(probe.getExitCode()).isNotZero();
        assertThat(probe.getStderr()).contains("BACKUP_FAILED:UPLOAD");
        assertThat(latestOutcome()).isEqualTo("FAILED");
        assertThat(scalar(source, "aigw_backup", "SELECT count(*) FROM gw.backup_run")).isEqualTo(rows + 1);
        assertThat(exec(runner, "find", "/work", "-mindepth", "2").getStdout()).isBlank();
        assertThat(metrics.lastRunFailed()).isEqualTo(1.0);
        assertThat(metrics.lastSuccessEpochSeconds()).isPositive();
        var invalid = exec(runner, "sh", "-c", "AIGW_BACKUP_DEST_OVERRIDE=s3://elsewhere exec /opt/aigw/aigw-backup.sh");
        assertThat(invalid.getExitCode()).isEqualTo(2);
        assertThat(scalar(source, "aigw_backup", "SELECT count(*) FROM gw.backup_run")).isEqualTo(rows + 1);
        var recovered = exec(runner, "/opt/aigw/aigw-backup.sh");
        assertThat(recovered.getExitCode()).isZero();
        assertThat(metrics.lastRunFailed()).isZero();
    }

    @Test @Order(3) void corruptedTocEntryStopsRestoreAtThatStep() throws Exception {
        // 대상에 없는 역할의 ACL 을 가진 항목 1개를 넣은 묶음은 그 TOC 항목에서 즉시 중단해야 한다.
        exec(source, "psql", "-X", "-w", "-U", "postgres", "-d", "ai_gateway", "-v", "ON_ERROR_STOP=1", "-c",
            "CREATE ROLE p15_toc_probe_absent NOLOGIN; CREATE TABLE gw.p15_toc_probe(id int); GRANT SELECT ON gw.p15_toc_probe TO aigw_backup, p15_toc_probe_absent;");
        var backup = exec(runner, "/opt/aigw/aigw-backup.sh");
        assertThat(backup.getExitCode()).as("backup stderr=%s", backup.getStderr()).isZero();
        String bundle = backup.getStdout().strip().replaceFirst("^BACKUP_SUCCESS bundle=", "");
        assertThat(s3("copyto", "aigwdest:aigw-backup/verify/" + bundle, "/tmp/restore/" + bundle).getExitCode()).isZero();
        var target = postgres("ai-gateway-db-restore-2", 15432);
        exec(runner, "mkdir", "-p", "/tmp/receipt-3");
        var restore = exec(runner, "/opt/aigw/aigw-restore.sh", "--bundle", "/tmp/restore/" + bundle, "--target-db-url", "postgresql://aigw_migrator@ai-gateway-db-restore-2:15432/ai_gateway",
            "--expect-db-release", "ai-gateway-db-restore", "--age-identity-file", "/run/age/identity", "--receipt-dir", "/tmp/receipt-3");
        assertThat(restore.getExitCode()).as("restore stderr=%s", restore.getStderr()).isEqualTo(8);
        assertThat(restore.getStderr()).startsWith("RESTORE_STEP_FAILED:");
        String failedStep = restore.getStderr().strip().substring("RESTORE_STEP_FAILED:".length());
        System.out.println("P15_TOC_PROBE failedStep=" + failedStep);
        assertThat(exec(runner, "test", "-e", "/tmp/receipt-3/restore-receipt.json").getExitCode()).isNotZero();
        // 실패 단계 이후 단계는 실행하지 않는다: pre-data 에서 멈췄다면 Job 데이터 적재도 없다.
        if (failedStep.equals("pre-data")) assertThat(scalar(target, 15432, "aigw_migrator", "SELECT count(*) FROM gw.job")).isZero();
    }

    @Test @Order(4) void malformedBundlesAreRejectedBeforeTargetAccess() throws Exception {
        // order(1) 의 실제 bundle 에서 변형을 만든다. 대상 검사(exit 7)보다 먼저 BUNDLE 단계(exit 8)에서 거부돼야 한다.
        assertThat(firstBundle).isNotNull();
        String prepare = String.join("\n",
            "set -eu; umask 077; src=/tmp/restore/" + firstBundle + "; bad=/tmp/bad; rm -rf $bad; mkdir -p $bad/w $bad/l; cd $bad/w; tar -xf $src",
            "tar -cf $bad/extra.bundle manifest.json.age main.dump.age job.copy.age; printf x > extra.txt; tar -rf $bad/extra.bundle extra.txt",
            "tar -cf $bad/duplicate.bundle manifest.json.age main.dump.age job.copy.age manifest.json.age",
            "tar -cf $bad/reordered.bundle main.dump.age manifest.json.age job.copy.age",
            "cp manifest.json.age job.copy.age $bad/l/; ln -s $bad/w/main.dump.age $bad/l/main.dump.age; (cd $bad/l && tar -cf $bad/link.bundle manifest.json.age main.dump.age job.copy.age)",
            "m=$(age -d -i /run/age/identity manifest.json.age)",
            "printf '%s' \"$m\" | sed 's/}}$/},\"rawNote\":\"x\"}/' | age -r \"$AIGW_BACKUP_AGE_RECIPIENT\" -o manifest.json.age; tar -cf $bad/unknown-field.bundle manifest.json.age main.dump.age job.copy.age",
            "printf '%s' \"$m\" | sed 's/^{/{\"jobRowCount\":0,/' | age -r \"$AIGW_BACKUP_AGE_RECIPIENT\" -o manifest.json.age; tar -cf $bad/duplicate-key.bundle manifest.json.age main.dump.age job.copy.age",
            "printf '%s' \"$m\" | sed 's/\"delegationExcluded\":true/\"delegationExcluded\":\"true\"/' | age -r \"$AIGW_BACKUP_AGE_RECIPIENT\" -o manifest.json.age; tar -cf $bad/wrong-type.bundle manifest.json.age main.dump.age job.copy.age",
            "rm -rf $bad/w $bad/l");
        var prepared = exec(runner, "bash", "-c", prepare);
        assertThat(prepared.getExitCode()).as("prepare stderr=%s", prepared.getStderr()).isZero();
        var target = targets.getFirst();
        Map<String,String> before = tableDigests(target, 15432);
        String temporaryBefore = exec(runner, "sh", "-c", "ls -d /tmp/tmp.* 2>/dev/null | sort").getStdout();
        for (String name : List.of("extra", "duplicate", "reordered", "link", "unknown-field", "duplicate-key", "wrong-type")) {
            var result = restore("/tmp/bad/" + name + ".bundle", "/tmp/receipt-bad-" + name);
            assertThat(result.getExitCode()).as(name + " stderr=%s", result.getStderr()).isEqualTo(8);
            assertThat(result.getStderr().strip()).as(name).isEqualTo("RESTORE_STEP_FAILED:BUNDLE");
            assertThat(exec(runner, "test", "-e", "/tmp/receipt-bad-" + name + "/restore-receipt.json").getExitCode()).as(name).isNotZero();
        }
        assertThat(tableDigests(target, 15432)).as("거부된 묶음은 대상에 쓰지 않는다").isEqualTo(before);
        assertThat(exec(runner, "sh", "-c", "ls -d /tmp/tmp.* 2>/dev/null | sort").getStdout()).as("복원 작업 디렉터리 잔류 0").isEqualTo(temporaryBefore);
        exec(runner, "rm", "-rf", "/tmp/bad");
    }

    private Container.ExecResult restore(String bundle, String receiptDirectory) throws Exception {
        exec(runner, "mkdir", "-p", receiptDirectory);
        return exec(runner, "/opt/aigw/aigw-restore.sh", "--bundle", bundle, "--target-db-url", "postgresql://aigw_migrator@ai-gateway-db-restore:15432/ai_gateway",
            "--expect-db-release", "ai-gateway-db-restore", "--age-identity-file", "/run/age/identity", "--receipt-dir", receiptDirectory);
    }

    private GenericContainer<?> postgres(String alias, int port) throws Exception {
        var container = new GenericContainer<>(pgImage).withNetwork(network).withNetworkAliases(alias)
            .withEnv(Map.of("POSTGRES_DB", "ai_gateway", "POSTGRES_USER", "postgres", "POSTGRES_PASSWORD", passwords.get("postgres"), "PGPORT", String.valueOf(port),
                "PGDATA", "/var/lib/postgresql/18/docker", "AIGW_MIGRATOR_PASSWORD", passwords.get("aigw_migrator"), "AIGW_APP_PASSWORD", passwords.get("aigw_app"),
                "AIGW_BACKUP_PASSWORD", passwords.get("aigw_backup")))
            .withCopyFileToContainer(MountableFile.forHostPath(root.resolve("ops/postgres/acceptance-pg_hba.conf")), "/etc/aigw/postgres/pg_hba.conf")
            .withCopyFileToContainer(MountableFile.forHostPath(root.resolve("ops/postgres/bootstrap.sql")), "/tmp/p15-bootstrap.sql")
            .withCommand("postgres", "-c", "hba_file=/etc/aigw/postgres/pg_hba.conf", "-c", "port=" + port).withExposedPorts(port)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2).withStartupTimeout(Duration.ofMinutes(3)));
        container.start();
        if (!alias.equals("ai-gateway-db")) targets.add(container);
        var bootstrap = exec(container, "psql", "-X", "-w", "-U", "postgres", "-d", "ai_gateway", "-v", "ON_ERROR_STOP=1", "-v", "acceptance_role_enabled=false", "-f", "/tmp/p15-bootstrap.sql");
        assertThat(bootstrap.getExitCode()).as("bootstrap").isZero();
        return container;
    }
    private void seed() throws Exception {
        try (var connection = DriverManager.getConnection(jdbc(source, 5432), "aigw_app", passwords.get("aigw_app"))) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO gw.conversation(id, owner_system, owner_actor_ref, created_at, updated_at, expires_at, status) VALUES ('conv-1','TC','actor-1',now(),now(),now() + interval '30 days','ACTIVE')");
                for (String message : List.of("msg-1", "msg-2", "msg-3"))
                    statement.executeUpdate("INSERT INTO gw.message(id, conversation_id, owner_system, role, created_at, content_masked, resource_refs, tool_execution_refs) VALUES ('" + message + "','conv-1','TC','user',now(),'P15 백업 왕복 질문','[]','[]')");
            }
            insertJob(connection, "job-done", "SUCCEEDED", null);
            insertJob(connection, "job-running", "RUNNING", DLG_SENTINEL);
            insertJob(connection, "job-queued", "QUEUED", DLG_SENTINEL + "Q");
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO gw.result(job_id, owner_system, status, payload, result_source_deps, created_at) VALUES ('job-done','TC','SUCCEEDED','{\"blocks\":[]}','[]',now())");
                statement.executeUpdate("INSERT INTO gw.job_event(job_id, owner_system, type, payload, created_at) VALUES ('job-done','TC','TERMINAL','{}',now())");
            }
            connection.commit();
        }
    }
    private void insertJob(Connection connection, String id, String status, String delegation) throws SQLException {
        String message = switch (id) { case "job-done" -> "msg-1"; case "job-running" -> "msg-2"; case "job-queued" -> "msg-3"; default -> "msg-1"; };
        try (var statement = connection.prepareStatement("INSERT INTO gw.job(id, conversation_id, owner_system, actor_ref, status, created_at, terminal_at, request_digest, expected_path,"
                + " delegation_id, request_perm, request_pdg, request_message_id, request_input) VALUES (?, 'conv-1', 'TC', 'actor-1', ?, now(), ?, 'digest', 'GENERAL_QUERY', ?, ARRAY['EQP_READ'], ?, ?, CAST(? AS jsonb))")) {
            statement.setString(1, id); statement.setString(2, status);
            statement.setTimestamp(3, Set.of("QUEUED", "RUNNING").contains(status) ? null : java.sql.Timestamp.from(java.time.Instant.now()));
            statement.setString(4, delegation); statement.setString(5, PDG); statement.setString(6, message);
            statement.setString(7, JSON.writeValueAsString(REQUEST_FIELDS));
            statement.executeUpdate();
        }
    }
    /** 표별 행 내용 지문(정렬된 JSON 행의 md5, job 은 delegation_id 제외). 식별자는 카탈로그에서만 얻는다. */
    private Map<String,String> tableDigests(GenericContainer<?> container, int port) throws SQLException {
        return tableQuery(container, port, "SELECT coalesce(md5(string_agg(x, E'\\n' ORDER BY x)), 'empty') FROM (SELECT (to_jsonb(t) - 'delegation_id')::text AS x FROM %s t) rows");
    }
    private Map<String,Long> tableCounts(GenericContainer<?> container, int port) throws SQLException {
        var counts = new TreeMap<String,Long>();
        tableQuery(container, port, "SELECT count(*)::text FROM %s").forEach((table, value) -> counts.put(table, Long.parseLong(value)));
        return counts;
    }
    private Map<String,String> tableQuery(GenericContainer<?> container, int port, String template) throws SQLException {
        var result = new TreeMap<String,String>();
        try (var connection = DriverManager.getConnection(jdbc(container, port), "aigw_migrator", passwords.get("aigw_migrator"))) {
            var tables = new ArrayList<String>();
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT schemaname, tablename FROM pg_tables WHERE schemaname IN ('gw','kn','gw_flyway') ORDER BY 1, 2")) {
                while (rows.next()) tables.add(rows.getString(1) + "." + rows.getString(2));
            }
            for (String table : tables) {
                String[] name = table.split("\\.");
                try (var statement = connection.createStatement(); var rows = statement.executeQuery(String.format(template, "\"" + name[0] + "\".\"" + name[1] + "\""))) {
                    assertThat(rows.next()).isTrue(); result.put(table, rows.getString(1));
                }
            }
        }
        return result;
    }
    /** 실제 reader 경로: 저장된 Job scope(ScopeAssembler.forWorker) + JobRepository.findRequestInput. */
    private com.nori.ai.gateway.core.job.JobRequestInput requestInput(GenericContainer<?> container, int port, String job) {
        var data = new DriverManagerDataSource(jdbc(container, port), "aigw_app", passwords.get("aigw_app"));
        var client = JdbcClient.create(data);
        var persisted = client.sql("SELECT j.owner_system,j.actor_ref,j.request_perm,j.request_pdg,j.id,j.created_at,j.conversation_id,c.expires_at,j.status FROM gw.job j"
                + " JOIN gw.conversation c ON c.id=j.conversation_id AND c.owner_system=j.owner_system WHERE j.id=:id").param("id", job)
            .query((rs, n) -> new com.nori.ai.gateway.persistence.queue.JobScopeRecord(com.nori.ai.gateway.core.scope.OwnerSystem.valueOf(rs.getString(1)), rs.getString(2),
                Set.copyOf(Arrays.asList((String[]) rs.getArray(3).getArray())), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant(), rs.getString(7),
                rs.getTimestamp(8).toInstant(), rs.getString(9))).single();
        var scope = new com.nori.ai.gateway.app.security.ScopeAssembler().forWorker(persisted);
        var transactions = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.support.JdbcTransactionManager(data));
        return new com.nori.ai.gateway.persistence.repository.JobRepository(client, transactions, java.time.Clock.fixed(persisted.createdAt().plusSeconds(1), java.time.ZoneOffset.UTC))
            .findRequestInput(scope, job).orElseThrow();
    }
    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private String latestOutcome() throws SQLException {
        try (var connection = DriverManager.getConnection(jdbc(source, 5432), "aigw_app", passwords.get("aigw_app"));
             var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT outcome FROM gw.backup_run ORDER BY id DESC LIMIT 1")) {
            return rows.next() ? rows.getString(1) : null;
        }
    }
    private long scalar(GenericContainer<?> container, String role, String sql) throws SQLException { return scalar(container, 5432, role, sql); }
    private long scalar(GenericContainer<?> container, int port, String role, String sql) throws SQLException {
        try (var connection = DriverManager.getConnection(jdbc(container, port), role, passwords.get(role)); var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue(); return rows.getLong(1);
        }
    }
    private String jdbc(GenericContainer<?> container, int port) { return "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(port) + "/ai_gateway"; }
    private Container.ExecResult s3(String... arguments) throws Exception {
        var command = new StringBuilder("export RCLONE_CONFIG_AIGWDEST_TYPE=s3 RCLONE_CONFIG_AIGWDEST_PROVIDER=Other RCLONE_CONFIG_AIGWDEST_ENDPOINT=http://aigw-backup-s3:8333"
            + " RCLONE_CONFIG_AIGWDEST_FORCE_PATH_STYLE=true RCLONE_CONFIG_AIGWDEST_ACCESS_KEY_ID=$(cat /run/aigw/backup-destination/access-key)"
            + " RCLONE_CONFIG_AIGWDEST_SECRET_ACCESS_KEY=$(cat /run/aigw/backup-destination/secret-key); exec rclone");
        for (String argument : arguments) command.append(' ').append(argument);
        return exec(runner, "sh", "-c", command.toString());
    }
    private static Container.ExecResult exec(GenericContainer<?> container, String... command) throws Exception { return container.execInContainer(command); }

    /** age X25519 키 한 쌍(bech32): 수신자 {@code age1…}, 개인키 {@code AGE-SECRET-KEY-1…}. 시험 프로세스 메모리에서만 만든다. */
    private void keys() throws GeneralSecurityException {
        KeyPair pair = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        byte[] scalar = ((XECPrivateKey) pair.getPrivate()).getScalar().orElseThrow();
        byte[] big = ((XECPublicKey) pair.getPublic()).getU().toByteArray(), publicKey = new byte[32];
        for (int i = 0; i < 32 && i < big.length; i++) publicKey[i] = big[big.length - 1 - i];
        recipient = bech32("age", publicKey);
        identity = bech32("age-secret-key-", scalar).toUpperCase(Locale.ROOT);
    }
    private static String bech32(String hrp, byte[] data) {
        String charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
        var values = new ArrayList<Integer>();
        int accumulator = 0, bits = 0;
        for (byte value : data) { accumulator = (accumulator << 8) | (value & 0xff); bits += 8; while (bits >= 5) { bits -= 5; values.add((accumulator >> bits) & 31); } }
        if (bits > 0) values.add((accumulator << (5 - bits)) & 31);
        var checksumInput = new ArrayList<Integer>();
        for (char c : hrp.toCharArray()) checksumInput.add(c >> 5);
        checksumInput.add(0);
        for (char c : hrp.toCharArray()) checksumInput.add(c & 31);
        checksumInput.addAll(values);
        for (int i = 0; i < 6; i++) checksumInput.add(0);
        int polymod = polymod(checksumInput) ^ 1;
        var out = new StringBuilder(hrp).append('1');
        for (int value : values) out.append(charset.charAt(value));
        for (int i = 0; i < 6; i++) out.append(charset.charAt((polymod >> (5 * (5 - i))) & 31));
        return out.toString();
    }
    private static int polymod(List<Integer> values) {
        int[] generator = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};
        int checksum = 1;
        for (int value : values) {
            int top = checksum >>> 25;
            checksum = ((checksum & 0x1ffffff) << 5) ^ value;
            for (int i = 0; i < 5; i++) if (((top >>> i) & 1) != 0) checksum ^= generator[i];
        }
        return checksum;
    }
    private static String secret() {
        byte[] bytes = new byte[24]; new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
