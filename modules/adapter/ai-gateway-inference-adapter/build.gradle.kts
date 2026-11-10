plugins { `java-library`; `java-test-fixtures` }
dependencies {
    implementation(project(":ai-gateway-core"))
    implementation("org.springframework:spring-web")
    implementation("io.micrometer:micrometer-core")
    implementation("com.fasterxml.jackson.core:jackson-databind")
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
    testImplementation(testFixtures(project(":adapter:ai-gateway-inference-adapter")))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation(libs.djl.tokenizers)
    testImplementation("io.micrometer:micrometer-core")
}

// 앱이 쓰는 tokenizer 자료도 시험 입력 지문에 포함합니다(Source 선언은 gitlab adapter 시험으로 옮김).
sourceSets.test {
    resources.srcDir(rootProject.file("modules/apps/ai-gateway-app/src/main/resources"))
    resources.include("tokenizer/**")
}

tasks.named<Test>("integrationTest") {
    systemProperty("ai.djl.offline", "true")
    val tokenizer = rootProject.file("eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json")
    inputs.file(tokenizer)
    systemProperty("aigw.tokenizerFile", tokenizer.absolutePath)
    systemProperty("aigw.harness.classpath", sourceSets["integrationTest"].runtimeClasspath.asPath)
    systemProperty("aigw.tokenizerSha256", rootProject.file("eval/f0/tokenizer/qwen3-4b-thinking-2507.tokenizer.json.sha256").readText().trim().substringBefore(' '))
}
