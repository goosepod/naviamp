# Learning Kotlin Through Naviamp

This project should be approachable for someone learning Kotlin for the first time.

## Early Concepts

- `data class` creates simple immutable value objects like `Track` and `Album`.
- `interface` defines behavior a class promises to provide, such as `MediaProvider`.
- `suspend fun` marks a function that can do asynchronous work without blocking a thread.
- `sealed interface` is useful when a value has a limited set of known shapes, such as original or transcoded stream quality.
- `value class` wraps a primitive value, such as a string ID, so different ID types are harder to mix up by accident.

## Project Layout

- `core/domain` contains shared app concepts and logic.
- `providers/navidrome` adapts Navidrome-specific behavior to the shared provider contract.
- `apps/desktop` contains the desktop UI.

## Teaching Rule

When adding Kotlin code, prefer obvious names and small types. A future reader should be able to understand the first version before we optimize it.

