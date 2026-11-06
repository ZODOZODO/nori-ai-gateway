package com.nori.ai.gateway.persistence.testing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.BindMode;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** 정본 bootstrap/HBA를 공유하며 자격과 격리 컨테이너의 수명을 소유합니다. */
public final class PgTestDatabase implements AutoCloseable {
    private static final String OWNER_LABEL = "aigw.p07.owner";
    private final String owner = UUID.randomUUID().toString();
    private final Path root;
    private final String image;
    private final Map<String, String> passwords = Map.of("postgres", password(), "aigw_migrator", password(),
        "aigw_app", password(), "aigw_backup", password(), "aigw_acceptance_ro", password());
    private PostgreSQLContainer container;

    public PgTestDatabase(String image, Path root) {
        if (image == null || !image.matches("[^\\s/@]+(?:/[^\\s/@]+)*:[^\\s/@:]+@sha256:[0-9a-f]{64}"))
            throw new IllegalArgumentException("고정된 PostgreSQL 시험 이미지가 필요합니다");
        this.image = image; this.root = root.toAbsolutePath().normalize();
        verifySource("ops/postgres/bootstrap.sql", "aigw.pgBootstrapSha256");
        verifySource("ops/postgres/acceptance-pg_hba.conf", "aigw.pgHbaSha256");
    }
    private void verifySource(String file, String property) {
        try {
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(file))));
            if (!actual.equals(System.getProperty(property))) throw new IllegalStateException("PostgreSQL 정본 입력 지문이 일치하지 않습니다");
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("PostgreSQL 정본 입력을 읽을 수 없습니다", failure);
        }
    }
    public PgTestDatabase start() {
        try {
            container = new PostgreSQLContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("ai_gateway").withUsername("postgres").withPassword(passwords.get("postgres"))
                .withLabel(OWNER_LABEL, owner).withStartupTimeout(java.time.Duration.ofMinutes(3)).withEnv("PGDATA", "/var/lib/postgresql/18/docker")
                .withEnv("AIGW_MIGRATOR_PASSWORD", passwords.get("aigw_migrator"))
                .withEnv("AIGW_APP_PASSWORD", passwords.get("aigw_app"))
                .withEnv("AIGW_BACKUP_PASSWORD", passwords.get("aigw_backup"))
                .withEnv("AIGW_ACCEPTANCE_PASSWORD", passwords.get("aigw_acceptance_ro"))
                .withFileSystemBind(root.resolve("ops/postgres/acceptance-pg_hba.conf").toString(),
                    "/etc/aigw/postgres/pg_hba.conf", BindMode.READ_ONLY)
                .withCopyFileToContainer(MountableFile.forHostPath(root.resolve("ops/postgres/bootstrap.sql")), "/tmp/p07-bootstrap.sql")
                .withCommand("postgres", "-c", "hba_file=/etc/aigw/postgres/pg_hba.conf");
            container.start();
            try (var connection = connection("postgres"); var statement = connection.createStatement();
                    var result = statement.executeQuery("SHOW server_version")) {
                if (!result.next() || !result.getString(1).startsWith("18.6")) throw new IllegalStateException("PostgreSQL 시험 버전이 일치하지 않습니다");
                System.out.println("P07_PG image=" + image + " version=" + result.getString(1));
            }
            return this;
        } catch (Exception failure) { close(); throw new IllegalStateException("격리 PostgreSQL 준비에 실패했습니다", failure); }
    }
    public void bootstrap(boolean acceptanceRoleEnabled) {
        try {
            var result = container.execInContainer("psql", "-X", "-w", "-U", "postgres", "-d", "ai_gateway", "-v", "ON_ERROR_STOP=1",
                "-v", "acceptance_role_enabled=" + acceptanceRoleEnabled, "-f", "/tmp/p07-bootstrap.sql");
            if (result.getExitCode() != 0) throw new IllegalStateException("정본 PostgreSQL bootstrap이 실패했습니다");
            try (var connection = connection("postgres"); var statement = connection.createStatement();
                    var version = statement.executeQuery("SELECT extversion FROM pg_extension WHERE extname = 'vector'")) {
                if (!version.next() || !"0.8.7".equals(version.getString(1))) throw new IllegalStateException("vector 시험 버전이 일치하지 않습니다");
                System.out.println("P07_VECTOR version=" + version.getString(1));
            }
        } catch (java.io.IOException | InterruptedException | SQLException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("정본 PostgreSQL bootstrap을 실행할 수 없습니다", failure);
        }
    }
    public void migrate() {
        Flyway.configure().dataSource(jdbcUrl(), "aigw_migrator", passwordFor("aigw_migrator"))
            .defaultSchema("gw_flyway").createSchemas(true).locations("classpath:db/migration/gateway").load().migrate();
    }
    public Connection connection(String role) throws SQLException { return DriverManager.getConnection(jdbcUrl(), role, passwordFor(role)); }
    public String jdbcUrl() { return container.getJdbcUrl(); }
    public String passwordFor(String role) {
        String value = passwords.get(role);
        if (value == null) throw new IllegalArgumentException("시험 역할이 잘못되었습니다");
        return value;
    }
    public boolean rejectsOtherDatabase(String database, boolean unix) {
        if (!java.util.Set.of("postgres", "template1", "p07_outside").contains(database)) throw new IllegalArgumentException("시험 DB가 잘못되었습니다");
        try {
            String script = "PGPASSWORD=\"$AIGW_ACCEPTANCE_PASSWORD\" exec psql -X -w " + (unix ? "" : "-h 127.0.0.1 ")
                + "-U aigw_acceptance_ro -d " + database + " -c 'SELECT 1'";
            var result = container.execInContainer("sh", "-c", script);
            return result.getExitCode() != 0 && result.getStderr().contains("pg_hba.conf rejects connection");
        } catch (java.io.IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("격리 HBA 거부를 확인할 수 없습니다", failure);
        }
    }
    public boolean acceptsViaUnix() {
        try {
            var result = container.execInContainer("sh", "-c", "PGPASSWORD=\"$AIGW_ACCEPTANCE_PASSWORD\" exec psql -X -w -U aigw_acceptance_ro -d ai_gateway -Atc 'SELECT count(*) FROM gw.conversation'");
            return result.getExitCode() == 0 && result.getStdout().trim().equals("1");
        } catch (java.io.IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("격리 HBA 허용을 확인할 수 없습니다", failure);
        }
    }
    @Override public void close() {
        if (container != null && container.getContainerId() != null) {
            if (!owner.equals(container.getCurrentContainerInfo().getConfig().getLabels().get(OWNER_LABEL)))
                throw new IllegalStateException("시험 자원 소유 label이 일치하지 않습니다");
            container.stop();
        }
    }
    private static String password() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
