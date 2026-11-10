plugins { `java-library` }
dependencies {
    api(libs.json.schema.validator)
    implementation(libs.json.canonicalization)
}

// 실제 앱 계약 데이터를 시험 classpath에 포함해 데이터 변경도 시험 입력 지문에 결속합니다.
sourceSets.test {
    resources.srcDir(rootProject.file("modules/apps/ai-gateway-app/src/main/resources"))
    resources.include("manifests/**", "workflows/**")
}
