plugins {
    id("org.springframework.boot") version "3.3.1" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    kotlin("jvm") version "1.9.22" apply false
    kotlin("plugin.spring") version "1.9.22" apply false
    kotlin("plugin.jpa") version "1.9.22" apply false
}

allprojects {
    group = "com.ticketing"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
