plugins { `java-library` }
dependencies {
    implementation(project(":gateway-core"))
    implementation(project(":gateway-tc-mock"))
    implementation(project(":gateway-integration"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("io.micrometer:micrometer-observation")
}
