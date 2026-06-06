# Shinnetai

**Shinnetai** is a lightweight, high-performance Java networking library built on top of **Java 21 Virtual Threads**. It is designed for low-latency client-server communication with minimal overhead.

### Key Features
*   **Virtual Threads IO** — Efficient handling of thousands of concurrent connections using Java 21's lightweight threads.
*   **Smart Protocol** — Custom packet-based architecture with built-in Handshake validation and Application-level KeepAlive (Ping/Pong).
*   **Optimized Buffers** — `SmartByteBuf` provides fast serialization with varint encoding, UTF-8 strings, and compact primitive arrays.
*   **Pipeline** — Per-connection interceptor chain for logging, transformation, and metrics at any stage of packet processing.
*   **Streams** — Experimental multiplexed logical channels over a single connection for multi-flow communication.
*   **ACK-based Delivery** — Optional in-memory reliable delivery with deduplication, retransmit and offline buffering.
*   **Resilience** — Circuit breaker, retry policy, and connection pool support.
*   **Security** — TLS hooks, IP filtering with CIDR, per-connection rate limiting, session token validation, and packet authorization hooks.
*   **Zero Dependencies** — Core modules are pure Java with no required external libraries.

### Requirements
*   Java 21 or higher.

---

## Getting Started

Add the following to your build file. Use `shinnetai-buffered` for buffer utilities only, or `shinnetai-proxy` for the full networking stack (it already depends on `shinnetai-buffered`).

### Gradle

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

### Quick start

**Server:**
```java
ShinnetaiServer<?> server = new ShinnetaiServer<>(
    ServerOptions.builder(9090)
        .keepAlive(true)
        .onConnect(conn -> System.out.println("connected: " + conn.getConnectionId()))
        .build()
);
server.getOptions().getPacketHandlerRegistry()
    .on(MyPacket.class, (packet, ctx) -> ctx.sendPacket(new MyResponse()));
server.start();
```

**Client:**
```java
ShinnetaiClient client = new ShinnetaiClient(
    ClientOptions.builder("localhost", 9090).build()
);
client.start();
client.sendPacket(new MyPacket("hello"));
```

---

## Modules

| Module | Purpose |
|---|---|
| `shinnetai-buffered` | Low-level byte buffer and serialization utilities |
| `shinnetai-proxy` | Server, client, protocol, pipeline, delivery, security |
| `shinnetai-serializers` | Optional Jackson-based serializers (JSON, CBOR, Smile, MessagePack, FlexBuffers) |

---

## Architecture Overview

```
ShinnetaiServer / ShinnetaiClient
    └── ShinnetaiIOWorker          (reader loop + writer loop on Virtual Threads)
            ├── ShinnetaiPipeline  (interceptor chain)
            ├── ShinnetaiStream[]  (multiplexed logical channels)
            ├── PacketOutbox       (offline delivery buffer)
            └── PacketDeduplicator (dedup window for reliable delivery)
```

Every connection goes through a single `ShinnetaiIOWorker`. The worker owns the read loop, write queue, pipeline, and streams. `ShinnetaiConnection` is a server-side `IOWorker` that also holds a reference to the server. `ShinnetaiClient` is the client-side counterpart.

---

## shinnetai-buffered

### `ByteBuf`
Base read/write buffer backed by a `byte[]`. Supports primitives (`int`, `long`, `short`, `float`, `double`, `char`, `boolean`), strings (length-prefixed), and raw byte arrays. Automatically expands on write.

### `SmartByteBuf` / `ReadOnlySmartByteBuf` / `WriteOnlySmartByteBuf`
Extends `ByteBuf` with protocol-level encoding:
- **VarInt / VarLong** — compact 1–5 / 1–10 byte integers
- **Strings** — UTF-8, length encoded as VarInt
- **Collections** — size-prefixed via `writeCollection` / `readCollection`
- **Boolean arrays** — bit-packed, 7 bits per byte
- **ID arrays** — BitSet-based delta encoding for sparse integer sets
- **UUID** — two `long` values

