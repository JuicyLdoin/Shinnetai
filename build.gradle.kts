plugins {
    java
    `maven-publish`
}

group = "net.ldoin"
version = "1.5.4"

repositories {
    mavenCentral()
}

val monoVersion = true
subprojects {
    group = "net.ldoin"
    if (monoVersion) {
        version = parent!!.version
    }

    apply("plugin" to "java")

    repositories {
        mavenCentral()
    }
}