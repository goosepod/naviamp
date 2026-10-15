# Cross-platform mTLS acceptance and native boundary audit

Issue: [#297](https://github.com/goosepod/naviamp/issues/297).
Implementation and final CI records: [#298](https://github.com/goosepod/naviamp/pull/298).

## Runtime contract

One shared contract runs on every supported host:

| Host | Real TLS and playback test |
| --- | --- |
| Windows, macOS, Linux | `DesktopSharedMutualTlsContractTest` |
| Android API 30 and API 35 | `AndroidMutualTlsPlaybackInstrumentedTest` |
| iOS simulator | `IosMutualTlsPlaybackIntegrationTest` |

Each host imports generated certificate files through its production private-copy adapter. The test
deletes the selected originals, then creates a fresh production provider client using the copied
files. It checks missing/untrusted client identities, bad passwords, untrusted server CAs, hostname
mismatch, authenticated browsing/artwork and uncached native playback. Playback must start before a
complete download, pause, seek to 90 seconds beyond buffered audio, resume and stop within three
seconds. Persistent audio caching and prefetch are disabled. A separate server-CA-only control omits
the client identity. The fixture records authenticated client subjects and finite audio ranges.

Only disposable loopback servers, credentials and media are used. Android checks use CI emulators;
iOS checks use a simulator. No TV, receiver, production credential or OS trust-store installation is
involved. Native document selection is injected in these tests; their real file-copy and TLS effects
execute. Earlier visible Windows application acceptance is recorded in
[the Windows verification record](mtls-windows-2026-10-15.md).

The initial expanded checks identified a CIO hostname-validation gap for IP endpoints and an Android
API 30 PKCS12 decoder limitation. JVM-based hosts now share an OkHttp/JCA adapter with hostname
verification and an explicit bundled crypto-provider instance. It does not register a global JCA
provider. iOS uses Apple Security and connection-scoped NSURLSession challenge handling.

The iOS native test requires a fully booted simulator rather than Kotlin's default standalone
execution. The latter failed Apple Security trust evaluation with OSStatus -26276 before client
authentication. CI provisions a dedicated simulator and collects trust-service diagnostics; this
keeps strict certificate validation intact and isolates it from parallel common-test simulator use.

## Shared ownership

Core owns TLS capability policy, certificate format parsing, challenge decisions, connection-form
transactions and error copy, picker-result revocation, provider requests, authenticated playback
leases, range validation, buffering, cancellation and queue behavior. Existing TLS settings and
credential persistence remain in use; this change adds no settings or database schema.

## Native production-file audit

| File | Irreducible native boundary |
| --- | --- |
| `providers/navidrome/src/androidMain/kotlin/app/naviamp/provider/navidrome/NavidromeAndroidPlatform.kt` | Android HTTP-engine initialization delegates to the JVM TLS adapter. |
| `providers/navidrome/src/jvmMain/kotlin/app/naviamp/provider/navidrome/NavidromeJvmPlatform.kt` | JVM HTTP-engine initialization and the existing Java URLConnection TLS-default API. |
| `providers/navidrome/src/jvmAndAndroidMain/kotlin/app/naviamp/provider/navidrome/NavidromeJvmTlsMaterial.kt` | Java KeyStore, SSLContext/SSLSocketFactory and OkHttp engine initialization. |
| `providers/navidrome/src/iosMain/kotlin/app/naviamp/provider/navidrome/NavidromeAppleTlsMaterial.kt` | Apple Security certificate/PKCS12 import, SecTrust evaluation and Core Foundation ownership. |
| `providers/navidrome/src/iosMain/kotlin/app/naviamp/provider/navidrome/NavidromeIosPlatform.kt` | NSURLSession configuration and authentication-delegate completion callbacks. |
| `platforms/android/src/main/kotlin/app/naviamp/android/settings/AndroidCoreCertificateFilePort.kt` | Android ContentResolver URI reads and app-private filesystem copies. |
| `apps/android/src/main/kotlin/app/naviamp/android/AndroidCoreUriPickerEffects.kt` | ActivityResult OpenDocument MIME filters. |
| `apps/android/src/main/kotlin/app/naviamp/android/AndroidNaviampCoreCatalog.kt` | Mounts the ContentResolver certificate-file effect in shared services. |
| `platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/settings/DesktopCoreCertificateFilePort.kt` | AWT FileDialog/EventQueue lifetime and Java NIO private file copies/permissions. |
| `apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopComposition.kt` | Mounts the native certificate-file effect with the desktop application-data path. |
| `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/settings/IosCoreCertificateFilePort.kt` | Security-scoped NSURL reads, NSData and app-private file copies. |
| `platforms/ios/src/iosMain/kotlin/app/naviamp/ios/settings/IosCoreSettingsSyncPort.kt` | Reuses UIDocumentPicker URL selection and manages security-scoped bookmark lifetime. |
| `apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosApplication.kt` | Mounts the UIKit/NSURL certificate-file effect with the application-support path. |

Android shrinker rules retain the JCA provider SPI names used by PKCS12 parsing. Native-test hosts
only choose disposable files, execute the real copy effect and create their platform BASS backend.

## Reproduction and limits

See [standard mobile tests](../testing.md#mobile-mutual-tls) and
[Desktop tests](../testing.md#desktop-mutual-tls). The shared fixture requires a JDK and reserves
loopback ports 59974–59977. Original media uses finite authenticated ranges when the server supplies
them; servers without ranges or provider time offsets retain buffered-seeking limits.
