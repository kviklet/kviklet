import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    kotlin("plugin.jpa") version "1.9.24"
    kotlin("kapt") version "1.9.24" // needed for query-dsl
}

kapt {
    javacOptions {
        option("querydsl.entityAccessors", true)
    }
    arguments {
        arg("plugin", "com.querydsl.apt.jpa.JPAAnnotationProcessor")
    }

    correctErrorTypes = true
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
val queryDslVersion = "5.0.0"
val testcontainersVersion = "1.18.3"
java.sourceCompatibility = JavaVersion.VERSION_21

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    maven { url = uri("https://repo.spring.io/milestone") }
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.session:spring-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.security:spring-security-ldap")
    implementation("org.springframework.ldap:spring-ldap-core")
    implementation("org.springframework.boot:spring-boot-devtools")

    implementation("org.springframework.security:spring-security-acl")
    implementation("org.springframework.security:spring-security-config")
    implementation("org.springframework:spring-context-support")
    implementation("com.github.jsqlparser:jsqlparser:4.9")
    implementation("io.kubernetes:client-java:20.0.1")
    implementation("software.amazon.awssdk:rds:2.30.6")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")
    implementation("org.liquibase:liquibase-core")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("org.postgresql:postgresql:42.7.3")

    runtimeOnly("com.h2database:h2")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.0.1")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:mysql:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:mssqlserver:$testcontainersVersion")
    testImplementation("org.testcontainers:mongodb:$testcontainersVersion")
    testImplementation("org.testcontainers:mariadb:$testcontainersVersion")

    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
    testImplementation("io.mockk:mockk:1.13.4")
    testImplementation("com.ninja-squad:springmockk:4.0.2")

    // querydsl
    implementation("com.querydsl:querydsl-core:$queryDslVersion")
    implementation("com.querydsl:querydsl-jpa:$queryDslVersion:jakarta")
    // annotationProcessor("com.querydsl:querydsl-apt:${queryDslVersion}:jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api:3.1.0")
    kapt("com.querydsl:querydsl-apt:$queryDslVersion:jakarta")

    // implementation(group="com.querydsl", name="querydsl-jpa", version=queryDslVersion, classifier="jakarta")
    // kapt("com.querydsl:querydsl-apt:${queryDslVersion}:jpa")
    implementation(group = "javax.inject", name = "javax.inject", version = "1")

    // db drivers
    implementation("org.mariadb.jdbc:mariadb-java-client:3.4.1")
    implementation("com.mysql:mysql-connector-j:8.3.0")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.6.1.jre11")
    implementation("org.mongodb:mongodb-driver-sync:5.1.2")
    implementation("org.mongodb:mongodb-driver-core:5.1.2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// tasks.withType<BootBuildImage> {
// 	builder = "paketobuildpacks/builder:tiny"
// 	environment = mapOf("BP_NATIVE_IMAGE" to "true")
// }

tasks.withType<BootRun> {
    systemProperty("spring.profiles.active", System.getProperty("spring.profiles.active"))
}