`ReadOnlySmartByteBuf` disables all write methods. `WriteOnlySmartByteBuf` disables all read methods.

### `IOUtil`
Static helpers for reading/writing primitives and VarInt/VarLong directly from `ReadableByteChannel` / `WritableByteChannel` or a `ByteReader` / `ByteWriter` lambda.

### `BufferedSerializer`
Annotation-driven binary serializer. Annotate a class with `@Buffered` and its fields will be auto-serialized to/from a `SmartByteBuf`.

### Bitwise serialization (`bitwise/`)

A bit-precise serialization layer for packing multiple values into a minimal number of bytes. Useful for compact protocol headers, game state snapshots, or any structure where byte-aligned serialization wastes space.

#### `BitwiseTrain`
Low-level bit-stream state machine that reads and writes arbitrary-width values into a `byte[]`. All entry types delegate to `writeBits(long value, int bits)` / `readBits(int bits)`.

#### `BitwiseSerializer`
Holds an ordered list of `BitwiseSerializerEntry` objects. `pack()` serializes all entries into the tightest possible `byte[]`; `unpack(byte[])` reads them back in the same order.

```java
// Write side
BitwiseSerializerNumberEntry channelId = new BitwiseSerializerNumberEntry(22, 5); // value=22, 5 bits
BitwiseSerializerBooleanEntry reliable = new BitwiseSerializerBooleanEntry(true);
BitwiseSerializerEnumEntry<PacketType> type = new BitwiseSerializerEnumEntry<>(PacketType.DATA);

BitwiseSerializer ser = new BitwiseSerializer();
ser.addEntry(channelId).addEntry(reliable).addEntry(type);
byte[] bytes = ser.pack();

// Read side
BitwiseSerializerNumberEntry rChannel = new BitwiseSerializerNumberEntry(5);
BitwiseSerializerBooleanEntry rReliable = new BitwiseSerializerBooleanEntry();
BitwiseSerializerEnumEntry<PacketType> rType = new BitwiseSerializerEnumEntry<>(PacketType.class);

BitwiseSerializer deser = new BitwiseSerializer();
deser.addEntry(rChannel).addEntry(rReliable).addEntry(rType);
deser.unpack(bytes);

long channel = rChannel.longValue();   // 22
boolean rel   = rReliable.getValue();  // true
PacketType pt = rType.getValue();      // PacketType.DATA
```

#### Entry types

| Class | Bits | Description |
|---|---|---|
| `BitwiseSerializerNumberEntry` | user-defined | Any `Number` (int, long, float, double, …). Bit width set via constructor or `.bits(n)`. |
| `BitwiseSerializerBooleanEntry` | 1 | A single boolean flag. |
| `BitwiseSerializerEnumEntry<E>` | auto | Enum stored as its ordinal; bit width = ⌈log₂(count)⌉. |
| `BitwiseSerializerFlagsEntry` | N (one per flag) | N named boolean flags packed into N bits. Values retrieved by name after `unpack`. |

#### Fluent builder

`BitwiseSerializer.builder()` provides a chainable API. Entry type is inferred from which `value()` overload is called:

```java
byte[] bytes = BitwiseSerializer.builder()
    .entry()
        .name("type")
        .value(PacketType.DATA)          // → BitwiseSerializerEnumEntry
    .entry()
        .name("channelId")
        .bits(5)
        .value(22)                       // → BitwiseSerializerNumberEntry
    .entry()
        .name("reliable")
        .value(true)                     // → BitwiseSerializerBooleanEntry
    .entry()
        .flags("urgent", "compressed")   // → BitwiseSerializerFlagsEntry
        .flag("urgent", true)
    .build()
    .pack();
```

A pre-built `BitwiseSerializerEntry` can also be injected directly:

```java
BitwiseSerializer.builder()
    .entry(myCustomEntry)               // inject a pre-built entry
    .entry()
        .bits(4).value(9)
    .build();
```

