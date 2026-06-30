plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    // SDK 를 로컬 jar 로 의존. jar 에 POM 이 없어 transitive 가 안 따라오므로
    // SDK 가 api() 로 노출하던 의존을 여기서 명시한다(버전은 Boot BOM / 직접 핀).
    implementation(files("$rootDir/libs/sdk-java-1.0.0.jar"))
    implementation("org.springframework:spring-web")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")
    implementation("org.slf4j:slf4j-api")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.session:spring-session-core")

    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    runtimeOnly("org.codehaus.janino:janino:3.1.12")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // RpAppSmokeIT 용 webauthn4j-test (ClientPlatform + PackedAuthenticator).
    testImplementation("com.webauthn4j:webauthn4j-test:0.31.5.RELEASE")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("rp-app.jar")
    // 실행 가능한 jar 를 루트 deploy/ 에 모아 배포 편의를 높인다.
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("deploy"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Java 17 UUID reflection access (passkey-app 와 동일 패턴)
    jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
}
