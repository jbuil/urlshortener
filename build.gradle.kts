import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot") version "2.7.3" apply false
    id("io.spring.dependency-management") version "1.0.13.RELEASE" apply false
    kotlin("jvm") version "1.7.10" apply false
    kotlin("plugin.spring") version "1.7.10" apply false
    kotlin("plugin.jpa") version "1.7.10" apply false
}

group = "es.unizar"
version = "0.2022.1-SNAPSHOT"

var mockitoVersion = "4.0.0"
var bootstrapVersion = "3.4.0"
var jqueryVersion = "3.6.1"
var guavaVersion = "31.1-jre"
var commonsValidatorVersion = "1.6"
var mockkVersion = "1.13.3"

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    repositories {
        mavenCentral()
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "11"
        }
    }
    tasks.withType<Test> {
        useJUnitPlatform()
    }
    dependencies {
        "implementation"("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:latest.release")

    }
}

project(":core") {
    dependencies {
       "implementation"("io.github.g0dkar:qrcode-kotlin-jvm:3.2.0")
       "implementation"("org.springframework:spring-core:5.3.22")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:latest.release")
    }
}

project(":repositories") {
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    dependencies {
        "implementation"(project(":core"))
        "implementation"("org.springframework.boot:spring-boot-starter-data-jpa")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:latest.release")

    }
    tasks.getByName<BootJar>("bootJar") {
        enabled = false
    }
}

project(":delivery") {
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    dependencies {
        "implementation"(project(":core"))
        "implementation"("org.springframework.boot:spring-boot-starter-web")
        "implementation"("org.springframework.boot:spring-boot-starter-hateoas")
        "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin")
        "implementation"("com.opencsv:opencsv:4.6")
        "implementation"("commons-validator:commons-validator:$commonsValidatorVersion")
        "implementation"("com.google.guava:guava:$guavaVersion")
        "implementation"("io.github.g0dkar:qrcode-kotlin-jvm:3.2.0")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:latest.release")
        "implementation"("ru.chermenin:kotlin-user-agents:0.2.2")
        "implementation"("com.google.api-client:google-api-client:latest.release")
        "implementation" ("com.google.apis:google-api-services-safebrowsing:v4-rev20190923-1.30.3")
        "implementation"("org.springframework.amqp:spring-rabbit:2.4.0")
        "implementation" ("io.springfox:springfox-swagger2:2.9.2")
        "implementation"("org.springdoc:springdoc-openapi-ui:1.6.4")
        "implementation" ("io.springfox:springfox-swagger-ui:2.9.2")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testImplementation"("org.mockito.kotlin:mockito-kotlin:$mockitoVersion")
        "implementation"("org.springframework.boot:spring-boot-starter-thymeleaf")
        "testImplementation"( "org.jetbrains.kotlinx:kotlinx-coroutines-test:latest.release")
        "implementation"("org.springframework.boot:spring-boot-starter-cache")
        "implementation"("com.google.zxing:core:3.4.0")
        "testImplementation"("io.mockk:mockk:${mockkVersion}")
        "implementation"("org.springframework.boot:spring-boot-starter-webflux")
    }
    tasks.getByName<BootJar>("bootJar") {
        enabled = false
    }
}

project(":app") {
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    dependencies {
        "implementation"(project(":core"))
        "implementation"(project(":delivery"))
        "implementation"(project(":repositories"))
        "implementation"( "org.webjars:bootstrap:$bootstrapVersion")
        "implementation"("org.webjars:jquery:$jqueryVersion")
        "runtimeOnly"("org.hsqldb:hsqldb")
        "implementation"("org.springframework.boot:spring-boot-starter")
        "implementation"("org.webjars:bootstrap:$bootstrapVersion")
        "implementation"("org.webjars:jquery:$jqueryVersion")
        "implementation"("org.springframework.amqp:spring-rabbit:2.4.0")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:latest.release")

        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testImplementation"("org.springframework.boot:spring-boot-starter-web")
        "testImplementation"("org.springframework.boot:spring-boot-starter-jdbc")
        "testImplementation"("org.mockito.kotlin:mockito-kotlin:$mockitoVersion")
        "testImplementation"("com.fasterxml.jackson.module:jackson-module-kotlin")

        "testImplementation"("org.apache.httpcomponents:httpclient")
    }
}