Or inside the chain via `.custom(entry)`:

```java
BitwiseSerializer.builder()
    .entry().custom(myCustomEntry)
    .entry().bits(4).value(9).build();
```

---

## shinnetai-proxy

### Packets (`packet/`)

#### `AbstractPacket<C, S>`
Base class for all user-defined packets. Subclasses implement:
- `read(ReadOnlySmartByteBuf)` — deserialize from buffer
- `write(WriteOnlySmartByteBuf)` — serialize to buffer
- `handleClient()` / `handleServer()` — logic executed after dispatch
- `response()` — optional automatic response packet
- `schemaVersion()` — per-packet schema marker for compatibility-aware serializers or validators
- `rateLimitCost()` — optional extra cost for weighted rate limiting
- `getHandleSide()` — restrict handling to `CLIENT`, `SERVER`, or `MULTIPLE`

Annotate the class with `@ShinnetaiPacket(id = N)` to register it in `PacketRegistry`.

#### `WrappedPacket`
Envelope around `AbstractPacket` that carries metadata:
- `PacketOptions` flags (`DELIVERY_TRACKED`, `DELIVERY_ACK`, `IN_STREAM`, ...)
- `packetId` — global ID for reliable delivery tracking
- `streamId` — target stream if the packet is routed through a stream
- `responseOptions` — waiter configuration for request/response
- `serializer` override — per-packet serializer if different from connection default

Use `WrappedPacket.of(packet)` or the builder `WrappedPacket.builder(packet).withOption(...).build()`.

#### `PacketRegistry`
Maps packet classes to integer IDs. The static `PacketRegistry.getCommons()` pre-registers all built-in system packets. For production, prefer explicit registration and validation:

```java
PacketRegistry registry = PacketRegistry.empty()
    .withCommons()
    .register(MyPacket.class)
    .validateOrThrow();
```

`new PacketRegistry("com.example.packets")` remains available as a convenience reflection-scanning path.

Built-in (system) packet IDs are negative:

| ID | Class | Direction |
|---|---|---|
| -1 | `HandshakePacket` | both |
| -2 | `PingPacket` | server → client |
| -3 | `PongPacket` | client → server |
| -4 | `DisconnectPacket` | both |
| -5 | `ServerDisablePacket` | server → client |
| -6 | `ExceptionPacket` | both |
| -7 | `EmptyResponsePacket` | both |
| -8 | `DeliveryAckPacket` | both |
| -9 | `StreamCommitPacket` | both |
| -10 | `StreamCommitAckPacket` | both |

### Worker (`worker/`)

#### `ShinnetaiWorkerContext<S>`
Abstract base shared by `ShinnetaiIOWorker` and `ShinnetaiStream`. Holds the NIO channels, packet registry, write lock, response waiter, statistics, and pipeline reference. Provides `sendPacket`, `handlePacket`, `sendException`, `sendAndWaitForResponse`, `sendAsyncWithResponse`, `tryConsumeRateLimit`, and `getAuthenticationContext`.

#### `ShinnetaiIOWorker<S>`
Extends `ShinnetaiWorkerContext`. Manages the full connection lifecycle:
- **Reader loop** — reads length-prefixed frames, decompresses, rate-limits, deserializes, dispatches to pipeline + handler
- **Writer loop** — drains `outQueue` (`ArrayBlockingQueue`), serializes, compresses, writes frames
- **KeepAlive loop** — sends `PingPacket` and closes on timeout
- Handles handshake negotiation, feature flags, delivery tracking and ACK
- Exposes lifecycle state via `getState()` and last close cause via `getLastCloseReason()`

`QueueOverflowStrategy` controls behaviour when `outQueue` is full: `DROP`, `WARN`, `THROW`, `BLOCK`, `DROP_OLDEST`, `DROP_NEWEST`.

