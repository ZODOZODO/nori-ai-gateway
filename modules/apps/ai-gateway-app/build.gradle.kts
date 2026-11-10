import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.process.CommandLineArgumentProvider

plugins {
    application
    alias(libs.plugins.spring.boot)
}
application { mainClass = "com.nori.ai.gateway.app.AiGatewayApplication" }
dependencies {
    implementation(project(":ai-gateway-core"))
    implementation(project(":adapter:ai-gateway-db-adapter"))
    implementation(project(":adapter:ai-gateway-inference-adapter"))
    implementation(project(":adapter:ai-gateway-tc-mock-adapter"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.opentelemetry:opentelemetry-sdk-logs")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation("io.micrometer:micrometer-registry-otlp")
    implementation(platform(libs.otel.instrumentation.bom.alpha))
    implementation(libs.otel.logback)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.djl.tokenizers)
    add("integrationTestImplementation", libs.nimbus.jose.jwt)
    add("integrationTestImplementation", libs.protobuf)
    add("integrationTestImplementation", libs.json.schema.validator)
    testImplementation(libs.archunit)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":adapter:ai-gateway-inference-adapter")))
    testImplementation(testFixtures(project(":adapter:ai-gateway-db-adapter")))
    testImplementation(testFixtures(project(":adapter:ai-gateway-tc-mock-adapter")))
    testImplementation(platform(libs.spring.ai.bom))
    testImplementation(libs.spring.ai.openai)
    testImplementation(libs.openai.java.okhttp)
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation(libs.nori.storage.starter)
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.named<Test>("integrationTest") {
    val harnessArguments = objects.newInstance<HarnessJvmArguments>()
    harnessArguments.runtimeClasspath.from(sourceSets["integrationTest"].runtimeClasspath)
    harnessArguments.javaExecutable.set(javaLauncher.map { it.executablePath })
    harnessArguments.argumentFile.set(layout.buildDirectory.file("aigw/harness-jvm.args"))
    jvmArgumentProviders.add(harnessArguments)
    val pgImage = providers.environmentVariable("AIGW_PG_TEST_IMAGE").orElse("").get()
    inputs.property("aigw.pgTestImage", pgImage)
    systemProperty("aigw.pgTestImage", pgImage)
    // P15 BackupScriptIT: 승인 backup 이미지(-Paigw.backupImage, 없으면 Dockerfile-backup 로컬 빌드)와 이미지 context 입력 바이트를 시험 지문에 결속한다.
    val backupImage = providers.gradleProperty("aigw.backupImage").orElse("").get()
    inputs.property("aigw.backupImage", backupImage)
    systemProperty("aigw.backupImage", backupImage)
    for ((key, path) in mapOf("pgBootstrapSha256" to "ops/postgres/bootstrap.sql", "pgHbaSha256" to "ops/postgres/acceptance-pg_hba.conf",
            "backupDockerfileSha256" to "deploy/docker/Dockerfile-backup", "backupScriptSha256" to "ops/backup/aigw-backup.sh",
            "restoreScriptSha256" to "ops/backup/aigw-restore.sh", "backupJobColumnsSha256" to "ops/backup/job-columns-v1.txt")) {
        val source = rootProject.file(path)
        val digest = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        inputs.file(source)
        inputs.property("aigw.$key", digest)
        systemProperty("aigw.$key", digest)
    }
    doFirst {
        if (!pgImage.matches(Regex("[^\\s/@]+(?:/[^\\s/@]+)*:[^\\s/@:]+@sha256:[0-9a-f]{64}")))
            throw GradleException("고정된 PostgreSQL 시험 이미지가 필요합니다")
        if (backupImage.isNotEmpty() && !backupImage.matches(Regex("harbor\\.nori\\.local/nori-tc/ai-gateway-backup@sha256:[0-9a-f]{64}")))
            throw GradleException("aigw.backupImage 는 harbor.nori.local/nori-tc/ai-gateway-backup@sha256:<64hex> 형식만 허용합니다")
    }
}

abstract class HarnessJvmArguments : CommandLineArgumentProvider {
    @get:Classpath abstract val runtimeClasspath: ConfigurableFileCollection
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val javaExecutable: RegularFileProperty
    @get:Internal abstract val argumentFile: RegularFileProperty
    override fun asArguments(): Iterable<String> {
        val arguments = listOf("-Daigw.harness.classpath=${runtimeClasspath.asPath}",
            "-Daigw.harness.javaExe=${javaExecutable.get().asFile.absolutePath}")
        val file = argumentFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(arguments.joinToString("\n", postfix="\n") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }, Charsets.UTF_8)
        return listOf("@${file.absolutePath}")
    }
}

abstract class WriteRuntimeClasspath : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val artifacts: ConfigurableFileCollection
    @get:Input abstract val coordinates: ListProperty<String>
    @get:OutputFile abstract val outputFile: RegularFileProperty
    @TaskAction fun write() {
        val files = artifacts.files.associateBy { it.name }
        val lines = coordinates.get().map { entry ->
            val coordinate = entry.substringBefore('|')
            val file = files.getValue(entry.substringAfter('|'))
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            val natives = if (file.extension == "jar") ZipFile(file).use { zip ->
                zip.entries().asSequence().filter { it.name.startsWith("native/lib/") && !it.isDirectory }.map { it.name }.sorted().toList()
            } else emptyList()
            "$coordinate sha256=$hash nativeResources=${natives.joinToString(",")}"
        }.sorted()
        outputFile.get().asFile.apply { parentFile.mkdirs(); writeText(lines.joinToString("\n", postfix="\n"), Charsets.UTF_8) }
    }
}

val writeRuntimeClasspath by tasks.registering(WriteRuntimeClasspath::class) {
    val runtime = configurations.runtimeClasspath
    artifacts.from(runtime)
    coordinates.set(provider { runtime.get().resolvedConfiguration.resolvedArtifacts.map { "${it.moduleVersion.id}|${it.file.name}" } })
    outputFile.set(layout.buildDirectory.file("aigw/runtime-classpath.txt"))
}
tasks.named<Test>("test") {
    dependsOn(writeRuntimeClasspath)
    dependsOn(":gateway-eval:classes")
    inputs.file(writeRuntimeClasspath.flatMap { it.outputFile })
    systemProperty("aigw.runtimeClasspathFile", writeRuntimeClasspath.get().outputFile.get().asFile.absolutePath)
}
tasks.register<Copy>("syncRuntimeClasspathEvidence") {
    from(writeRuntimeClasspath.flatMap { it.outputFile })
    into(rootProject.layout.projectDirectory.dir("docs/gates/f1"))
}
