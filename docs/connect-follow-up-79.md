# Connect playback ownership follow-up (#79)

The shared peer regression reproduces the September 14 phone/Mac role reversal using two complete
Core compositions and an in-memory transport. Before the fix, the receiving phone retained its old
`Reconnecting` remote output after accepting the Mac as its controller.

An authenticated incoming target session now selects local playback, cancels the previous outgoing
reconnect and command retries, and clears its selection and notice. It retains trust and leaves the
local queue untouched. Shared Now Playing decoration requires connected playback authority before
labeling a track as remote. The output menu describes selection without claiming that a selected,
armed, or disconnected destination is currently playing.

Regressions cover reversal from active and disconnected sessions, preservation of local queue
metadata on both peers, heartbeat survival past the reconnect deadline, detach, retained trust,
and remote Now Playing labels for every connection status with and without playback authority.
All production edits are in common Core code; there are no host adapter or settings/schema changes.

Physical acceptance remains pending while the Pixel is unplugged: repeat the outgoing disconnect /
incoming controller sequence, verify local output labels and controls, detach, and confirm target
playback continues. Do not close [#79](https://github.com/goosepod/naviamp/issues/79) until that pass.

Verification: full presentation and UI JVM suites passed, along with presentation/shared UI Android,
iOS ARM64, and iOS Simulator ARM64 compilation and `verifyCoreFirstArchitecture`.
