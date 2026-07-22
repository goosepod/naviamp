# Naviamp Agent Development Rules

## Core-First File Placement

Before creating or moving any source file under an Android, Desktop, or iOS module, verify that the file depends on a concrete platform-only API, operating-system lifecycle, native library, or host integration.

- If the code can compile and behave correctly in shared Kotlin, place it in the appropriate `core` module.
- Product behavior, UI, actions, navigation, validation, orchestration, persistence policy, and provider-neutral logic belong in Core by default.
- A platform directory is not justified merely because only one platform currently calls the code.
- SQLDelight repositories that consume shared queries and models belong in `core:storage`; a host-selected driver, native path, filesystem, credential store, or OS database API remains platform-specific.
- Platform files should be narrow adapters for concrete OS or native capabilities. Document the exact constraint that prevents each new platform file from being shared.
- If only part of a proposed file is platform-specific, split the portable owner into Core and inject the narrow native effect through a shared contract.
- Use neutral or `Naviamp` names in Core and matching `Android`, `Desktop`, or `Ios` prefixes only for genuine platform implementations.

Treat an unexplained Android/Desktop/iOS implementation difference as architecture debt, not as an accepted capability difference.
