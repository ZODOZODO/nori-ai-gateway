plugins { application }
application { mainClass = "com.nori.ai.gateway.eval.EvalCli" }
dependencies {
    implementation(libs.jackson)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
// F0 시험이 직접 읽는 자료 바이트를 Test 선언 입력과 run-binding fingerprint 에 같은 값으로 결속합니다(회귀 시험이 같은 파일을 적용).
apply(from = "f0-test-inputs.gradle.kts")
