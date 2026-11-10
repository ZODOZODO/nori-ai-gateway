plugins { application }
// 배포 수용 하네스는 installDist 배포본으로만 실행합니다(Boot·fat-jar 0). Gateway 앱은 이 모듈에 의존하지 않습니다.
application { mainClass = "com.nori.ai.gateway.acceptance.AcceptanceMain" }
dependencies {
    implementation(project(":gateway-core"))
    implementation(project(":gateway-tc-mock"))
    implementation(project(":gateway-integration"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation(testFixtures(project(":gateway-tc-mock")))
    implementation("org.junit.jupiter:junit-jupiter-api")
    implementation("org.junit.platform:junit-platform-launcher")
    implementation("org.postgresql:postgresql")
    runtimeOnly("org.junit.jupiter:junit-jupiter-engine")
    runtimeOnly("org.junit.platform:junit-platform-reporting")
    testImplementation("io.micrometer:micrometer-observation")
}
// probe 패키지는 EvidenceBlockWriterTest 가 Launcher 로만 실행하는 OTR 입력이다(의도된 실패 시험 포함).
tasks.named<Test>("test") { exclude("probe/**") }
// Windows cmd 는 한 줄 8191자 제한이다. 배포본 lib 의 jar 147개를 APP_HOME 절대 경로로 모두 펼치면 .bat 이 "입력 줄이 너무 깁니다"로
// 실행조차 못 한다(P15 ⑩ 실측). Windows 스크립트만 같은 lib 디렉터리의 JVM wildcard classpath 로 바꾼다(Linux sh 스크립트·jar 집합 불변).
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        windowsScript.writeText(windowsScript.readText().replace(Regex("(?m)^set CLASSPATH=.*$")) { "set CLASSPATH=%APP_HOME%\\lib\\*" })
    }
}