`tryAddPacket(...)` returns `EnqueueResult` (`ACCEPTED`, `DROPPED`, `REJECTED_FULL`, `TIMED_OUT`, `CLOSED`) so callers can distinguish overload from a closed connection.

#### `WorkerOptions` / `ServerOptions` / `ClientOptions`
All configuration is passed via immutable options objects built with a fluent builder.

Key `WorkerOptions` settings:

| Option | Default | Description |
|---|---|---|
| `maxPacketSize` | 64 KB | Maximum allowed packet size (compressed and uncompressed) |
| `readTimeout` | 30 000 ms | Connection timeout |
| `keepAlive` | true | Enable Ping/Pong heartbeat |
| `compressionThreshold` | 0 (off) | Compress frames larger than N bytes using Deflate |
| `reliableDelivery` | true | Enable ACK-based delivery tracking |
| `maxQueueSize` | 10 000 | Outbound packet queue capacity |
| `virtualThreads` | true | Use Virtual Threads for IO loops |
| `handshakeTimestampWindowMs` | 30 000 ms | Replay protection window (0 = disabled) |
| `maxHandshakeDurationMs` | 10 000 ms | Maximum time a transport may remain pending before handshake |
| `maxPendingHandshakes` | 1024 | Pending handshake cap (0 = unlimited) |
| `queueWatermarks` | 0.8 / 0.5 | Queue overload/recovery thresholds |
| `onOverloaded` / `onRecovered` | null | Queue watermark callbacks |
| `onQueueTimeout` | null | Callback when blocking enqueue times out |
| `packetSerializer` | binary | Default packet serializer |
| `packetAuthorizer` | allow all | Hook called before application packet handlers |
| `rateLimiter` | 20k packets/s, 64 MB/s | Per-worker inbound token bucket; set `null` to disable |
| `rateLimitCostResolver` | `AbstractPacket::rateLimitCost` | Resolves extra per-packet rate limit cost |
| `requireTlsForSessionTokens` | false | Reject session-token configuration unless TLS is enabled |

### Pipeline (`worker/pipeline/`)

A pipeline is an ordered list of handler units executed at four hook points:

```
  inbound packet
       │
  BEFORE_HANDLE  ← interceptors run here (e.g. traffic logger)
       │
  handlePacket() ← actual dispatch to AbstractPacket / PacketHandlerRegistry
       │
  AFTER_HANDLE   ← interceptors run here
       │
  outbound packet (write)
       │
  BEFORE_SEND    ← interceptors run here
       │
  sendPacket()   ← actual write to channel
       │
  AFTER_SEND     ← interceptors run here
```

#### `ShinnetaiPipeline`
Holds a `Map<ShinnetaiPipelineHandleType, List<ShinnetaiPipelineHandlerUnit>>`. Add handlers with `addFirst`, `addLast`, or `add(type, handler, index)`. Can also override the serializer for the connection with `setSerializer(...)`.

#### `ShinnetaiPipelineHandler` / `ShinnetaiPipelineHandlerUnit`
A `ShinnetaiPipelineHandlerUnit` wraps a `ShinnetaiPipelineHandler` (functional interface). Use `SingleHandlerUnit` for inline lambdas or `ParallelHandlerUnit` with `ParallelPipelineHandlerFactory` to execute multiple handlers concurrently.

**Assigning a pipeline:**
```java
ShinnetaiPipeline pipeline = new ShinnetaiPipeline()
    .addLast(ShinnetaiPipelineHandleType.BEFORE_HANDLE,
        new SingleHandlerUnit((ctx, packet) -> System.out.println("IN: " + packet)));

WorkerOptions options = WorkerOptions.builder().pipeline(pipeline).build();
```

### Streams (`stream/`)

Streams are **experimental multiplexed logical channels** over one physical connection. Each stream has its own ID, lifecycle, and optionally its own pipeline and serializer.

```
ShinnetaiIOWorker
    ├── ShinnetaiInStream (id=1)  ← receives packets of a given type/filter
    ├── ShinnetaiInStream (id=2)
    └── ShinnetaiOutStream (id=3) ← sends packets through a dedicated queue
```

