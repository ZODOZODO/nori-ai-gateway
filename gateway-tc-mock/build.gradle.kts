plugins { `java-library`; `java-test-fixtures` }
dependencies {
    implementation(project(":gateway-core"))
    implementation("org.springframework:spring-context")
    testFixturesApi(libs.nimbus.jose.jwt)
}
