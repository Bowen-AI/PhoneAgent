# Benchmark and correctness protocol (v0)

**No real Android measurements yet.** An emulator can validate task flow and guards, but physical Pixel measurements are required for on-device inference, power, thermals and realistic latency.

### Three scenario families

1. **Media:** open an installed streaming app, find a named show/film, verify the right title. Optional playback success requires a separate observable, user-authorized signal. Use controlled test apps first; Hulu/YouTube as compatibility examples.
2. **Settings:** navigate to a named setting, verify current value; if changing a setting is supported, verify post-change state and restore the test device afterward.
3. **Messaging:** navigate to a sandbox test chat and prepare a draft; verify recipient and content, but **do not send** without an explicit confirmation step.

Also test failures: missing accessibility grant, login wall, missing app, stale UI, network loss, failed click, screen protected by DRM, cancellation, unexpected notification and app-version changes.

### Device and emulator

- CI: JVM tests, schema validation, mock/replay.
- Emulator: functional navigation, repeated automated UI tests; latency is not representative of a Pixel.
- Physical phone: Android version, application versions, model artifact hash, backend CPU/GPU/NPU, RAM, battery and thermal state. Record cold vs warm model starts separately.

### Time spans

Record `SystemClock.elapsedRealtimeNanos()` in the same device process. For every trial report input route, local-model load, observe, LLM generation/network round trip, action dispatch, screen stabilization, verify, and end-to-end durations in milliseconds. Log app switches and attempts. Never subtract unsynchronized phone/cloud wall clocks.

Use a fixed timeout and count failure/timeout runs in latency summaries. Report success with **independent state verification**, not an agent's claim. Run at least 30 trials per scenario per condition as a starting point. Report n, success rate, timeouts, mean, p50, p90, p95, and failure types. Compare warm and cold separately. Avoid fabricated numbers.

Never include raw messages, credentials, screenshots, or personally identifying UI contents in logs. Work with seeded test fixtures and redact measurements before publication.
