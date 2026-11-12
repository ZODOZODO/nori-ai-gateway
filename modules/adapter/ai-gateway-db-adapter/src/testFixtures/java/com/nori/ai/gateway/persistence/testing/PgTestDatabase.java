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
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * 공유 운영 서버의 서버 담당자 선행조건(DB nori_ai_gateway·vector 0.8.7·DB 를 소유하는 단일 계정)을 격리 컨테이너에 재현하고
 * 자격과 컨테이너 수명을 소유합니다. superuser 는 컨테이너 준비({@link #bootstrap()})와 시험의 결함 주입에만 씁니다.
 * 계정 이름은 실행마다 무작위로 만들어 코드가 특정 계정 이름에 기대지 않게 합니다.
 */
public final class PgTestDatabase implements AutoCloseable {
    public static final String DATABASE = "nori_ai_gateway";
    private static final String OWNER_LABEL = "aigw.p07.owner";
    private final String owner = UUID.randomUUID().toString();
    private final Path root;
    private final String image;
    private final String account = "acct_" + HexFormat.of().formatHex(randomBytes(6));
    private final Map<String, String> passwords = Map.of("postgres", password(), account, password());
    private PostgreSQLContainer container;

    public PgTestDatabase(String image, Path root) {
        if (image == null || !image.matches("[^\\s/@]+(?:/[^\\s/@]+)*:[^\\s/@:]+@sha256:[0-9a-f]{64}"))
            throw new IllegalArgumentException("고정된 PostgreSQL 시험 이미지가 필요합니다");
        this.image = image; this.root = root.toAbsolutePath().normalize();
        verifySource("operations/postgres/bootstrap.sql", "aigw.pgBootstrapSha256");
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
                .withDatabaseName(DATABASE).withUsername("postgres").withPassword(passwords.get("postgres"))
                .withLabel(OWNER_LABEL, owner).withStartupTimeout(java.time.Duration.ofMinutes(3)).withEnv("PGDATA", "/var/lib/postgresql/18/docker")
                .withEnv("AI_GATEWAY_DB_USER", account)
                .withEnv("AI_GATEWAY_DB_PASSWORD", passwords.get(account))
                .withCopyFileToContainer(MountableFile.forHostPath(root.resolve("operations/postgres/bootstrap.sql")), "/tmp/p07-bootstrap.sql");
            container.start();
            try (var connection = connection("postgres"); var statement = connection.createStatement();
                    var result = statement.executeQuery("SHOW server_version")) {
                if (!result.next() || !result.getString(1).startsWith("18.6")) throw new IllegalStateException("PostgreSQL 시험 버전이 일치하지 않습니다");
                System.out.println("P07_PG image=" + image + " version=" + result.getString(1));
            }
            return this;
        } catch (Exception failure) { close(); throw new IllegalStateException("격리 PostgreSQL 준비에 실패했습니다", failure); }
    }
    /** 서버 담당자 선행조건 재현: superuser 의 psql 로 정본 bootstrap 을 실행한다(멱등). */
    public void bootstrap() {
        try {
            var result = container.execInContainer("psql", "-X", "-w", "-U", "postgres", "-d", DATABASE, "-v", "ON_ERROR_STOP=1",
                "-f", "/tmp/p07-bootstrap.sql");
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
    /** 루트 build.gradle.kts 의 Flyway 설정과 같은 값(filesystem flyway/sql, 이력 gw_flyway, 단일 계정)이다. */
    public FluentConfiguration flywayConfiguration() {
        String directory = System.getProperty("aigw.flyway.sql.dir");
        if (directory == null || directory.isBlank()) throw new IllegalStateException("aigw.flyway.sql.dir 시험 속성이 필요합니다");
        return Flyway.configure().dataSource(jdbcUrl(), account, passwordFor(account))
            .defaultSchema("gw_flyway").createSchemas(true).locations("filesystem:" + root.resolve(directory));
    }
    public void migrate() { flywayConfiguration().load().migrate(); }
    /** 단일 계정 이름(앱·migration·수용 oracle 이 함께 쓴다). */
    public String user() { return account; }
    public Connection connection(String role) throws SQLException { return DriverManager.getConnection(jdbcUrl(), role, passwordFor(role)); }
    public String jdbcUrl() { return container.getJdbcUrl(); }
    public String passwordFor(String role) {
        String value = passwords.get(role);
        if (value == null) throw new IllegalArgumentException("시험 역할이 잘못되었습니다");
        return value;
    }
    @Override public void close() {
        if (container != null && container.getContainerId() != null) {
            if (!owner.equals(container.getCurrentContainerInfo().getConfig().getLabels().get(OWNER_LABEL)))
                throw new IllegalStateException("시험 자원 소유 label이 일치하지 않습니다");
            container.stop();
        }
    }
    private static byte[] randomBytes(int size) { byte[] bytes = new byte[size]; new SecureRandom().nextBytes(bytes); return bytes; }
    private static String password() { return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32)); }
}
