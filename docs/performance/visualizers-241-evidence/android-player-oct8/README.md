# Physical Android player samples, 2026-10-08

Read the [measurement report](../../visualizers-241-direct-presentation.md#android-ordinary-playback-baseline-investigation-2026-10-08) before comparing values. CPU percentages refer to one core, not the entire phone or battery consumption.

- Physical Pixel 10a, Android 17/API 37; isolated minified/profileable benchmark app.
- 1080 × 2424 display, density 420, active 60 Hz mode; USB charging at 100%, thermal status 0.
- Synthetic ten-minute mono 16 kHz WAV. Short metadata has no marquee; long metadata includes a scrolling title. The artwork state uses the fixture's static placeholder.
- Baseline APK SHA-256: `cb2b6dcbcc4381f5709f067383568d6e5d9168744c78069ea6b33553d4090915`.
- Pixel-deadline APK SHA-256: `4fb8f4119884a87f0701c9e810b577aeccd44fccf80af191c44b33a315a95a56`.

`*-cpu.txt` contains unmodified simpleperf process task-clock output. `cpu-summary.csv` extracts percentage and interval length. `*-summary.txt` contains process/thread-scoped Perfetto queries from a separate capture after the CPU interval. GPU wall durations include driver waits and are not GPU utilization percentages. Animation callbacks include progress and marquee work. Zero parent-window frames do not establish zero compositor cost.

Compare static, progress-only, marquee-only and combined states. Moving/holding marquee phases, startup/JIT activity and different interval lengths make exact CPU deltas uncertain. The report records the rejected/mislabeled and blank/loading states; they are excluded from accepted performance samples. The five-second collapsed sample checks whether GLES stops; it is not a sustained CPU budget test.

Raw screenshots and system traces remain local because they may include private device content. Reproduce with `scripts/visualizer-probe/android-player-sample.py` and `scripts/visualizer-probe/presentation-trace.sql`. Verify that the app is visible, its content actually moves, and the test state is correct before sampling.
