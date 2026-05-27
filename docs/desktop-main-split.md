# Desktop Main Split Checklist

This tracks the work to give the Compose desktop app the same kind of maintainability treatment that Android just received, while continuing to move platform-neutral behavior into shared `core/domain` or `core/ui` code first.

## Goals

- [ ] Reduce `apps/desktop/.../Main.kt` from a large all-in-one app module into focused desktop adapters.
- [ ] Keep desktop behavior aligned with Android by reusing shared domain/UI helpers where possible.
- [ ] Avoid moving desktop-only windowing, filesystem/cache, native playback, file pickers, or OS integration into common code.
- [ ] Keep the packaged Windows app build green after each meaningful split.

## Planned Splits

- [x] Create this desktop split checklist.
- [x] Split desktop window/chrome setup out of `Main.kt`.
- [x] Split desktop constants and tiny app helpers out of `Main.kt`.
- [ ] Split desktop playback/session controller logic out of `Main.kt`.
- [ ] Split desktop radio orchestration out of `Main.kt`, keeping shared queue/refill rules in `core/domain`.
- [ ] Split connection/provider setup out of `Main.kt`.
- [ ] Split library sync/freshness helpers out of `Main.kt`.
- [ ] Split playlist/download mutations out of `Main.kt`, keeping shared provider mutations in `core/domain`.
- [ ] Split artist/album detail loading out of `Main.kt`.
- [ ] Split diagnostics/stats mapping out of `Main.kt`.
- [ ] Re-check whether any remaining desktop logic belongs in shared `core/domain` or `core/ui`.

## Verification

- [x] `.\gradlew.bat :apps:desktop:compileKotlinDesktop`
- [x] `.\gradlew.bat :apps:desktop:stageReleaseApp`

## Notes

- Unlike Android, this is not driven by ART/profile warnings. The desktop target is maintainability and shared behavior reuse.
- Start with low-risk extractions, then move the state-heavy controller logic once the boundaries are clearer.
