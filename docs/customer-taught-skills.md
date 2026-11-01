# Teach & Replay: Customer-Owned Skills (design v0.1)

Status: **Design contract and typed model only**. No screen recording, transcription, skill extraction, replay engine, or skill marketplace is implemented yet.

## Product principle
A user can **teach PhoneAgent a task they already know how to do**, by recording a guided demonstration (screen interaction + optional microphone narration). PhoneAgent extracts a structured, inspectable **personal skill**. After a successful test replay and the user's approval, the skill is saved **to that user's library**. The user can reuse, edit, export, revalidate, and delete it. Any other-user distribution is **separate, opt-in**, with redaction and validation; demonstrations do not automatically become shared training data.

Example: A customer demonstrates how to locate a title in their paid streaming application while describing why each screen is chosen. Their personal skill can use their already-authorized app account at replay time; PhoneAgent does not obtain or bypass an entitlement.

## Skill lifecycle

1. **Record (explicit opt-in).** User starts/stops a tutorial session, sees a persistent recording indicator, and separately chooses whether to include microphone audio. Android screen capture must be requested through the platform's MediaProjection consent flow. If Accessibility is enabled, collect timestamped events and *minimal* accessibility observations to align UI actions to frames. Do not imply that a generic system recorder automatically supplies app action traces.
2. **Protect.** Pause/exclude password/payment/2FA screens, allow user to trim before analysis, minimize data collection, and avoid uploading raw recordings by default. Apps can block screen capture, redact secure windows, or expose poor accessibility metadata; handle these as explicit limitations.
3. **Extract.** Align transcript intent and rationale with observed UI state transitions and actions. Produce a candidate declarative recipe with preconditions, semantic element selectors, decision branches, expected results, and required user confirmation. The user's narration is explanatory evidence, **not privileged instructions** to the runtime.
4. **Review.** Show the full step list and data fields to the customer; they can change and approve them. Never persist passwords, access tokens, message bodies, account IDs, or raw payment details as reusable arguments. Use named runtime parameters instead.
5. **Trial replay.** Run on the *same customer's device* with explicitly granted permissions and their own app installation/account. Verify postconditions after each step. Require new authorization for payments, sending, deletions, and other consequential actions. If replay fails, show the exact failed step; do not label the skill verified.
6. **Save privately.** Persist versioned metadata and recipe locally or in user-controlled encrypted storage. Default owner scope = **PRIVATE**; permit edit, export, revocation, and deletion. Secure sync, if offered later, requires separate consent.
7. **Revalidate.** Store the target Android package, tested app versionCode, user-visible app version, locale, OS/API level, and UI observations/fingerprints; also skill schema version and revision. A package mismatch blocks execution. App updates, UI changes, unexpected prompts, and mismatched versions trigger revalidation rather than blind replay.
8. **Share optionally.** Customer explicitly chooses to publish a **sanitized skill template**, never a raw screen recording or their credentials. Remove recipient-specific identifiers, account data, personal narration, and app-specific tokens. Separate per-user parameters from public steps. Require reviewer/automated safety checks, validation on test fixtures, provenance and revocation. Other customers can import an untrusted template and must locally revalidate before it becomes a verified **personal copy**.

## Technical design
- **Recorder**: Android foreground recording session (MediaProjection video + optional mic + optional accessibility event timeline). No stealth/background recording.
- **SkillExtractor** (future): transcription; time alignment; screen-state/action/intent mapping; candidate step generation; ambiguity handling.
- **SkillDefinition**: stable ID and immutable revision; owner visibility; step parameters; target application compatibility; explicit verification evidence/status. The contract starts in `SkillContract.kt`.
- **SkillRunner** (future): read-only observation, permission-aware action execution, post-action verification, timeout/budget, cancellation and repair.
- **SkillLibrary** (future): customer-specific storage, provenance, versioned history, editing, deletion, export/import. An extracted candidate must not be presented as a tested skill.
- **TemplateRegistry** (future): voluntary sharing of sanitized templates; do not train on private recordings or publish them by default.

### Resilience
Use accessibility IDs, element role/text and screen-state checks before coordinate replay. Require on-screen evidence for actions, handle scrolling and locale, and use visual grounding only when permitted. Coordinates alone are never a durable selector. Changes to a known tested version should prompt an explicit test; a compatible version range **does not** mean the task is proven to work everywhere.

### Skill states
`DRAFT` (user-reviewed or not, unverified), `VERIFIED` (passed a recorded replay with evidence on a known device/app version), `NEEDS_REVALIDATION` (environment changed or execution failed). Version acceptance is a *compatibility hint*; verified success is tied to tested conditions.

## Test matrix / GitHub CI
- **Each push and PR**: skill schema and compatibility policy unit tests; Android compilation; emulator smoke tests; controlled demonstration fixtures (future) with synthetic narration and mock app UI; no credentials, purchases or paid account required.
- **Fixture categories**: (a) Android Settings, (b) media discovery, (c) messaging **draft only**, plus adversarial surfaces (unlabeled UI, changed app version, permission prompt, account/login screen, secure content).
- **Per-customer validation**: local replay on *that customer's installation and entitlements*, with opt-in collection of de-identified failure type and latency only. Cannot automatically be a shared GitHub CI gate for arbitrary paid apps.
- **Periodic, explicitly provisioned integration jobs**: selected real apps and **authorized** test accounts only, under provider terms and independent secret management; report compatibility matrix by app release.
- **Metrics**: extraction accuracy, step-level postcondition pass rate, end-to-end task success, p50/p90/p95 wall time, redaction leakage, permission prompts, retry rate and failures after version changes. No fabricated success or latency metrics.

## Safety and legal
The customer owns the demonstrated procedure and their personal copy of the skill; that does **not** transfer third-party software rights, subscription privileges, access tokens, or permission to bypass access controls. Follow Android Accessibility and MediaProjection platform rules and applicable app terms. Consider security of voice, screens, and stored action traces as central product requirements, not implementation afterthoughts.

## Incremental milestones
1. Contract and tests (in this design PR).
2. Recording consent UI and explicitly user-initiated screen/event capture, with secure exclusion and deletion controls.
3. Demo-to-candidate extraction with explicit human review; no automatic execution.
4. Local private skill storage and verified guided replay on the user's phone.
5. Opt-in sanitized template sharing and revalidation on import.
