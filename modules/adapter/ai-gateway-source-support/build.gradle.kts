plugins { `java-library`; `java-test-fixtures` }
// loki·tempo·gitlab adapter 가 함께 쓰는 Source 공용 기반(전송기·Source 인가 확인·관측·TC 앱 목록). Bean 을 만들지 않는 라이브러리이며 조립은 app 이 한다.
dependencies {
    api(project(":ai-gateway-core"))
    api("org.springframework:spring-web")
    api("io.micrometer:micrometer-observation")
    api("io.micrometer:micrometer-core")
    api("com.fasterxml.jackson.core:jackson-databind")
    testFixturesImplementation(platform(libs.spring.boot.bom))
    testFixturesImplementation("ch.qos.logback:logback-classic")
    testFixturesImplementation(libs.assertj)
    testImplementation(testFixtures(project(":adapter:ai-gateway-source-support")))
}
