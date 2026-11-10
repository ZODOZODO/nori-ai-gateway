import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.util.UUID
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.process.CommandLineArgumentProvider

plugins {
    base
    id("com.nori.common.root-project-conventions")
}

// 저장소 identity 는 이 allprojects 한 곳이 명시한다. root-project-conventions 는 group/version 을 정하지 않는다.
// Java 패키지 com.nori.ai.gateway.* 는 이 값과 무관하게 그대로다.
allprojects {
    group = "com.nori.ai.gateway"
    version = "0.0.1-SNAPSHOT"
}

fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
fun fileSha(file: File): String = if (file.isFile) sha(file.readBytes()) else "MISSING"
fun ordered(value: Any?): Any? = when (value) {
    is Map<*, *> -> value.entries.associate { it.key.toString() to ordered(it.value) }.toSortedMap()
    is Iterable<*> -> value.map { ordered(it) }
    else -> value
}
fun json(value: Any?): String = JsonOutput.toJson(ordered(value))
var aigwExplicitSpec: File? = null
fun specFile(): File? = aigwExplicitSpec ?: providers.gradleProperty("aigw.specPath").orNull
    ?.let { rootProject.file(it) }
    ?: providers.environmentVariable("AIGW_SPEC_FILE").orNull?.let { rootProject.file(it) }
fun catalogFile(): File = rootProject.file("gateway-eval/src/main/resources/com/nori/ai/gateway/eval/leaf-catalog-v2.json")
fun classpathItem(file: File): Any = if (file.isDirectory) file.walkTopDown().filter { it.isFile }
    .map { file.toPath().relativize(it.toPath()).toString().replace('\\', '/') to fileSha(it) }
    .sortedBy { it.first }.map { listOf(it.first, it.second) }.toList() else fileSha(file)
fun aigwInputComponents(test: Test): Map<String, Any?> {
    val properties = test.systemProperties.filterKeys { it.startsWith("aigw.") }
        .filterKeys { it !in setOf("aigw.rootDir", "aigw.specPath", "aigw.evalClasspath", "aigw.harness.classpath", "aigw.harness.javaExe") }
        .mapValues { (key, value) -> if (key in setOf("aigw.runtimeClasspathFile", "aigw.tokenizerFile")) fileSha(rootProject.file(value.toString())) else value.toString() }
    return mapOf(
        "classpath" to test.classpath.files.map { classpathItem(it) },
        "specSha" to (specFile()?.let { fileSha(it) } ?: "MISSING"),
        "baselineSha256" to fileSha(rootProject.file("docs/gates/spec-baseline.md")),
        "catalogSha256" to fileSha(catalogFile()),
        "includeTags" to (providers.gradleProperty("includeTags").orNull ?: "").split(',').filter { it.isNotBlank() }.sorted(),
        "testFilters" to (test.filter.includePatterns + (test.filter as org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter).commandLineIncludePatterns).sorted(),
        "systemProperties" to properties
    )
}
fun aigwInputFingerprint(test: Test): String = sha(json(aigwInputComponents(test)).toByteArray(Charsets.UTF_8))
val requiredTests = mutableSetOf<String>()

