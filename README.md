# Yggdrasil

Yggdrasil is a modern desktop GUI client for Apache ZooKeeper, built with Kotlin Multiplatform and Compose Desktop.

The project is currently in early development. The first milestone focuses on a safe, practical desktop workflow for browsing ZooKeeper clusters, inspecting znodes, and preparing the foundation for guarded write operations.

## Goals

- Manage multiple ZooKeeper connections.
- Browse znode trees with a desktop-first interface.
- Inspect znode data, stat metadata, ACLs, and watch state.
- Support safe create, update, delete, import, export, and comparison workflows.
- Prefer explicit previews and confirmations for destructive or production-sensitive operations.

## Project Structure

- [`desktopApp`](./desktopApp/src/main/kotlin) contains the JVM desktop entry point and native desktop packaging configuration.
- [`shared`](./shared/src/commonMain/kotlin) contains shared Compose UI, application state, and domain models.
- [`shared/src/jvmMain`](./shared/src/jvmMain/kotlin) is reserved for JVM-specific integrations such as ZooKeeper clients, local storage, and desktop security services.
- [`shared/src/commonTest`](./shared/src/commonTest/kotlin) contains shared domain and state tests.

## Current Status

Implemented:

- Compose Desktop application shell.
- Connection, znode, operation, and error domain models.
- Basic application state holder.
- Path validation tests and state transition tests.

Not implemented yet:

- Real ZooKeeper connectivity.
- Persistent connection profile storage.
- znode tree loading.
- znode data editing.
- ACL editing.
- Import, export, search, and comparison tools.

## Development

Run the desktop app:

```bash
./gradlew :desktopApp:run
```

Run with Compose hot reload:

```bash
./gradlew :desktopApp:hotRun --auto
```

Run tests:

```bash
./gradlew :shared:jvmTest
```

Compile the desktop app:

```bash
./gradlew :desktopApp:compileKotlin
```

Build a native package for the current operating system:

```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
```

## Technology

- Kotlin Multiplatform
- Compose Multiplatform Desktop
- Material 3
- Kotlin Coroutines
- JVM desktop packaging through the Compose Gradle plugin

ZooKeeper client integration is planned for the JVM source set so the UI and domain layers remain decoupled from the concrete client implementation.
