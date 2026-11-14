# Intent-V2

Greenfield INTENT HOME launcher (`com.intent.launcher`) **v0.0.07**.

## Build

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Scope

Evolving-intention foundation on a brand-new HOME launcher — not the full INTENT vision, and not a reuse of intent-app / `com.intent.coach`.

- **v0.0.01:** Spanish scripted E2E (explore → fact → proposal → approve → defer → restart)
- **v0.0.02:** Marathon second shape on the same model/screens (`Explore a marathon` on Home)
- **v0.0.03:** Four-core visual redesign (Home, Intention, Conversation, Proposal/Approval) to match Project design targets
- **v0.0.04:** Four-core polish — sans Home display (Inter-like), tighter spacing/icons vs HTML + JPG mocks
- **v0.0.05:** Business third shape (PR #8 — open; rebase this branch if #8 merges before this lands)
- **v0.0.06:** Honest capability registry (architecture Rule 5) — chips / proposals / next actions consult one registry; no fake lesson-completion suggestions
- **v0.0.07:** Stage 4 daily action — time-aware Home next-step with explainable reason (Begin / Discuss / Review) + unit tests across times/states

## Shapes

Shared core records (`Intention`, facts, hypotheses, proposals, sessions). Shape tag: `language` | `marathon` | `business`.

## Capability registry

`CapabilityRegistry` lists what INTENT can do (`available` / `permission_needed` / `not_supported`, etc.). Conversation chips, recurring-session proposals, and `NextActionEngine` recommendations filter through it — e.g. opening an app is available; observing Duolingo lesson completion is not.

## Daily action (Stage 4)

`NextActionEngine` classifies session timing (deferred / due now / approaching / later today / future day) and attaches a human-readable `reason` so Home can answer “Why this now?” without inventing facts. Capability locks fall back to Discuss (or none) with the registry `limitNote`.

## Download

Debug APK (v0.0.07): [INTENT-v0.0.07-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/cursor/stage4-daily-action-9bc1/artifacts/INTENT-v0.0.07-debug.apk)

(Prior: [INTENT-v0.0.06-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.06-debug.apk) · [INTENT-v0.0.04-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.04-debug.apk) · [INTENT-v0.0.03-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.03-debug.apk) · [INTENT-v0.0.02-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.02-debug.apk) · [INTENT-v0.0.01-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.01-debug.apk))
