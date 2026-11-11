import java.security.MessageDigest

plugins { `java-library`; `java-test-fixtures` }
dependencies {
    implementation(project(":ai-gateway-core"))
    api("org.springframework.boot:spring-boot-starter-jdbc")
    // SchemaVersionGuard 의 MigrationVersion 비교만 쓴다. 앱 안 마이그레이션(Spring Flyway 자동 설정)은 없다.
    implementation(libs.flyway.core)
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(libs.flyway.core)
    testImplementation(libs.archunit)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":adapter:ai-gateway-db-adapter")))
    testImplementation(testFixtures(project(":adapter:ai-gateway-inference-adapter")))
    testFixturesImplementation(platform(libs.spring.boot.bom))
    testFixturesImplementation(enforcedPlatform(libs.testcontainers.bom))
    testFixturesImplementation("org.testcontainers:testcontainers-postgresql")
    // PgTestDatabase.flywayConfiguration() 이 Flyway 설정 타입을 공개한다.
    testFixturesApi(libs.flyway.core)
    testFixturesImplementation("org.flywaydb:flyway-database-postgresql")
    testFixturesImplementation("org.postgresql:postgresql")
}

// RepositorySignatureArchTest 는 app 의 실제 호출자 클래스(build/classes/java/main)를 읽는다. 종전 평면 경로에서는
// app 의 compileJava 가 실행 순서상 먼저 돌아 우연히 존재했고, :adapter 가 :apps 보다 앞서는 지금은 명시해야 한다.
tasks.named<Test>("test") { dependsOn(":apps:ai-gateway-app:classes") }

// SchemaVersionGuard 는 classpath db/migration/gateway 사본을 읽으므로 이 모듈 IT 에도 루트 flyway/sql 사본을 둔다(app 의 processResources 와 같은 복사).
tasks.named<ProcessResources>("processIntegrationTestResources") {
    from(rootProject.file("flyway/sql")) { into("db/migration/gateway") }
}

tasks.named<Test>("integrationTest") {
    val pgImage = providers.environmentVariable("AIGW_PG_TEST_IMAGE").orElse("").get()
    inputs.property("aigw.pgTestImage", pgImage)
    systemProperty("aigw.pgTestImage", pgImage)
    // 시험 Flyway 는 루트 flyway/sql 을 filesystem 위치로 읽는다. 값은 aigw.rootDir 기준 상대 경로다.
    inputs.dir(rootProject.file("flyway/sql"))
    systemProperty("aigw.flyway.sql.dir", "flyway/sql")
    for ((key, path) in mapOf("pgBootstrapSha256" to "operations/deploy/helm/ai-gateway-db-restore/bootstrap.sql")) {
        val source = rootProject.file(path)
        val digest = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        inputs.file(source)
        inputs.property("aigw.$key", digest)
        systemProperty("aigw.$key", digest)
    }
    doFirst {
        if (!pgImage.matches(Regex("[^\\s/@]+(?:/[^\\s/@]+)*:[^\\s/@:]+@sha256:[0-9a-f]{64}")))
            throw GradleException("고정된 PostgreSQL 시험 이미지가 필요합니다")
    }
}