#### `ShinnetaiInStream`
A buffered input stream. Incoming packets are pushed into a `BlockingQueue` and processed by the stream's own run loop. Use `canAccept()` to implement packet filtering.

#### `ShinnetaiOutStream`
A buffered output stream with its own send queue. Packets enqueued via `send(packet, onSent)` are flushed through the worker's channel.

**Opening a stream:**
```java
int streamId = worker.openStream(new ShinnetaiInStream(worker, ShinnetaiStreamOptions.builder().build()));
// close later
worker.closeStream(streamId);
```

Opened streams are automatically registered against inbound packets with `PacketOptions.IN_STREAM` set and matching `streamId`.

#### Stream Commit Guarantee

When a sender needs confirmation that the receiver has processed every packet in a stream and closed it, it can enable the commit guarantee on `ShinnetaiStreamOptions`.

**How it works:**
1. The sender (`ShinnetaiOutStream`) sends all packets, then calls `commit()`, which sends a `StreamCommitPacket` to the remote side.
2. The receiver (`ShinnetaiInStream`) drains its queue, and once all packets are processed, replies with a `StreamCommitAckPacket`.
3. The sender's `CompletableFuture<StreamCommitResult>` is completed with `SUCCESS`, `TIMEOUT`, or `FAILED`.

The flow relies on TCP in-order delivery: the `StreamCommitPacket` always arrives after all data packets, so the receiver sends the ack only after processing everything.

**Auto-commit (limited stream):** If `packetsAmount` is set, `commit()` is called automatically when the run loop finishes.

**Manual commit (unlimited stream):** Call `outStream.commit()` explicitly when done sending.

```java
// Sender side
ShinnetaiOutStream out = new ShinnetaiOutStream(worker,
    ShinnetaiStreamOptions.builder(worker)
        .packetsAmount(3)
        .commitGuarantee(true)
        .commitTimeoutMs(10_000)
        .onCommit(result -> {
            if (result.isSuccess()) {
                System.out.println("Stream committed!");
            } else {
                System.err.println("Commit failed: " + result.getMessage());
            }
        })
        .build());
out.open();
out.send(WrappedPacket.of(new MyPacket()));
out.send(WrappedPacket.of(new MyPacket()));
out.send(WrappedPacket.of(new MyPacket()));
// auto-commit fires when all 3 packets are sent

// Or: call commit() manually and block until confirmed
CompletableFuture<StreamCommitResult> future = out.commit();
StreamCommitResult result = future.get(10, TimeUnit.SECONDS);
```

`StreamCommitResult` has:
- `isSuccess()` — true if the receiver confirmed all packets were processed
- `getStatus()` — `SUCCESS`, `TIMEOUT`, or `FAILED`
- `getMessage()` — error description when not successful
- `getStreamId()` — the stream this result belongs to

### Delivery (`delivery/`)

#### `DeliveryGuarantee`
Enum: `RELIABLE` or `BEST_EFFORT`. Set the default per connection via `WorkerOptions.defaultDeliveryGuarantee(...)`.

#### `PacketOutbox`
Stores undelivered packets for offline clients. When a client disconnects, the server calls `outbox.retain(clientId, pendingPackets)`. On reconnect, packets are replayed after successful handshake. Backed by `ConcurrentHashMap<clientId, PacketOutboxSession>` with TTL and `maxSessions` cap (default `10_000`, use `0` for unlimited).

Delivery guarantee: at-least-once within active process memory. It is not durable by default, does not survive server restart, and ACK means Shinnetai worker receipt rather than application business commit.

```java
PacketOutbox outbox = PacketOutbox.builder()
    .sessionTtl(Duration.ofMinutes(5))
    .maxPacketsPerSession(500)
    .maxSessions(1000)
    .build();

ServerOptions options = ServerOptions.builder(9090)
    .packetOutbox(outbox)
    .onReliablePacketRejected(result -> {
        // alert, log, persist to dead-letter storage, or apply backpressure
        System.err.println("Reliable delivery state: " + result.result());
    })
    .build();
```

