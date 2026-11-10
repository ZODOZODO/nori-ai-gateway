package com.nori.ai.gateway.eval;

import com.nori.ai.gateway.eval.io.Json;
import org.gradle.testkit.runner.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

final class TestKitSupport {
    final Path root;
    final TestSupport fixture;
    final Map<String,String> env;
    TestKitSupport(Path root) throws IOException {
        this.root=root;fixture=new TestSupport(root);env=sanitizedEnv();env.put("AIGW_SPEC_FILE",fixture.spec.toString());
        Path actualRoot=Path.of(System.getProperty("aigw.rootDir"));
        Files.copy(actualRoot.resolve("build.gradle.kts"),root.resolve("build.gradle.kts"));
        Files.createDirectories(root.resolve("gradle"));
        Files.copy(actualRoot.resolve("gradle/libs.versions.toml"),root.resolve("gradle/libs.versions.toml"));
        write("settings.gradle.kts",buildLogicSettings()+"rootProject.name=\"guard-fixture\"\ninclude(\"gateway-eval\",\"sub\",\"placeholder\")\nbuildCache { local { directory = rootDir.resolve(\"cache\") } }\n");
        write("gradle.properties","org.gradle.jvmargs=-Xmx384m -XX:MaxMetaspaceSize=256m\norg.gradle.workers.max=1\n");
        // 루트 build 는 java 를 모듈 plugins {} 에 맡기므로 시험 없는 모듈도 java 를 직접 적용한다.
        write("placeholder/build.gradle.kts","plugins { java }\n");
        String cp=System.getProperty("aigw.evalClasspath");assertThat(cp).isNotBlank();String quoted=cp.replace("\\","\\\\").replace("\"","\\\"");
        write("gateway-eval/build.gradle.kts","plugins { application }\napplication { mainClass=\"com.nori.ai.gateway.eval.EvalCli\" }\ndependencies { runtimeOnly(files(\""+quoted+"\".split(System.getProperty(\"path.separator\")))) }\n");
        write("sub/build.gradle.kts","plugins { application }\napplication { mainClass=\"com.nori.ai.gateway.eval.EvalCli\" }\ndependencies { testImplementation(\"org.junit.jupiter:junit-jupiter\"); testRuntimeOnly(\"org.junit.platform:junit-platform-launcher\"); runtimeOnly(files(\""+quoted+"\".split(System.getProperty(\"path.separator\")))) }\nval integrationTest by sourceSets.creating\nconfigurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())\nconfigurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())\ntasks.register<Test>(\"integrationTest\") { testClassesDirs=integrationTest.output.classesDirs; classpath=integrationTest.runtimeClasspath }\n");
        Path catalog=root.resolve("gateway-eval/src/main/resources/com/nori/ai/gateway/eval/leaf-catalog-v2.json");Files.createDirectories(catalog.getParent());
        Files.copy(actualRoot.resolve("gateway-eval/src/main/resources/com/nori/ai/gateway/eval/leaf-catalog-v2.json"),catalog);
    }
    // 복사한 루트 build 가 root-project-conventions 를 요구하므로 실제 settings 와 같은 composite build-logic·settings-conventions 를 둔다.
    static String buildLogicSettings() {
        String buildLogic=Path.of(System.getProperty("aigw.rootDir")).resolve("../nori-common/build-logic").normalize().toString().replace('\\','/');
        return "pluginManagement { includeBuild(\""+buildLogic+"\") { name = \"nori-common-build-logic\" } }\nplugins { id(\"com.nori.common.settings-conventions\") }\n";
    }
    static Map<String,String> sanitizedEnv() {
        Map<String,String> sanitized=new HashMap<>(System.getenv());sanitized.keySet().removeIf(k->{String key=k.toUpperCase(Locale.ROOT);return key.equals("CI")||key.startsWith("CI_")||key.startsWith("GITLAB_")||key.startsWith("AIGW_");});return sanitized;
    }
    void write(String relative,String text) throws IOException {Path path=root.resolve(relative);Files.createDirectories(path.getParent());Files.writeString(path,text,StandardCharsets.UTF_8);}
    void passingTest() throws IOException { write("sub/src/test/java/probe/PassingTest.java","package probe; import org.junit.jupiter.api.*; class PassingTest { @Test void checksFixtureArithmetic(){ Assertions.assertEquals(4,2+2); } }\n"); }
    BuildResult run(boolean fail,String... arguments) {
        List<String> args=new ArrayList<>(List.of(arguments));args.addAll(List.of("--console=plain","--max-workers=1","--stacktrace"));
        GradleRunner runner=GradleRunner.create().withGradleVersion("9.2.1").withProjectDir(root.toFile()).withEnvironment(env).withArguments(args);
        return fail?runner.buildAndFail():runner.build();
    }
    String verifyArgs(String tasks){return "--args=test-evidence verify --tasks "+tasks;}
    String runId(){return Json.read(root.resolve("sub/build/aigw-otr/test/run-binding.json")).path("runId").asText();}
    void deleteOutput(String relative) throws IOException {
        Path target=root.resolve(relative).toAbsolutePath().normalize();if(!target.startsWith(root.toAbsolutePath().normalize()))throw new IllegalStateException("OUTSIDE_FIXTURE");
        if(!Files.exists(target))return;try(var files=Files.walk(target)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
    }
}
