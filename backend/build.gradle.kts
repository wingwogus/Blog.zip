plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    kotlin("plugin.jpa") version "2.4.10"
    id("com.google.devtools.ksp") version "2.3.11"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.blogzip"
version = "0.0.1-SNAPSHOT"
description = "Blog.zip backend"

// docs/decisions/007-persistence-stack.md 참고.
// QueryDSL 7.6의 부모 POM이 Boot 4.1.0 / Hibernate 7.4.5 / Kotlin 2.4.10 / KSP 2.3.11로
// 선언되어 있어 그 조합에 맞춘다.
val querydslVersion = "7.6"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Boot 4는 Jackson 3(`tools.jackson`)을 쓴다. Jackson 2의 `com.fasterxml.jackson`이 아니다.
    // 버전은 Boot BOM이 관리한다.
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")

    // 동적 쿼리. docs/decisions/007-persistence-stack.md
    implementation("io.github.openfeign.querydsl:querydsl-jpa:$querydslVersion")
    ksp("io.github.openfeign.querydsl:querydsl-ksp-codegen:$querydslVersion")

    // 식별자. docs/decisions/006-id-strategy.md
    implementation("com.github.f4b6a3:ulid-creator:5.2.4")

    // 단기 상태(요청 제한, lookupToken). docs/decisions/009-ephemeral-state.md
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")

    // 인증. docs/decisions/002-auth-strategy.md
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // API 문서. docs/decisions/010-api-response-contract.md
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

    // 스키마. docs/decisions/008-schema-migration.md
    // Boot 4는 autoconfiguration이 모듈화되어 flyway-core만 넣으면 자동 설정이 도지 않는다.
    // starter를 사용해야 FlywayAutoConfiguration이 들어온다.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Boot 4 BOM이 testcontainers 버전을 관리하지 않아 BOM을 직접 가져온다.
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // 단일 모듈이므로 의존 방향을 컴파일러가 막지 못한다.
    // docs/decisions/011-backend-module-structure.md
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
