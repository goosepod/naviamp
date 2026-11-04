# Filled Now Playing spacing

Tracking: #495. The owner supplied a 320×704 content viewport where the filled-artwork
player's separate rating and audio-information lines crowded the identity and transport.

## Shared layout

Filled artwork now places the format and quality on equal-width sides of a centered
favorite/five-star rating bar. Existing compact audio-label splitting is reused. Side
text is measured at the active font scale; when it cannot fit, ratings and the complete
audio label use separate lines instead of clipping or squeezing the controls.

Identity lines use 6 dp gaps. Main control groups use 12 dp gaps, or 8 dp in the existing
under-500-dp compact-height presentation. The title height and time-label widths honor
larger text in this filled presentation. The recovered audio-information line gives the
ordinary narrow layout more room while retaining artwork coverage and all controls.

The change is entirely in Core's shared UI. No Android/Desktop/iOS production code,
settings, schema, translations or new UI copy changed. Wide/split, standalone and TV
presentation retain their existing layout. Animation policy/update frequency is unchanged.

## Verification

The focused rendered matrix includes 320×704 at font scales 1.0, 1.4 and 2.0; 280×500;
412×818; long lossless quality text; absent audio information; hidden audio information;
and unavailable ratings. It checks centered ratings, audio on opposite sides, readable
fallback and time labels, spacing, favorite/rating actions and visible transport/footer.
Existing artwork tests also cover loaded/placeholder corner coverage, wide/standalone/TV
fallbacks, artist silhouettes and artwork reuse across playback progress updates.

On 2026-11-03 all seven focused artwork tests passed, including the eight-case spacing
matrix and the existing seven-viewport layout matrix. Android/JVM/both iOS common UI
compilation, architecture verification and staged Mac packaging passed. Captures in
`build/495-captures` were inspected, including 320×704 at 1×/1.4×/2× text. The large-text
capture retains readable identity and time text and a full two-line metadata fallback.
Logs: `build-495-final.log` and `build-495-package.log`.

The existing isolated Mac artwork-review app was updated from that staged build, preserving
its separate bundle ID and connected profile. Previous app files and a stopped-profile
backup are in `/private/tmp/naviamp-artwork-review-ogzelx7l/before-spacing-495`.
The review launcher requires `NaviampArtworkReview.cfg`; an initial restart failed when that
separate configuration was omitted. It was restored with the staged classpath and existing
profile path before relaunch. No production profile was replaced.

Visible production-window inspection verified the format/quality fields on opposite sides
of centered ratings, clear identity/transport gaps, the full artwork background and the
restored player. Both MP3 and FLAC metadata were observed. The owner then confirmed the
opened test app looked good; that approval accepts this spacing. Hosted checks still gate
merge. No animation-performance improvement is claimed.

The first run passed the screenshot-sized and large-text cases, but the existing landscape
case incorrectly expected the noncompact 12 dp gap in a compact-height layout. Its assertion
now checks the intended 8 dp compact gap. Visual inspection also found wrapped time text at
2× font size; filled-layout time slots were widened before the final run. No playback timing
or native animation policy was changed.
