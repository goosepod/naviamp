# Native login capability preservation (#331)

Windows acceptance of candidate `a3776547` opened a smart playlist after a native password refresh.
Sonic Radio then reported unsupported while the independent diagnostic advertised support and
returned matches. Restarting restored Radio. The shared native-session controller replaced the
validated provider with a fresh provider whose negotiated extension capabilities were still defaults.

The replacement now completes normal connection validation before it becomes active or is persisted.
This negotiates Sonic, stream offsets, playback reporting, lyrics and account capabilities together.
Failure or cancellation retains the existing provider. A source/provider change while authentication
or validation is suspended discards the late result.

The provider-common regression checks use a synthetic HTTP endpoint response. They verify immediate
Sonic, stream-offset and playback-timeline capabilities, unsupported Sonic remaining unavailable,
validation failure/cancellation retaining the active provider, and late source changes being rejected.
No production platform file, setting, schema, translation or server data changes are involved.

Verification: 126 `NavidromeProviderTest` tests passed with zero failures/errors/skips, including the
native-session cases. Shared Android debug, iOS ARM64 and iOS Simulator ARM64 compilation and
`verifyCoreFirstArchitecture` passed. JVM was compiled by the test run.

Commands: `:providers:navidrome:jvmTest --tests '*NavidromeProviderTest'` with
`-Pkotlin.native.enableKlibsCrossCompilation=false`, followed by
`:providers:navidrome:compileDebugKotlinAndroid :providers:navidrome:compileKotlinIosArm64
:providers:navidrome:compileKotlinIosSimulatorArm64 verifyCoreFirstArchitecture` with native
cross-compilation enabled. Local logs are retained outside tracked verification artifacts.

The packaged login refresh
flow still needs acceptance on a build containing the fix. No release is authorized.
