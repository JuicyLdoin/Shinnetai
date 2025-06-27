# Shinnetai Network Library - README

Shinnetai is a high-performance Java network library that provides easy-to-use tools for working with client-server
communication. It offers advanced features like custom packet creation, error handling, clustering, packet-streams support and asynchronous communication.

## Getting Started

To use Shinnetai, you need to add the library dependencies to your project. You can do this in both Maven and Gradle.

### Gradle

Add the following to your `build.gradle` file:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.JuicyLdoin.Shinnetai:shinnetai-buffered:1.6.0'
    implementation 'com.github.JuicyLdoin.Shinnetai:shinnetai-proxy:1.6.0'
}
```

### Maven

Add the following to your `pom.xml`:

```xml

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
<dependency>
    <groupId>com.github.JuicyLdoin.Shinnetai</groupId>
    <artifactId>shinnetai-buffered</artifactId>
    <version>1.6.0</version>
</dependency>
<dependency>
    <groupId>com.github.JuicyLdoin.Shinnetai</groupId>
    <artifactId>shinnetai-proxy</artifactId>
    <version>1.6.0</version>
</dependency>
</dependencies>
```

## Full documentation will be added later