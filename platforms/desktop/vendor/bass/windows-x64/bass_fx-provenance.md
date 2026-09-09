# BASS FX for Windows x64

Official package: https://www.un4seen.com/files/z/0/bass_fx24.zip (retrieved 2026-09-06).
The DLL's numeric version is 2.4.12.6; the packaged readme still identifies 2.4.12.1.
The header and x64 DLL are copied unmodified;
the upstream readme and copyright notice are retained as `bass_fx.txt`.

- Archive SHA-256: `A4BAF602865941963127ACB15ED12627D108189F99C2757970432AE7DA0366CD`
- x64 DLL SHA-256: `A6E1847EEF52D882B4137AF514D834C2E220DACEB417C821D1E502FB7A34C84A`

Windows uses BASS_FX_BFX_PEAKEQ because DX8 PARAMEQ cannot represent Naviamp's low bands.
Core owns the frequency, gain, bandwidth, and sample-rate plan. JNI translates that plan into
BFX parameters on Windows and DX8 parameters (bandwidth in semitones) on other JNI targets.
The iOS adapter consumes the same plan through its native DX8 ABI.

The JVM loads this DLL from the same absolute bundled directory as BASS and BASSmix before JNI
initialization. It is an effects library, not a codec registered with BASS_PluginLoad.
