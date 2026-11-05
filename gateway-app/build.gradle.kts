import java.security.MessageDigest
import java.util.zip.ZipFile

plugins { application }
application { mainClass = "com.nori.ai.gateway.app.AiGatewayApplication" }
dependencies {
    implementation(project(":gateway-core"))
    implementation(project(":gateway-persistence"))
    implementation(project(":gateway-integration"))
    implementation(project(":gateway-tc-mock"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.opentelemetry:opentelemetry-sdk-logs")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    implementation(libs.otel.logback)
    add("integrationTestImplementation", libs.nimbus.jose.jwt)
    add("integrationTestImplementation", libs.protobuf)
    add("integrationTestImplementation", libs.json.schema.validator)
    testImplementation(libs.archunit)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(testFixtures(project(":gateway-integration")))
    testImplementation(platform(libs.spring.ai.bom))
    testImplementation(libs.spring.ai.openai)
    testImplementation(libs.openai.java.okhttp)
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation(libs.nori.storage.starter)
    testImplementation("org.testcontainers:testcontainers-postgresql")
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
