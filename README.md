# Araisan Meme Server

A lightweight, high-performance WebSocket broadcast server built using Kotlin Multiplatform and Ktor (CIO engine).

## Features

- **Real-time Broadcasting**: Any message received on the `/ws` endpoint is automatically broadcasted to all other
  connected WebSocket clients.
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

## API Endpoints

- `WS /ws`: WebSocket endpoint for broadcasting messages. Any text message received is sent to all other connected
  clients.

## Project Structure

- `src/commonMain/kotlin`: Contains the core logic (Main, Routing, and WebSocket handling).
- `build.gradle.kts`: Build configuration and target definitions.
- `.editorconfig`: Ktlint and IntelliJ IDEA configuration.