subprojects {
    // java 는 각 모듈의 plugins {} 가 적용한다. 루트가 먼저 적용하면 java-module-conventions 가 아직 등록되지 않은 모듈의
    // libs catalog extension 을 찾다가 실패하고, 구조용 project 에도 java 가 붙는다. java 적용 시 root-project-conventions 가
    // java-module-conventions(toolchain 21·UTF-8·-parameters·JUnit Platform·compileOnly slf4j-api)와
    // dependency-matrix-conventions 를 붙인다. 의존 저장소는 settings 가 소유한다.
    pluginManager.withPlugin("java") {
    dependencies {
        add("implementation", platform(rootProject.libs.spring.boot.bom))
        add("testRuntimeOnly", "org.junit.platform:junit-platform-reporting")
        if (project.path != ":gateway-eval") {
            add("testImplementation", rootProject.libs.junit.jupiter)
            add("testImplementation", rootProject.libs.assertj)
            add("testImplementation", enforcedPlatform(rootProject.libs.testcontainers.bom))
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        }
    }
    if (project.name in setOf("ai-gateway-core", "ai-gateway-db-adapter", "ai-gateway-inference-adapter", "ai-gateway-source-support", "ai-gateway-loki-adapter", "ai-gateway-tempo-adapter", "ai-gateway-gitlab-adapter", "ai-gateway-tc-mock-adapter", "ai-gateway-app", "ai-gateway-acceptance-app")) {
        val integration = extensions.getByType<SourceSetContainer>().create("integrationTest")
        val main = extensions.getByType<SourceSetContainer>()["main"]
        integration.compileClasspath += main.output
        integration.runtimeClasspath += main.output
        configurations[integration.implementationConfigurationName].extendsFrom(configurations["testImplementation"])
        configurations[integration.runtimeOnlyConfigurationName].extendsFrom(configurations["testRuntimeOnly"])
        tasks.register<Test>("integrationTest") {
            testClassesDirs = integration.output.classesDirs
            classpath = integration.runtimeClasspath
            systemProperty("aigw.requireDocker", providers.gradleProperty("aigw.requireDocker").orElse("false").get())
        }
    }
    tasks.withType<JavaExec>().configureEach { workingDir = rootDir }
    afterEvaluate { tasks.withType<Test>().toList().forEach { configuredTest -> configuredTest.apply {
        val testTask = this
        useJUnitPlatform {
            providers.gradleProperty("includeTags").orNull?.split(',')?.filter { it.isNotBlank() }?.let { includeTags(*it.toTypedArray()) }
        }
        filter.isFailOnNoMatchingTests = true
        systemProperty("junit.platform.reporting.open.xml.enabled", "true")
        systemProperty("aigw.rootDir", rootDir.absolutePath)
        if (project.path == ":gateway-eval") systemProperty("aigw.evalClasspath", project.extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath.asPath)
        val otr = layout.buildDirectory.dir("aigw-otr/$name")
        val evidence = layout.buildDirectory.file("aigw-evidence/$name/leaf-results.json")
        jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Djunit.platform.reporting.output.dir=${otr.get().asFile.absolutePath}") })
        outputs.dir(otr)
        // 파일 경로가 같아도 내용 변경은 반드시 새 측정으로 이어져야 합니다.
        inputs.property("aigw.specSha", provider { specFile()?.let { fileSha(it) } ?: "MISSING" })
        inputs.property("aigw.baselineSha", provider { fileSha(rootProject.file("docs/gates/spec-baseline.md")) })
        inputs.property("aigw.catalogSha", provider { fileSha(catalogFile()) })
        inputs.property("aigw.policyInputs", provider { json(aigwInputComponents(testTask).filterKeys { it != "classpath" }) })
        doFirst {
            val output = otr.get().asFile
            delete(output)
            output.mkdirs()
            val components = aigwInputComponents(testTask)
            File(output, "run-binding.json").writeText(json(mapOf(
                "runId" to UUID.randomUUID().toString(), "inputFingerprint" to aigwInputFingerprint(testTask),
                "recordedAt" to java.time.Instant.now().toString(),
                "specSha" to components["specSha"], "baselineSha256" to components["baselineSha256"],
                "catalogSha256" to components["catalogSha256"], "components" to components
            )), Charsets.UTF_8)
        }
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) = Unit
            override fun beforeTest(testDescriptor: TestDescriptor) = Unit
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) = Unit
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.parent == null && result.testCount == 0L) throw GradleException("NO_TESTS_EXECUTED")
            }
        })
        val extraction = tasks.register<JavaExec>("${name}LeafEvidence") {
            workingDir = rootDir
            mainClass = "com.nori.ai.gateway.eval.EvalCli"
            val app = rootProject.findProject(":apps:ai-gateway-app")
            if (app != null) dependsOn(":apps:ai-gateway-app:writeRuntimeClasspath")
            val eval = rootProject.findProject(":gateway-eval")
            if (eval != null) {
                classpath = eval.extensions.getByType<SourceSetContainer>()["main"].runtimeClasspath
            } else {
                classpath = files(providers.gradleProperty("aigw.evalClasspath").get().split(File.pathSeparator))
            }
            doFirst {
                // 선언된 환경값 대신 현재 레포의 실제 HEAD를 증거에 결속합니다. 비 Git fixture는 기존 동작을 유지합니다.
                if (rootProject.file(".git").exists()) {
                    val head = providers.exec {
                        workingDir(rootDir)
                        commandLine("git", "rev-parse", "HEAD")
                    }.standardOutput.asText.get().trim()
                    if (!head.matches(Regex("[0-9a-f]{40}"))) throw GradleException("GIT_IDENTITY_INVALID")
                    environment("AIGW_GIT_SHA", head)
                }
                val arguments = mutableListOf("leaf-extract", "--otr-dir", otr.get().asFile.absolutePath,
                    "--task", testTask.path, "--module", project.path,
                    "--baseline", rootProject.file("docs/gates/spec-baseline.md").absolutePath,
                    "--catalog", catalogFile().absolutePath,
                    "--out", evidence.get().asFile.absolutePath,
                    "--current-fingerprint", aigwInputFingerprint(testTask))
                specFile()?.let { arguments.addAll(listOf("--spec", it.absolutePath)) }
                if (app != null) arguments.addAll(listOf("--runtime-classpath-file", app.layout.buildDirectory.file("aigw/runtime-classpath.txt").get().asFile.absolutePath))
                if (testTask.path in requiredTests) arguments.add("--require-executed")
                setArgs(arguments)
            }
        }
        finalizedBy(extraction)
    } } }
    }
}

