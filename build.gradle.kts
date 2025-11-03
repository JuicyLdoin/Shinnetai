plugins {
    java
    `maven-publish`
}

group = "net.ldoin"
version = "1.6.2"

repositories {
    mavenCentral()
}

val monoVersion = true

subprojects {
    group = "net.ldoin"
    if (monoVersion) {
        version = parent!!.version
    }

    apply(plugin = "java")
    apply(plugin = "maven-publish")

    repositories {
        mavenCentral()
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()
            }
        }

        repositories {
            mavenLocal()
        }
    }
}