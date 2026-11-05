plugins { `java-library`; `java-test-fixtures` }
dependencies {
    implementation(project(":gateway-core"))
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.ai.openai)
    implementation(libs.djl.tokenizers)
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation(libs.nori.storage.starter)
    implementation(platform(libs.awssdk.bom))
    implementation("software.amazon.awssdk:s3")
    testFixturesImplementation(enforcedPlatform(libs.testcontainers.bom))
    testFixturesImplementation(platform(libs.spring.boot.bom))
    testFixturesImplementation("org.testcontainers:testcontainers")
    testFixturesImplementation(libs.junit.jupiter)
    testImplementation(testFixtures(project(":gateway-integration")))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation(libs.djl.tokenizers)
}

tasks.named<Test>("integrationTest") {
    systemProperty("ai.djl.offline", "true")
    val tokenizer = rootProject.file("eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json")
    inputs.file(tokenizer)
    systemProperty("aigw.tokenizerFile", tokenizer.absolutePath)
    systemProperty("aigw.harness.classpath", sourceSets["integrationTest"].runtimeClasspath.asPath)
    systemProperty("aigw.tokenizerSha256", rootProject.file("eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json.sha256").readText().trim().substringBefore(' '))
}
