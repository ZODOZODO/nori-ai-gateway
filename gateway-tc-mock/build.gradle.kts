plugins { `java-library`; `java-test-fixtures` }
dependencies {
    implementation(project(":gateway-core"))
    implementation("org.springframework:spring-context")
    testFixturesApi(libs.nimbus.jose.jwt)
}

// 실제 앱 계약 파일을 시험 입력에 결속해 Mock과 정본의 차이를 확인합니다.
sourceSets.test {
    resources.srcDir(rootProject.file("gateway-app/src/main/resources"))
    resources.include("manifests/**", "config/source-permissions/**", "pdg-vectors-v1.json")
}
