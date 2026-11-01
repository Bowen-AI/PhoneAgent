# Guarded customer skill replay (prototype)

This slice implements a pure Kotlin `SkillRunner` policy and deterministic fake-device tests. It **does not** yet record a phone screen, transcribe narration, execute actions on Android, persist customer recordings/skills, or claim real-world end-to-end task success.

## Current behavior

- A skill belongs to an exact `ownerId`. The runner rejects calls from a different owner.
- Every skill starts by opening its exact declared Android package. The installed app version and first foreground-screen fingerprint must be compatible before any interaction.
- `TRIAL` accepts a draft and returns results for review; it does **not** change a skill into `VERIFIED`.
- `VERIFIED_REPLAY` requires a previously verified skill and matching verified version.
- UI-visible labels use case-insensitive exact matching (whitespace trimmed), whereas package names, owner IDs, parameter names and user-supplied parameter values are case-sensitive and kept verbatim.
- Before tapping, the target label must be visible. A successful tap dispatch is **not** success: the expected postcondition must appear in the target app.
- User parameters are injected only at replay time, never included in `SkillRunResult`, and sensitive field labels are conservatively blocked. This simple keyword blocker is not complete DLP.
- Consequential actions require a fresh `SkillConfirmation` callback per action and an allowlisted `SkillDevice.confirmAndAct` adapter. Confirmation defaults to **denied**.
- The runner returns a specific failure status and 0-based failing step, and does not report an unchecked success.

## Important current boundaries

The `SkillDevice` interface has **no production adapter**: only tests with a controlled mock app/screen fixture exist. The Android UI can still launch/settings/tap manually, but no autonomous replay is wired up. The mock's successful actions must not be confused with a real third-party-app reliability benchmark.

`EnterParameter` currently checks field visibility, successful dispatch, and that the target app remains foreground. It does not yet validate entered characters; that will need a privacy-aware verification hook before claiming production-grade replay.

The runner is synchronous and has no cancellation, wait/retry budget, or asynchronous UI-state handling. A real Android adapter must observe and verify the accessibility tree after each action, honor permission boundaries, refuse sensitive screens, enforce allowlists for confirmed actions, avoid logging values, and support user interruption. An on-device UI confirmation must replace mock confirmations before any purchase/message/delete action.

## Next slices

1. Add explicit recording consent UI and user-visible tutorial session controls (MediaProjection/optional mic, no silent capture).
2. Create an on-device demonstration/event fixture extractor with transcript alignment and customer review.
3. Connect a permission-scoped adapter for safe, non-consequential tasks such as searching Android Settings; add emulator integration tests for replay.
4. Add encrypted customer-owned local skill storage with export/delete, then introduce opt-in template sharing.
5. Maintain pre-submit build, unit, and emulator checks on every PR and push. Enforce mandatory checks using repository ruleset/branch protection when configured.

The default remains **private customer skill, no credentials, no entitlement bypass**.
