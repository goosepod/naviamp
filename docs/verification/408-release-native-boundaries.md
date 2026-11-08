# v2.10.0 release transfer: native boundary review

Tracking: #408. This inventory compares the old `release/2.10.0` baseline `6e602832a` with
the consolidated `f4da8bbc5` candidate. These production adapter changes already exist on accepted
`main` at `9783770a0`; the new Windows/editor/progress fixes are common code. Consolidation does
not introduce another platform implementation. The platform diff was inspected separately.

| Changed production file | Concrete native boundary |
| --- | --- |
| [AndroidCoreUriPickerEffects.kt](../../apps/android/src/main/kotlin/app/naviamp/android/AndroidCoreUriPickerEffects.kt) | Android document-picker MIME filters for the Activity result API. |
| [AndroidNaviampCoreCatalog.kt](../../apps/android/src/main/kotlin/app/naviamp/android/AndroidNaviampCoreCatalog.kt) | Wires the Android Context/ContentResolver certificate-file effect into Core. |
| [DesktopComposition.kt](../../apps/desktop/src/desktopMain/kotlin/app/naviamp/desktop/app/DesktopComposition.kt) | Wires the JVM filesystem/native-dialog certificate effect using the host's data-directory Path. |
| [NaviampIosApplication.kt](../../apps/ios/src/iosMain/kotlin/app/naviamp/ios/NaviampIosApplication.kt) | Wires the UIKit document-picker/security-scoped file effect using the Apple app-storage directory. |
| [DesktopOwnedDialogSurface.kt](../../core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopOwnedDialogSurface.kt) | Owns AWT/ComposeDialog construction, native bounds, activation, movement callbacks and disposal. |
| [DesktopRasterPresenter.kt](../../core/ui/src/jvmMain/kotlin/app/naviamp/ui/DesktopRasterPresenter.kt) | Publishes AWT window facts and initializes Skiko rendering for future transparent native windows on the event thread. |
| [AndroidBassAudioBackend.kt](../../platforms/android/src/main/kotlin/app/naviamp/android/playback/AndroidBassAudioBackend.kt) | Translates shared stream/callback contracts into Android BASS JNI handles and calls. |
| [AndroidBassJni.kt](../../platforms/android/src/main/kotlin/app/naviamp/android/playback/AndroidBassJni.kt) | Declares the JNI FILEPROCS/end-sync ABI entry points. |
| [AndroidBassPlaybackEngineRuntime.kt](../../platforms/android/src/main/kotlin/app/naviamp/android/playback/AndroidBassPlaybackEngineRuntime.kt) | Retains Android Uri conversion and JVM monitor effects while removing portable worker/clock policy. |
| [AndroidCoreCertificateFilePort.kt](../../platforms/android/src/main/kotlin/app/naviamp/android/settings/AndroidCoreCertificateFilePort.kt) | Uses ContentResolver streams, persistable URI permissions and app-private Android filesystem access. |
| [DesktopBassAudioBackend.kt](../../platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/playback/bass/DesktopBassAudioBackend.kt) | Translates the shared random-access source into a BASS JNI stream. |
| [DesktopBassJniBinding.kt](../../platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/playback/bass/DesktopBassJniBinding.kt) | Declares JNI stream and callback-registration operations and removes the old host-owned callback map. |
| [DesktopBassPlaybackEngineRuntime.kt](../../platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/playback/bass/DesktopBassPlaybackEngineRuntime.kt) | Retains JVM URI/filesystem conversion and synchronization while moving portable workers/clock to Core. |
| [DesktopCredentialProtector.kt](../../platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/security/DesktopCredentialProtector.kt) | Calls Windows CurrentUser DPAPI and keeps MSIX-sensitive filesystem IO in the app process. |
| [DesktopCoreCertificateFilePort.kt](../../platforms/desktop/src/desktopMain/kotlin/app/naviamp/desktop/settings/DesktopCoreCertificateFilePort.kt) | Uses AWT FileDialog, JVM file streams and POSIX file permissions for a native file copy. |
| [IosBassPlayback.kt](../../platforms/ios/src/iosMain/kotlin/app/naviamp/ios/playback/IosBassPlayback.kt) | Bridges BASS FILEPROCS C pointers, StableRef lifetime and memcpy; portable worker/clock policy is removed. |
| [IosCoreCertificateFilePort.kt](../../platforms/ios/src/iosMain/kotlin/app/naviamp/ios/settings/IosCoreCertificateFilePort.kt) | Uses security-scoped NSURL access and Foundation app-private file copying. |
| [IosCoreSettingsSyncPort.kt](../../platforms/ios/src/iosMain/kotlin/app/naviamp/ios/settings/IosCoreSettingsSyncPort.kt) | Returns UIDocumentPicker URLs and manages Apple security-scoped persistent references. |
| [NavidromeAndroidPlatform.kt](../../providers/navidrome/src/androidMain/kotlin/app/naviamp/provider/navidrome/NavidromeAndroidPlatform.kt) | Supplies the Android HTTP-engine actual by delegating to the shared JVM/Android native TLS initializer. |
| [NavidromeAppleTlsMaterial.kt](../../providers/navidrome/src/iosMain/kotlin/app/naviamp/provider/navidrome/NavidromeAppleTlsMaterial.kt) | Imports Security.framework identities/anchors and manages SecTrust/NSURLCredential native resources. |
| [NavidromeIosPlatform.kt](../../providers/navidrome/src/iosMain/kotlin/app/naviamp/provider/navidrome/NavidromeIosPlatform.kt) | Maps NSURLSession authentication callbacks to shared challenge decisions and invokes Apple completion handlers. |
| [NavidromeJvmTlsMaterial.kt](../../providers/navidrome/src/jvmAndAndroidMain/kotlin/app/naviamp/provider/navidrome/NavidromeJvmTlsMaterial.kt) | Initializes JCA KeyStore/SSLContext and the OkHttp TLS engine using JVM-specific crypto APIs. |
| [NavidromeJvmPlatform.kt](../../providers/navidrome/src/jvmMain/kotlin/app/naviamp/provider/navidrome/NavidromeJvmPlatform.kt) | Applies JVM SSLContext/HttpsURLConnection defaults and delegates provider-client TLS initialization. |
| [naviamp_bass_jni.cpp](../../native/bass-jni/src/naviamp_bass_jni.cpp) | Implements the BASS/JNI FILEPROCS/end-sync ABI, native-thread attachment and native/global-reference lifetime. |

`core/testkit/src/jvmMain/kotlin/app/naviamp/testkit/NaviampMobileMutualTlsFixture.kt` and
`NaviampMutualTlsFixture.kt` are JDK HTTP/SSL/process fixture infrastructure, not product hosts.
Native adapter tests and packaging/build resources are outside the production placement requirement.
The #412 follow-up changes only the desktop native acceptance test; it does not change any adapter.
