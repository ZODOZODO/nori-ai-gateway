plugins { `java-library` }
dependencies {
    implementation(project(":ai-gateway-core"))
    implementation(project(":adapter:ai-gateway-source-support"))
    implementation("org.springframework.boot:spring-boot")
    testImplementation(testFixtures(project(":adapter:ai-gateway-source-support")))
}
