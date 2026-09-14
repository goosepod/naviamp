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

Physical acceptance passed on September 14 with the updated Pixel 10a and Mac development app.
The Pixel first entered a disconnected outgoing session. The Mac then reconnected as controller;
the Pixel player no longer showed the Mac output badge, and its output menu selected
`Playback device: Pixel 10a`. The retained local “Jumpin’ Jack” queue resumed on the Pixel.
Mac Pause held the Pixel at 2:09; Resume followed by detach let the Pixel continue to 2:23.
Trusted reconnect succeeded while it continued to 2:43. Both queues were paused at cleanup.
The disconnection trigger was the independently tracked DNS-SD stop stall in
[#81](https://github.com/goosepod/naviamp/issues/81).

Verification: full presentation and UI JVM suites passed, along with presentation/shared UI Android,
iOS ARM64, and iOS Simulator ARM64 compilation and `verifyCoreFirstArchitecture`.
