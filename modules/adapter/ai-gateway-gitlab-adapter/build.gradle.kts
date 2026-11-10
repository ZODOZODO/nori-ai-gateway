plugins { `java-library` }
dependencies {
    implementation(project(":ai-gateway-core"))
    implementation(project(":adapter:ai-gateway-source-support"))
    testImplementation(testFixtures(project(":adapter:ai-gateway-source-support")))
}

// 실제 Source 선언도 Connector 시험의 입력 지문에 포함합니다.
sourceSets.test {
    resources.srcDir(rootProject.file("modules/apps/ai-gateway-app/src/main/resources"))
    resources.include("config/sources/**")
}
