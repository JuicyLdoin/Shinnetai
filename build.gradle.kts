plugins {
    java
    `maven-publish`
}

group = "net.ldoin"
version = "1.4.0"

repositories {
    mavenCentral()
}

val publishModules = listOf(
    "shinnetai-buffered",
    "shinnetai-proxy"
)

val monoVersion = true

subprojects {
    group = "net.ldoin"
    if (monoVersion) {
        version = rootProject.version
    }

    apply(plugin = "java")

    repositories {
        mavenCentral()
    }
}