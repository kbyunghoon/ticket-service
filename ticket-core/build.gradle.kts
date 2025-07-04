plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
    id("io.spring.dependency-management") version "1.1.6"
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.1")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(project(":ticket-common"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.data:spring-data-redis")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.redisson:redisson-spring-boot-starter:3.29.0")
    implementation("com.h2database:h2")
    implementation("mysql:mysql-connector-java:8.0.33")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("ch.qos.logback:logback-classic")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

configurations.all {
    resolutionStrategy {
        exclude(group = "org.slf4j", module = "slf4j-simple")

        eachDependency {
            if (requested.group == "org.slf4j") {
                if (requested.name == "slf4j-simple") {
                    useTarget("ch.qos.logback:logback-classic:1.4.14")
                    because("Avoid SLF4J multiple bindings conflict")
                }
            }
        }
    }
}
