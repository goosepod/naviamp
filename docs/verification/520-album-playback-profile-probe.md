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
