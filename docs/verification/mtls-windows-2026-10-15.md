# Windows mTLS playback acceptance — 2026-10-15

Issue: [#294](https://github.com/goosepod/naviamp/issues/294).
Implementation: [#296](https://github.com/goosepod/naviamp/pull/296).

## Conditions

Windows x64, JDK 21, staged application built from the issue branch, fresh disposable application
profile, generated private server CA and PKCS12 client identity. Persistent audio caching, audio
prefetch and waveforms were disabled. Output was pinned to BASS's no-sound device and muted.
The installed application and normal profile were not used. No TV or receiver was involved.

The loopback server required the fixture client certificate. Its request record contained only
`CN=Naviamp Test Client` identities, including artwork and audio requests. Only URL paths and ranges
were recorded; authentication query parameters were excluded.

## Visible application

- Restored the seeded connection and three-minute **TLS Tone** track without entering credentials.
- Started the uncached track through the normal Play control. The displayed position advanced
  from 0:28 to 0:58. The server recorded finite authenticated ranges rather than a whole-file fetch.
- Paused, sought from approximately 0:58 to 2:01, and remained paused. The server recorded a new
  range at byte 3,801,088, beyond the previously consumed audio.
- Resumed and observed progress advance to 2:31.
- Closed the application process, relaunched against the same disposable profile, and observed the
  restored connection, track and approximately 2:36 position.
- Resumed after restart and observed successful completion at 3:00.
- Closed the test application and stopped the fixture, which deletes its temporary certificates.

The first fixture launch was rejected because the test seeder omitted SQLite's schema version.
The seeder now uses the production database initializer. No production migration was added.

## Automatic regression coverage

Ordinary Desktop tests exercise the shared playback adapter and actual native BASS with a long
upstream response whose tail is withheld. They require uncached startup, authenticated range seeking
while paused, resume, prompt stop, a private-CA-only control and rejection of a missing required client
certificate. A separate real SQLite/encryption test covers saved settings and downloaded playback.

Shared tests cover bounded range caching, invalid response metadata, source changes, current/prepared
lease lifetime, native cursor cancellation and preventing failed authenticated reads from reporting
successful track completion. Provider tests cover valid/missing/untrusted client certificates, private
server trust, bad PKCS12 passwords, browsing, artwork, byte delivery and recovery.

Native callback decoding/seeking tests are also included in Android instrumentation and iOS native
verification. The complete required CI matrix must pass before the implementation is merged.

## Reproduction and limits

See [the standard test commands and visible-app fixture](../testing.md#desktop-mutual-tls).
The visible check used generated WAV media and a local server. Broader format decoding remains covered
by the existing native codec suite. Original media uses random access when upstream supplies finite
ranges and total length; sequential streams retain server/native seek constraints when neither
ranges nor provider time offsets are available.
