# Album Play profile investigation

Sven confirmed #520 uses the album Play button with global Crossfade / 8 seconds and an individual
album Gapless profile. The separate album-track selection context bug does not explain that report.

The new common `NaviampCoreAlbumPlaybackProfileTest` exercises the actual Core album-select,
SavePlaybackProfile and album Play commands, with source-scoped profile lookup and the production
shared playback-engine adapter. Its observing engine records settings at play and next-track
preparation, then emits completion to exercise the next within-album track.

On main baseline `cafcee8cd`, one passing regression covers four saved modes: Gapless (zero
crossfade), explicit Crossfade (3 seconds), Pause (zero crossfade without preloading), and Inherit
(global 8 seconds). All retain the album queue group and expected settings on the next within-album
track. This does not reproduce the reported failure in the portable command/queue/resolution path.

The probe deliberately uses an in-memory profile repository and observing engine. It is not proof
of on-disk profile migration/lookup or audible native transitions. Those boundaries, actual
reporter/device details, shuffle, queue boundaries, active edits and restored queues still require
investigation. No production change or resolution claim is made at this checkpoint.

## Related album-track context loss

A second end-to-end regression starts by selecting an individual album track. Before the correction,
Gapless expects zero crossfade but observes the global 8 seconds at the engine boundary. This is
the separate path already identified in the issue, not Sven's confirmed album Play reproduction.

The shared track action controller now captures the loaded album context with its ordered tracks
and starts through the existing album playback owner at the selected index. That owner retains the
source-scoped saved profile and full album group. Generic library/search track selection keeps its
existing context. No new setting, persistence schema or platform production code is added.

Validation after correction: 24 focused common tests pass (17 Core, 5 queue-controller and 2 profile
entry-point tests), with zero failures/errors/skips. The two end-to-end tests cover the four saved
modes through both album Play and album-track selection, including next-track preparation and the
next within-album track. Architecture verification and shared Android/JVM/both iOS compilation pass.
The original album Play report remains open for storage/native/audible investigation.
