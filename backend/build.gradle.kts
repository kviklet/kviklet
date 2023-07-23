import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
	id("org.springframework.boot") version "2.7.5"
	id("io.spring.dependency-management") version "1.0.15.RELEASE"
	kotlin("jvm") version "1.6.21"
	kotlin("plugin.spring") version "1.6.21"
	kotlin("plugin.jpa") version "1.6.21"
	id("org.springframework.experimental.aot") version "0.12.1"

    kotlin("kapt") version "1.8.10" // needed for query-dsl
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
val queryDslVersion = "5.0.0"
val testcontainersVersion = "1.18.3"
java.sourceCompatibility = JavaVersion.VERSION_17

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
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("org.springdoc:springdoc-openapi-ui:1.6.13")
    implementation("org.liquibase:liquibase-core")
    implementation("javax.validation:validation-api")
	runtimeOnly("org.springdoc:springdoc-openapi-kotlin:1.6.13")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("com.h2database:h2")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.0.1")
    testImplementation("org.testcontainers:mysql:${testcontainersVersion}")
    testImplementation("org.testcontainers:postgresql:${testcontainersVersion}")

    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
    testImplementation("io.mockk:mockk:1.13.4")


    //querydsl
    implementation("com.querydsl:querydsl-jpa:${queryDslVersion}")
    kapt("com.querydsl:querydsl-apt:${queryDslVersion}:jpa" )
    implementation(group="javax.inject", name="javax.inject", version="1")

    //db drivers
    implementation("org.mariadb.jdbc:mariadb-java-client:3.1.4")
    implementation("com.mysql:mysql-connector-j:8.0.33")

}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "17"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<BootBuildImage> {
	builder = "paketobuildpacks/builder:tiny"
	environment = mapOf("BP_NATIVE_IMAGE" to "true")
}

tasks.withType<BootRun> {
    systemProperty("spring.profiles.active", System.getProperty("spring.profiles.active"))
}
