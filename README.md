# PhoneAgent — Android on-device agent

Independent open-source Android phone agent project under Bowen-AI. **Work in progress: Android diagnostic prototype and benchmark tooling; not yet an autonomous cross-app AI assistant.**

## Goals

A user-authorized, local-first phone agent with Android Accessibility observation, controlled device actions, an interchangeable local/cloud model interface, deterministic task verification, and strong permission boundaries.

## Repository layout

- `app/` — Kotlin Android diagnostic application and opt-in AccessibilityService.
- `docs/architecture.md` — design choices, API contracts, limitations and planned phases.
- `docs/benchmark_protocol.md` — reproducible latency and correctness test method.
- `benchmarks/schema.md` — JSONL run-level measurement schema (no fabricated results).
- `tools/summarize.py` — success rate + p50/p90/p95 report.
- `tests/test_summarize.py` — Python analyzer regression tests.
- `.github/workflows/` — analyzer tests and Android debug build.

## MVP status

**Implemented in this starter:** diagnostic permission screen, Android accessibility observation and basic manually-triggered system navigation, event timing records, analysis pipeline. **Not implemented yet:** integrated Gemma/LiteRT inference, autonomous planner, multimodal vision, cross-app benchmarks and device-proven success rates.

## Testing

```bash
python3 -m unittest discover -s tests -v
python3 tools/summarize.py --help
```

For Android development, install Android SDK API 35 / Build Tools and Gradle 8.11.1, then run `gradle :app:assembleDebug` (CI is configured to do the Android build). Install on a test device; explicitly grant Accessibility permission only after reviewing the code.

**No actual Pixel benchmark timings are claimed.** Use the real-device protocol and record measurements independently.

## Source provenance

Conceptual reference: [PokeClaw](https://github.com/agents-io/PokeClaw), Apache 2.0. PhoneAgent's starter implementation is independently written and does not incorporate PokeClaw source. Preserve third-party notices for any later code imports.
