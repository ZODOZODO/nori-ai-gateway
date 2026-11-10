import java.security.MessageDigest

plugins { `java-library`; `java-test-fixtures` }
dependencies {
    implementation(project(":gateway-core"))
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    testImplementation(libs.archunit)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":gateway-persistence")))
    testImplementation(testFixtures(project(":gateway-integration")))
    testFixturesImplementation(platform(libs.spring.boot.bom))
    testFixturesImplementation(enforcedPlatform(libs.testcontainers.bom))
    testFixturesImplementation("org.testcontainers:testcontainers-postgresql")
    testFixturesImplementation("org.flywaydb:flyway-core")
    testFixturesImplementation("org.flywaydb:flyway-database-postgresql")
    testFixturesImplementation("org.postgresql:postgresql")
}

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
