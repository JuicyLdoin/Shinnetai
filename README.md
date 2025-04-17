# Shinnetai

**Shinnetai** is a lightweight, high-performance Java networking library built on top of **Java 21 Virtual Threads**. It is designed for low-latency client-server communication with minimal overhead.

### Key Features
*   **Virtual Threads IO**: Efficient handling of thousands of concurrent connections using Java 21's lightweight threads.
*   **Smart Protocol**: Custom packet-based architecture with built-in Handshake validation and Application-level KeepAlive (Ping/Pong).
*   **Optimized Buffers**: SmartByteBuf provides fast serialization with standard UTF-8 support and zero-allocation primitive arrays.
*   **Robust Architecture**: Integrated exception pipeline for production-grade reliability and monitoring.
*   **Zero Dependencies**: Pure Java implementation without external bloat.

### Requirements
*   Java 21 or higher.

## Getting Started

To use Shinnetai, you need to add the library dependencies to your project.

### Gradle

Add the following to your `build.gradle` file:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.JuicyLdoin.Shinnetai:shinnetai-buffered:1.0.0'
    implementation 'com.github.JuicyLdoin.Shinnetai:shinnetai-proxy:1.0.0'
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
        <version>1.0.0</version>
    </dependency>
    <dependency>
        <groupId>com.github.JuicyLdoin.Shinnetai</groupId>
        <artifactId>shinnetai-proxy</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

*Detailed documentation will be available in the Wiki.*
