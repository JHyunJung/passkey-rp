plugins {
    java
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.crosscert.passkey"
version = "0.0.1-SNAPSHOT"

repositories { mavenCentral() }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.14") }
    // webauthn4j 가 끌어오는 Jackson 3 계열이 jackson-annotations 2.20+ 를 요구.
    // Boot BOM 의 2.19.x 를 앞으로 override(2.x annotations 는 하위호환).
    dependencies { dependency("com.fasterxml.jackson.core:jackson-annotations:2.21") }
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")

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
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // RpAppSmokeIT 용 webauthn4j-test (ClientPlatform + PackedAuthenticator).
    testImplementation("com.webauthn4j:webauthn4j-test:0.31.5.RELEASE")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("rp-app.jar")
    // 실행 가능한 jar 를 deploy/ 에 모아 배포 편의를 높인다.
    destinationDirectory.set(layout.projectDirectory.dir("deploy"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Java 17 module system: Spring Data 등의 UUID 리플렉션 접근 허용.
    jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
}
