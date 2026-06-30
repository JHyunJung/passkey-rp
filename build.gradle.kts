plugins {
    java
    id("org.springframework.boot") version "3.5.14" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "com.crosscert.passkey"
    version = "0.0.1-SNAPSHOT"
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    }

    extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports { mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.14") }
        // webauthn4j 가 끌어오는 Jackson 3 계열이 jackson-annotations 2.20+ 를 요구.
        // Boot BOM 의 2.19.x 를 앞으로 override(2.x annotations 는 하위호환).
        dependencies { dependency("com.fasterxml.jackson.core:jackson-annotations:2.21") }
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok:1.18.34")
        "annotationProcessor"("org.projectlombok:lombok:1.18.34")
        "testCompileOnly"("org.projectlombok:lombok:1.18.34")
        "testAnnotationProcessor"("org.projectlombok:lombok:1.18.34")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // Java 17 module system: Spring Data 등의 UUID 리플렉션 접근 허용.
        jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
    }
}
