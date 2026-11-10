import java.security.MessageDigest

plugins { `java-library`; `java-test-fixtures` }
dependencies {
    implementation(project(":ai-gateway-core"))
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    testImplementation(libs.archunit)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":adapter:ai-gateway-db-adapter")))
    testImplementation(testFixtures(project(":adapter:ai-gateway-inference-adapter")))
    testFixturesImplementation(platform(libs.spring.boot.bom))
    testFixturesImplementation(enforcedPlatform(libs.testcontainers.bom))
    testFixturesImplementation("org.testcontainers:testcontainers-postgresql")
    testFixturesImplementation("org.flywaydb:flyway-core")
    testFixturesImplementation("org.flywaydb:flyway-database-postgresql")
    testFixturesImplementation("org.postgresql:postgresql")
}

// RepositorySignatureArchTest 는 app 의 실제 호출자 클래스(build/classes/java/main)를 읽는다. 종전 평면 경로에서는
// app 의 compileJava 가 실행 순서상 먼저 돌아 우연히 존재했고, :adapter 가 :apps 보다 앞서는 지금은 명시해야 한다.
tasks.named<Test>("test") { dependsOn(":apps:ai-gateway-app:classes") }

tasks.named<Test>("integrationTest") {
    val pgImage = providers.environmentVariable("AIGW_PG_TEST_IMAGE").orElse("").get()
    inputs.property("aigw.pgTestImage", pgImage)
    systemProperty("aigw.pgTestImage", pgImage)
    for ((key, path) in mapOf("pgBootstrapSha256" to "ops/postgres/bootstrap.sql", "pgHbaSha256" to "ops/postgres/acceptance-pg_hba.conf")) {
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
