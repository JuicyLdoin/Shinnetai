plugins {
    java
    `maven-publish`
}

group = "net.ldoin"
version = "1.5.3"

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
        version = parent!!.version
    }

    apply("plugin" to "java")

    repositories {
        mavenCentral()
    }
}