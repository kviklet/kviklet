import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    kotlin("kapt") version "2.3.21" // needed for query-dsl
}

kapt {
    javacOptions {
        option("querydsl.entityAccessors", "true")
    }
    arguments {
        arg("plugin", "com.querydsl.apt.jpa.JPAAnnotationProcessor")
    }

    correctErrorTypes = true
}

// Liquibase 5 moved to the Functional Source License; stay on the last Apache-2.0 release.
extra["liquibase.version"] = "4.33.0"

group = "com.example"
version = "0.0.1-SNAPSHOT"
val queryDslVersion = "5.1.0"
val testcontainersVersion = "2.0.5"
java.sourceCompatibility = JavaVersion.VERSION_21

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    maven { url = uri("https://repo.spring.io/milestone") }
    mavenCentral()
    maven { url = uri("https://build.shibboleth.net/maven/releases/") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.security:spring-security-ldap")
    implementation("org.springframework.ldap:spring-ldap-core")
    implementation("org.springframework.security:spring-security-saml2-service-provider")
    implementation("org.springframework.boot:spring-boot-devtools")

    implementation("org.springframework.security:spring-security-acl")
    implementation("org.springframework.security:spring-security-config")
    implementation("org.springframework:spring-context-support")
    implementation("com.github.jsqlparser:jsqlparser:4.9")
    implementation("io.kubernetes:client-java:26.0.0")
    implementation("software.amazon.awssdk:rds:2.30.37")
    implementation("software.amazon.awssdk:sts:2.30.37")

    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("org.postgresql:postgresql:42.7.3")

    runtimeOnly("com.h2database:h2")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-mysql:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-mssqlserver:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-mongodb:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-mariadb:$testcontainersVersion")
    testImplementation("org.jsoup:jsoup:1.16.1")

    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
    testImplementation("io.mockk:mockk:1.13.4")
    testImplementation("com.ninja-squad:springmockk:5.0.1")
    testImplementation("net.sourceforge.htmlunit:htmlunit:2.70.0")
    // Must match the okhttp version pulled in transitively (kubernetes client-java -> okhttp 5.x);
    // mockwebserver 4.x crashes with NoClassDefFoundError against okhttp 5 at runtime.
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")

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
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
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
