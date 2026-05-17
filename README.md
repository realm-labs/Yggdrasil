# Yggdrasil

Yggdrasil is a modern desktop GUI client for Apache ZooKeeper, built with Kotlin Multiplatform and Compose Desktop.

The project is still under active development, but the core ZooKeeper client workflow is now usable: connect to a
cluster, browse the znode tree, inspect and edit data, manage ACLs, run zkCli-style commands, and move data between
environments.

## Goals

- Manage multiple ZooKeeper connections.
- Browse znode trees with a desktop-first interface.
- Inspect znode data, stat metadata, ACLs, and watch state.
- Support safe create, update, delete, import, export, and comparison workflows.
- Prefer clear previews for destructive or production-sensitive operations.
- Provide practical SSH tunnel support for clusters that are only reachable through jump hosts.

## Project Structure

- [`desktopApp`](./desktopApp/src/main/kotlin) contains the JVM desktop entry point and native desktop packaging configuration.
- [`shared`](./shared/src/commonMain/kotlin) contains shared Compose UI, application state, and domain models.
- [`shared/src/jvmMain`](./shared/src/jvmMain/kotlin) contains JVM-specific integrations such as ZooKeeper clients,
  local storage, SSH tunneling, file picking, and desktop security services.
- [`shared/src/commonTest`](./shared/src/commonTest/kotlin) contains shared domain and state tests.

## Feature Highlights

- Saved ZooKeeper connection profiles with read-only/read-write modes.
- ZooKeeper digest authentication.
- SSH tunnel connections with password or identity-file authentication.
- Secret storage for credentials where the desktop platform supports it.
- Znode tree browsing with filtering, refresh, and selectable/copyable paths.
- Znode create, copy, edit, and delete actions.
- Automatic delete preview before execution.
- Data editor modes for text, JSON, hex, and Base64, with validation for structured formats.
- Inspector panel for stat metadata and ACL details.
- ACL editing for common ZooKeeper permission schemes.
- Top-bar search for znode paths and data, with inline results and direct tree navigation.
- Embedded zkCli-style terminal with command validation, supported command parameters, and tab completion.
- Import, export, and compare workflows for znode subtrees.
- Resizable tree, editor, inspector, and terminal panes.
- English and Chinese UI.
- Light, dark, and system theme preferences.

## Current Status

Most of the P0 desktop client workflow is implemented. The remaining work is mostly product hardening:

- broaden zkCli compatibility where the GUI can safely map commands to the underlying client API;
- improve edge-case behavior around very large trees and large znode payloads;
- continue polishing platform-specific packaging, signing, and distribution;
- add more integration coverage against real ZooKeeper versions and SSH environments.

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
- Kotlinx Serialization
- Apache Curator
- System SSH client integration for tunnels
- JVM desktop packaging through the Compose Gradle plugin

ZooKeeper client integration lives in the JVM source set so the UI and domain layers remain decoupled from the concrete client implementation.
