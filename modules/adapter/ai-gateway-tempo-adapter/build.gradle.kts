plugins { `java-library` }
dependencies {
    implementation(project(":ai-gateway-core"))
    implementation(project(":adapter:ai-gateway-source-support"))
    testImplementation(testFixtures(project(":adapter:ai-gateway-source-support")))
}
