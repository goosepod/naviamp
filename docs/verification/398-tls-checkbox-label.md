# Shared TLS checkbox accessibility repair — October 29, 2026

Tracking: #398, found during #397. Release-note category: Bug fix.

The connection form previously rendered its translated TLS-verification label as a sibling of an independently clickable, unnamed checkbox. Native Android accessibility could read the label but could not activate the checkbox through that named node.

The shared `NaviampConnectionForm` now makes the complete row a toggleable control with `Role.Checkbox`, the existing translated label, the current checked state and one change callback. The visual Material checkbox has no independent callback. Text clicks, box clicks and accessibility activation therefore use the same target; the box does not create a second unnamed action. TLS defaults, provider capabilities, trust behavior, connection validation, persistence and settings export/import are unchanged. No new UI strings are introduced.

The rendered regression runs from `core/ui/src/renderedTest`, shared by JVM and Android. It checks the named control's role and checked state, asserts exactly one toggleable node even in the unmerged tree, and verifies two activations yield exactly two callbacks and restore the initial state. The fixture explicitly enables the TLS capability and uses the same scrollable form host as settings.

Validation completed before packaging: the new rendered JVM regression passes and the common implementation compiles for Android, JVM, iOS Simulator ARM64 and iOS ARM64. A signed integration candidate preserves the previously accepted candidate source and adds only this shared production change.

All five related JVM connection cases pass (four existing focus/navigation cases and the new accessibility case). The shared rendered regression also passes on the physical Pixel 10a/Android 17 in 2.419 seconds.

The signed integration APK is installed in place, retaining the original package, UID and signing certificate. In an unsaved connection form, activating the translated label now reaches its owning row directly. Android's actual UIAutomator accessibility XML shows exactly one clickable/checkable subtree containing the label; the checked state becomes true and then false on two activations. The form is canceled without connecting or saving, so the original account's TLS policy is unchanged.

Verified signed checkbox candidate SHA-256: `94e94d26286c3a50a3311575299d8b73eea84df82f9be71d68e9b573c229364c`. It is the previously tested production source plus this common UI fix, packaged as the normal non-debuggable build-60 application with the existing signing identity. The separate owner-requested store-changelog correction (#401) has separate combined-candidate verification; it changes no checkbox or network behavior.

Native screenshots/XML and raw test/build logs remain private under the acceptance worktree's ignored `build/fixes-398-399` and `build/phone-release-acceptance-2026-10-29` directories. No release is published by this verification.

No Android/Desktop/iOS production file is changed. The fix lives in common UI because its behavior has no operating-system dependency. The rendered tests are shared UI tests; the native acceptance probe separately exercises Android's actual accessibility bridge. This checkbox is an input interaction and adds no continuous animation.
