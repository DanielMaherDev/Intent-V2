# Intent-V2

Greenfield INTENT HOME launcher (`com.intent.launcher`) **v0.0.11**.

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
- **v0.0.05:** Business/work third shape (`Explore a business idea`) — discovery-friendly + optional research-hour proposal (content; ships as later versions after rebase collisions)
- **v0.0.06:** Honest capability registry (architecture Rule 5) — chips / proposals / next actions consult one registry; no fake lesson-completion suggestions
- **v0.0.07:** Stage 4 daily action — time-aware Home next-step with explainable reason (Begin / Discuss / Review) + unit tests across times/states
- **v0.0.08:** Business third shape rebased onto registry + Stage 4 (PR #8)
- **v0.0.09:** Begin/session execution — Home Begin starts a real in-progress session run (Complete / Abandon); registry-honest, no fake calendar/LLM
- **v0.0.10:** SessionCard / active-session UX — in-progress chrome, honest local elapsed timer, Complete / Abandon always reachable
- **v0.0.11:** Home Discuss opens Conversation for the active/scheduled intention (Begin / Complete / Abandon / SessionCard timer unchanged)

## Shapes

Shared core records (`Intention`, facts, hypotheses, proposals, sessions). Shape tag: `language` | `marathon` | `business`.

## Capability registry

`CapabilityRegistry` lists what INTENT can do (`available` / `permission_needed` / `not_supported`, etc.). Conversation chips, recurring-session proposals, and `NextActionEngine` recommendations filter through it — e.g. opening an app is available; observing Duolingo lesson completion is not. All three shapes consult the same registry.

## Daily action (Stage 4)

`NextActionEngine` classifies session timing (deferred / due now / approaching / later today / future day) and attaches a human-readable `reason` so Home can answer “Why this now?” without inventing facts. Capability locks fall back to Discuss (or none) with the registry `limitNote`.

## Session begin (v0.0.09)

Home **Begin** applies `FlowCommand.BeginSession`, creating a durable `SessionRun` (`IN_PROGRESS`). Home then shows **Complete** / **Abandon** (`ACTIVE_SESSION`). Completion is user-reported via the command path — not external calendar write-back or LLM verification.

## Active session card (v0.0.10)

While a run is `IN_PROGRESS`, Home’s SessionCard shows clear in-progress chrome (session title, intention label, status pills), an **elapsed** clock derived only from `startedAtEpochMs` and the device clock (updates locally each second — no server timer), and primary **Complete** / **Abandon** with Discuss as a quiet control. No destination-app launch or LLM/calendar claims in this slice.

## Discuss from Home (v0.0.11)

Home **Discuss** (Begin chrome, upcoming Discuss chrome, active SessionCard quiet control, and Agent tab) opens Conversation for the intention behind the current next action. The path activates that intention when needed and does not end an in-progress run — Complete / Abandon and the local elapsed timer keep working after return.

## Download

Debug APK (v0.0.11): [INTENT-v0.0.11-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/cursor/discuss-from-home-ae02/artifacts/INTENT-v0.0.11-debug.apk)

(Prior: [INTENT-v0.0.10-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.10-debug.apk) · [INTENT-v0.0.09-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.09-debug.apk) · [INTENT-v0.0.08-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.08-debug.apk) · [INTENT-v0.0.07-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.07-debug.apk) · [INTENT-v0.0.06-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.06-debug.apk) · [INTENT-v0.0.05-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.05-debug.apk) · [INTENT-v0.0.04-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.04-debug.apk) · [INTENT-v0.0.03-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.03-debug.apk) · [INTENT-v0.0.02-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.02-debug.apk) · [INTENT-v0.0.01-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.01-debug.apk))
