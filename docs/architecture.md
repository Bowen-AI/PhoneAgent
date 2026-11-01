# PhoneAgent architecture v0.1

Status: manually controlled **diagnostic Android APK**, not an autonomous AI assistant. Device verification pending.

## Interfaces

User intent -> TaskFamily classification -> Planner (future) -> ActionPolicy -> DeviceController -> Accessibility observation -> verifier -> event/latency telemetry.

Current foundation:
- `AgentCore.kt`: portable action schema, three app-agnostic scenario families (media, settings, messaging), deliberately restrictive sample policy.
- `AgentAccessibilityService.kt`: user-enabled Android accessibility view tree, node text matching, manual click. The foreground UI must be tested on a real device.
- `MainActivity.kt`: manual test control surface with basic elapsed-time logging; no hidden agent.
- `benchmarks/`, `tools/`: schema and aggregations for verified run-level latency.

Future:
1. Execution engine with cancellable task sessions, monotonic time spans, stale-node rejection and deterministic verification.
2. Local Gemma/LiteRT and optional remote models through a common planner API. No AI inference is currently installed.
3. Controlled replay app and instrumented emulator tests, followed by real-device tests on a Pixel.
4. Accessibility-tree-first observation, screenshot/vision fallback only when device privacy and screenshot restrictions permit.
5. Per-action confirmation for consequential actions; isolated network APIs; explicit opt-in, redaction and no unattended messaging, payments or deletions.

The three test families are **media discovery** (Hulu and YouTube as examples; not guaranteed playback), **Android settings**, and **messaging draft**. The agent must check UI state after each action; a successful tap dispatch is NOT task success.

## Security

Accessibility can expose highly sensitive UI data. Prototype does not request network, contact, SMS, notification or storage permissions. It does not contain a cloud API key. This is not a vetted security boundary: sensitive screens must be excluded from agent observations; tools need per-app scopes, confirmation and dynamic risk classification.

## Reference

PokeClaw (agents-io/PokeClaw, Apache 2.0) informed design discussion. Starter code is independently authored; reuse of any upstream code requires license/attribution handling.