gradle.taskGraph.whenReady {
    val requiredFile = rootProject.file("ci/integration-required.txt")
    val registeredModules = if (requiredFile.isFile) requiredFile.readLines().filter { !it.startsWith("#") && it.isNotBlank() }.map { it.substringBefore(' ').substringBefore('|') }.toSet() else emptySet()
    // 직접 경로와 루트 비한정 selector는 서로 다른 필수 대상 규칙을 가집니다.
    gradle.startParameter.taskRequests.flatMap { it.args }.filter { !it.startsWith("-") }.forEach { selector ->
        if (selector.startsWith(":")) {
            rootProject.allprojects.flatMap { it.tasks.withType<Test>().toList() }.filter { it.path == selector }.forEach { requiredTests.add(it.path) }
        } else {
            rootProject.allprojects.flatMap { it.tasks.withType<Test>().toList() }.filter { it.name == selector && (it.project.path in registeredModules || it.project.name in registeredModules) }.forEach { requiredTests.add(it.path) }
        }
    }
}

project(":gateway-eval") {
    pluginManager.withPlugin("application") {
    tasks.named<JavaExec>("run") {
        dependsOn(provider {
            val arguments = args ?: emptyList()
            if (arguments.take(2) != listOf("test-evidence", "verify")) emptyList<Any>() else {
                val index = arguments.indexOf("--tasks")
                val paths = if (index >= 0) arguments[index + 1].split(',') else {
                    val selector = arguments.indexOf("--selector")
                    if (selector < 0) emptyList() else rootProject.allprojects.flatMap { it.tasks.withType<Test>().toList() }.filter { it.name == arguments[selector + 1] }.map { it.path }
                }
                paths.mapNotNull { rootProject.tasks.findByPath(it) as? Test }.flatMap { listOf(it.classpath, it.testClassesDirs) }
            }
        })
        doFirst {
            val arguments = args ?: emptyList()
            if (arguments.take(2) == listOf("test-evidence", "verify")) {
                val specIndex = arguments.indexOf("--spec")
                if (specIndex >= 0) aigwExplicitSpec = rootProject.file(arguments[specIndex + 1])
                val tasksIndex = arguments.indexOf("--tasks")
                val selectorIndex = arguments.indexOf("--selector")
                val selected = if (tasksIndex >= 0) arguments[tasksIndex + 1].split(',') else {
                    val requiredIndex = arguments.indexOf("--required")
                    if (selectorIndex < 0 || requiredIndex < 0) throw GradleException("INVALID_ARGUMENTS")
                    val required = rootProject.file(arguments[requiredIndex + 1]).readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { it.substringBefore(' ').substringBefore('|') }.toSet()
                    rootProject.allprojects.flatMap { it.tasks.withType<Test>().toList() }.filter { it.name == arguments[selectorIndex + 1] && (it.project.path in required || it.project.name in required) }.map { it.path }
                }
                selected.forEach { path ->
                    val test = rootProject.tasks.findByPath(path) as? Test ?: throw GradleException("UNKNOWN_TEST_TASK")
                    val out = rootProject.file("build/aigw-fingerprint/${path.replace(':', '_')}.json")
                    out.parentFile.mkdirs()
                    // 제품 모듈은 settings 의 projectDir 로 modules/ 아래에 있으므로 verifier 가 증거 위치를 task 경로로 유도하지 않도록 실제 위치를 함께 기록합니다.
                    val projectDir = rootDir.toPath().relativize(test.project.projectDir.toPath()).toString().replace('\\', '/')
                    out.writeText(json(mapOf("taskPath" to path, "inputFingerprint" to aigwInputFingerprint(test), "projectDir" to projectDir)), Charsets.UTF_8)
                }
            }
        }
    }
    }
}