Outbox storage is fail-closed for reliable packets: if a packet cannot be tracked because of session or per-session limits, Shinnetai reports `REJECTED_*` through `onReliablePacketRejected(...)` instead of silently downgrading the packet to best-effort. Packets discarded later because of retry exhaustion or TTL report `DISCARDED_*`.

Use the structured APIs when delivery state matters:

- `tryTrack(...)` — assign/record a reliable packet before sending;
- `tryEnqueue(...)` — store one offline packet;
- `tryRetain(...)` — retain a batch of pending packets on disconnect;
- `collectForReplay(..., discardedConsumer)` / `collectForResend(..., discardedConsumer)` — receive discard callbacks while replaying.

`PacketOutboxStoreResult` values are `STORED`, `ALREADY_STORED`, `REJECTED_INVALID_PACKET_ID`, `REJECTED_MAX_SESSIONS`, `REJECTED_MAX_PACKETS`, `DISCARDED_MAX_RETRIES`, and `DISCARDED_EXPIRED`.

#### `PacketDeduplicator`
Sliding-window deduplicator. Tracks `packetId → timestamp` in a `LinkedHashMap`. Entries are evicted by TTL and capped by `maxEntries`. A duplicate is a packet whose ID was already seen within the window.

### Security (`security/`)

#### `RateLimiter`
Token-bucket rate limiter with separate limits for packet/action tokens per second and byte tokens per second. All state is `synchronized`; thread-safe for multiple virtual threads per connection.

```java
RateLimiter limiter = new RateLimiter(1000, 1_000_000); // 1000 packets/s, 1 MB/s
```

Every inbound frame consumes one packet token and its frame size in byte tokens before allocation. Packets can also declare extra cost for heavier operations:

```java
class SearchPacket extends AbstractPacket<?, ?> {
    @Override
    public RateLimitCost rateLimitCost() {
        return RateLimitCost.packets(20); // heavy query
    }
}
```

You can also centralize cost rules in options:

```java
ServerOptions options = ServerOptions.builder(9090)
    .rateLimiterFactory(() -> new RateLimiter(1_000, 10_000_000))
    .rateLimitCostResolver(packet -> packet instanceof SearchPacket
        ? RateLimitCost.packets(20)
        : RateLimitCost.none())
    .build();
```

Handlers can consume tokens for action-level work:

```java
server.on(SearchPacket.class, (packet, ctx) -> {
    if (!ctx.tryConsumeRateLimit(50)) {
        return; // reject or defer expensive action
    }
    runExpensiveSearch(packet);
});
```

#### `IpFilter`
Allowlist/denylist with exact IP matching and CIDR block support. Evaluated in order: custom predicate → exact deny → CIDR deny → exact allow → CIDR allow → default.

```java
IpFilter filter = IpFilter.builder()
    .deny("10.0.0.5")
    .allow("10.0.0.0/24")
    .denyAll()
    .build();
```

#### Packet authorization
Use `packetAuthorizer(...)` to block packets before `AbstractPacket.handle(...)` and `PacketHandlerRegistry.dispatch(...)`:

```java
ServerOptions options = ServerOptions.builder(9090)
    .packetAuthorizer((auth, packet, ctx) -> auth.authenticated())
    .build();
```

#### Session tokens
`requireSessionToken(true)` enables handshake token checks. In production, provide a server-side validator:

```java
ServerOptions options = ServerOptions.builder(9090)
    .ssl(true)
    .requireTlsForSessionTokens(true)
    .requireSessionToken(true)
    .sessionTokenValidator(token -> tokenStore.isValid(token))
    .build();
```

`allowClientGeneratedSessionTokens(true)` exists for development and tests; avoid it for production deployments.

### Resilience (`resilience/`)

