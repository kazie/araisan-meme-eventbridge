# Araisan Meme Server

A lightweight, high-performance WebSocket pub/sub event bridge built using Kotlin Multiplatform and Ktor (CIO engine).
Any service can subscribe to topics and publish messages to them.

## Features

- **Topic-based Pub/Sub**: Clients subscribe to named topics and only receive messages published to those topics.
- **HTTP Publishing**: Services that don't keep a WebSocket open can `POST` the same JSON publish frame.
- **Native Performance**: Compiles to native executables for Linux, macOS, and Windows using Kotlin/Native.
- **Code Quality**: Enforced code style using [Spotless](https://github.com/diffplug/spotless)
  and [Ktlint](https://pinterest.github.io/ktlint/).

## Getting Started

### Prerequisites

- JDK 11 or higher.

### Building the Project

To build the native executable and run all checks (including linting):

```bash
./gradlew build
```

### Running the Server

You can run the server directly using Gradle:

```bash
./gradlew runDebugExecutableHost
```

The server starts by default on `0.0.0.0:8080`.

#### Command Line Arguments

The server accepts the following optional arguments:

- `-port=XXXX`: Specify the port to listen on (default: 8080).
- `-host=X.X.X.X`: Specify the host address (default: 0.0.0.0).

Example:

```bash
./gradlew runDebugExecutableHost --args="-port=9090 -host=127.0.0.1"
```

### Code Style & Formatting

This project uses Spotless for code formatting. Formatting is automatically applied during the build process, but you
can also run it manually:

```bash
./gradlew spotlessApply
```

## API

### `WS /ws`

Every frame is a JSON object with an `op` field. Topics are any non-blank string of at most 256 characters; using
`/` to namespace them per service (e.g. `araisan-meme/control`) is a convention, not a requirement.

Client → bridge:

```json
{"op": "subscribe",   "topic": "araisan-meme/control"}
{"op": "unsubscribe", "topic": "araisan-meme/control"}
{"op": "publish",     "topic": "araisan-meme/control", "data": {"any": "json"}}
{"op": "publish",     "topic": "araisan-meme/control", "data": {"any": "json"}, "echo": false}
```

Bridge → client:

```json
{"op": "message", "topic": "araisan-meme/control", "seq": 42, "data": {"any": "json"}}
{"op": "error",   "message": "Invalid topic"}
```

- **Ordering:** the bridge numbers every message with a per-topic `seq`, starting at 1 and increasing by one.
  Every subscriber receives a topic's messages in increasing `seq` order, so all subscribers see the same sequence.
  Clients can keep the last `seq` they applied and ignore anything that is not newer. A gap in `seq` only means
  messages were published while the client wasn't subscribed.
- **Restarts:** `seq` starts over from 1 when the bridge restarts. That closes every connection, so clients should
  reset their last-seen `seq` whenever they reconnect.
- **Echo:** a publisher that is subscribed to the topic receives its own message too, with the same `seq` as everyone
  else. Send `"echo": false` to opt out.
- **Slow clients:** each connection has its own outbox of up to 1024 messages, so a slow client never delays anyone
  else. A client whose outbox overflows is disconnected with close code 1013 (try again later).
- Delivery is fire-and-forget; nothing is stored for subscribers that connect later.

### `POST /publish`

Publishes without holding a WebSocket open. The body is the same `publish` frame used over the WebSocket.
Responds `202 Accepted` with `{"op":"published","topic":"…","seq":42}`, or `400 Bad Request` if the body is not a
valid publish frame or the topic is invalid.

```bash
curl -X POST localhost:8080/publish \
  -d '{"op":"publish","topic":"araisan-meme/control","data":{"type":"playback_control","action":"pause"}}'
```

## Project Structure

- `src/commonMain/kotlin`: Contains the core logic (Main, Routing, WebSocket handling, Protocol and TopicBroker).
- `src/commonTest/kotlin`: Integration tests running against a real CIO server.
- `build.gradle.kts`: Build configuration and target definitions.
- `.editorconfig`: Ktlint and IntelliJ IDEA configuration.