// 패키징 정본: 한 번의 Gradle 호출에서 bootJar·수용 배포본·runtime classpath를 함께 만들어 신원이 갈라지지 않게 합니다.
val aigwPackageDir = layout.buildDirectory.dir("package")
val aigwPackage by tasks.registering(Sync::class) {
    val app = project(":apps:ai-gateway-app")
    into(aigwPackageDir)
    // bootJar 출력만 사용합니다. plain jar·installDist·Boot 배포 task 산출은 이미지에 들어가지 않습니다.
    from(app.tasks.named<Jar>("bootJar").flatMap { it.archiveFile }) { rename { "app.jar" } }
    from(app.tasks.named("writeRuntimeClasspath")) { rename { "runtime-classpath.txt" } }
    from(project(":apps:ai-gateway-acceptance-app").tasks.named("installDist")) { into("acceptance") }
}
val aigwBuildIdentity by tasks.registering {
    val gitSha = providers.gradleProperty("aigw.gitSha")
    val appJar = aigwPackageDir.map { it.file("app.jar") }
    val runtimeClasspath = aigwPackageDir.map { it.file("runtime-classpath.txt") }
    val identity = aigwPackageDir.map { it.file("build-identity.json") }
    dependsOn(aigwPackage)
    inputs.property("aigw.gitSha", gitSha.orElse(""))
    inputs.files(appJar, runtimeClasspath)
    outputs.file(identity)
    // 작업 트리 상태는 Gradle 입력으로 추적되지 않으므로 매번 다시 판정합니다.
    outputs.upToDateWhen { false }
    doLast {
        val output = identity.get().asFile
        output.delete()
        val sha = gitSha.orNull ?: throw GradleException("BUILD_IDENTITY_GIT_SHA_MISSING")
        if (!sha.matches(Regex("[0-9a-f]{40}"))) throw GradleException("BUILD_IDENTITY_GIT_SHA_INVALID")
        // 다른 commit 이나 수정된 트리에서 만든 jar 에 그 SHA 를 붙이지 않습니다.
        val head = providers.exec { workingDir(rootDir); commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.get().trim()
        if (head != sha) throw GradleException("BUILD_IDENTITY_HEAD_MISMATCH")
        val status = providers.exec { workingDir(rootDir); commandLine("git", "status", "--porcelain") }.standardOutput.asText.get()
        if (status.isNotBlank()) throw GradleException("BUILD_IDENTITY_DIRTY_TREE")
        val jarSha = fileSha(appJar.get().asFile)
        val classpathSha = fileSha(runtimeClasspath.get().asFile)
        if (jarSha == "MISSING" || classpathSha == "MISSING") throw GradleException("BUILD_IDENTITY_INPUT_MISSING")
        output.writeText(json(mapOf("gitSha" to sha, "appJarSha256" to jarSha, "runtimeClasspathSha256" to classpathSha)) + "\n", Charsets.UTF_8)
    }
}
aigwPackage { finalizedBy(aigwBuildIdentity) }