#### `RetryPolicy`
Configures retry behaviour for `sendPacket` calls. Factory methods: `RetryPolicy.once()`, `RetryPolicy.fixed(attempts, delayMs)`, `RetryPolicy.exponential(attempts, initialDelayMs, maxDelayMs)`.

#### `CircuitBreaker`
Standard three-state circuit breaker (`CLOSED → OPEN → HALF_OPEN`). Attach an `onStateChange` listener to react to transitions. Use `isCallAllowed()` before attempting a send; record outcomes with `recordSuccess()` / `recordFailure()`.

### Handlers (`handler/`)

#### `PacketHandlerRegistry`
Dispatches received packets to registered handler lambdas or annotated handler objects.

**Lambda style:**
```java
registry.on(MyPacket.class, (packet, ctx) -> { ... });
```

**Annotation style:**
```java
class MyHandlers {
    @PacketHandler
    public void onMyPacket(MyPacket packet, ShinnetaiWorkerContext<?> ctx) { ... }
}
registry.register(new MyHandlers());
```

### Scheduling (`packet/schedule/`)

#### `PacketScheduler`
Periodically sends a packet to a single worker or to all connections on a server matching a `Predicate<ShinnetaiConnection<?>>`.

```java
PacketScheduler.builder()
    .server(server)
    .every(Duration.ofSeconds(5))
    .packet(HeartbeatPacket::new)
    .build()
    .start();
```

### Metrics and Debug

#### `ShinnetaiMetricsCollector` / `ShinnetaiRuntimeMetrics`
Snapshot of runtime metrics: uptime, CPU load (process + system), heap usage, thread count, inbound/outbound bytes and packet counts. Collected from JMX and the connection's `ShinnetaiStatistic`.

```java
ShinnetaiRuntimeMetrics metrics = server.getRuntimeMetrics();
System.out.println(metrics.heapUsed() + " / " + metrics.heapMax());
```

`ShinnetaiServer.health()` returns `HealthStatus.UP`, `DEGRADED`, or `DOWN` based on running state and pending-handshake pressure.

#### `TrafficLog` / `TrafficPlayer`
Records all inbound/outbound packets in a ring buffer, optionally flushed to `.trcl` chunk files on disk. `TrafficPlayer` can replay recorded events for debugging or load testing.

```java
TrafficLog log = TrafficLog.toDirectory(Path.of("logs/traffic"));
WorkerOptions options = WorkerOptions.builder().trafficLog(log).build();
```

---

## shinnetai-serializers

Optional module providing `PacketSerializer` implementations backed by Jackson. Each serializer uses reflection to serialize packet fields; annotating fields with standard Jackson annotations is supported.

| Class | Format |
|---|---|
| `JsonPacketSerializer` | JSON (text) |
| `CborPacketSerializer` | CBOR (binary) |
| `SmilePacketSerializer` | Smile (binary) |
| `MessagePackPacketSerializer` | MessagePack (binary) |
| `FlexBuffersPacketSerializer` | FlatBuffers FlexBuffers (binary) |

Use via `WorkerOptions.builder().packetSerializer(JsonPacketSerializer.INSTANCE)` or per-packet via `AbstractPacket.serializer()`.

---

## Security Notes

- **Always enable SSL** (`WorkerOptions.builder().ssl(true).sslKeystore(...)`) when transmitting session tokens or sensitive data.
- The `packetMagic` value is transmitted in plaintext; it is an identifier, not authentication.
- `requireTlsForSessionTokens(true)` rejects session-token configurations that do not enable SSL.
- `requireSessionToken(true)` requires a minimum 32-character token and now requires `sessionTokenValidator(Predicate<String>)` unless `allowClientGeneratedSessionTokens(true)` is explicitly enabled for development.
- `handshakeTimestampWindowMs` (default 30 s) protects against replay attacks; set to `0` to disable.
- Prefer explicit `PacketRegistry.empty().withCommons().register(...)` registration in production. Package reflection scanning remains a convenience path.
