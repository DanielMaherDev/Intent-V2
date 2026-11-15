# Intent-V2

Greenfield INTENT HOME launcher (`com.intent.launcher`) **v0.0.21**.

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
- **v0.0.12:** Destination-app launch from Begin / SessionCard Open — registry-honest PackageManager path; opening ≠ lesson completion
- **v0.0.13:** Stage 5 adaptive UI — Home / Intention / Conversation composition by shape (language · marathon · business) on one model
- **v0.0.14:** Stage 6 UsageStats probe — live permission status, package-level foreground on SessionCard; never lesson completion
- **v0.0.15:** Stage 6 overlay permission probe — live draw-over-apps status; floating agent still not shipped
- **v0.0.16:** Floating agent companion — overlay-gated bubble for the same Conversation agent (`idle` / `attention_needed` / `active_session`); press → Discuss
- **v0.0.17:** Stable sideload signing (`keystore/intent-sideload.jks`) so native Package Installer can install/update GitHub artifacts
- **v0.0.18:** Stage 6 voice path — mic/STT gated; voice + chips share `ConversationCoordinator` (no live LLM)
- **v0.0.19:** Sideload APKs non-debuggable — fixes PackageInstaller `INSTALL_FAILED_TEST_ONLY` (ApkMirror-only installs)
- **v0.0.20:** Drop `QUERY_ALL_PACKAGES` (use `<queries>`); publish release-named sideload APK for PackageInstaller UI
- **v0.0.21:** Declare `SYSTEM_ALERT_WINDOW` so INTENT appears under Display over other apps

## Shapes

Shared core records (`Intention`, facts, hypotheses, proposals, sessions). Shape tag: `language` | `marathon` | `business`.

## Capability registry

`CapabilityRegistry` lists what INTENT can do (`available` / `permission_needed` / `not_supported`, etc.). Conversation chips, recurring-session proposals, and `NextActionEngine` recommendations filter through it — e.g. opening an app is available; observing Duolingo lesson completion is not. All three shapes consult the same registry.

## Daily action (Stage 4)

`NextActionEngine` classifies session timing (deferred / due now / approaching / later today / future day) and attaches a human-readable `reason` so Home can answer “Why this now?” without inventing facts. Capability locks fall back to Discuss (or none) with the registry `limitNote`.

## Session begin (v0.0.09)

Home **Begin** applies `FlowCommand.BeginSession`, creating a durable `SessionRun` (`IN_PROGRESS`). Home then shows **Complete** / **Abandon** (`ACTIVE_SESSION`). Completion is user-reported via the command path — not external calendar write-back or LLM verification.

## Active session card (v0.0.10)

While a run is `IN_PROGRESS`, Home’s SessionCard shows clear in-progress chrome (session title, intention label, status pills), an **elapsed** clock derived only from `startedAtEpochMs` and the device clock (updates locally each second — no server timer), and primary **Complete** / **Abandon** with Discuss as a quiet control.

## Discuss from Home (v0.0.11)

Home **Discuss** (Begin chrome, upcoming Discuss chrome, active SessionCard quiet control, and Agent tab) opens Conversation for the intention behind the current next action. The path activates that intention when needed and does not end an in-progress run — Complete / Abandon and the local elapsed timer keep working after return.

## Destination app launch (v0.0.12)

Shapes with a destination package (Spanish → Duolingo / `com.duolingo`) launch through `LaunchDestinationApp` + `CapabilityRegistry.canLaunchInstalledApp()`. **Begin** starts the session then attempts open; the active SessionCard **Open** control re-launches. Missing app / launch failure / registry block surfaces an honest note — never UsageStats auto-complete or lesson-completion claims.

## Adaptive UI (Stage 5 / v0.0.13)

`ShapeProjection` drives Intention Space hero/approach/knowledge sections, Home “Your space” subtitles, and Conversation eyebrow/headline from `shapeTag` + phase — same screens, different composition. No tracker/report modules yet.

## UsageStats (Stage 6 / v0.0.14)

`UsageStatsProbe` refreshes `usage_stats` in the capability registry from a live AppOps check. On an active SessionCard with a destination package, INTENT shows package-level foreground time when granted, or an honest “Grant usage access” path when not — **never** auto-complete or lesson claims.

## Overlay permission (Stage 6 / v0.0.15)

`OverlayPermissionProbe` refreshes `floating_overlay` from `Settings.canDrawOverlays`. Home shows an honest note and **Grant overlay permission** when needed.

## Floating companion (Stage 6 / v0.0.16)

When overlay is granted, `FloatingAgentService` shows a bubble for the **same** agent as Conversation (`idle` / `attention_needed` / `active_session`; stub labels for capture/think/reply). Press opens Discuss → Conversation. When overlay is denied, there is **no** fake bubble — use Home Agent / Discuss.

## Sideload packaging (v0.0.17–v0.0.21)

Published APKs use `keystore/intent-sideload.jks`, **`isDebuggable = false`**, and **no `QUERY_ALL_PACKAGES`** (destination apps via `<queries>`). Prefer the **release** artifact `INTENT-v0.0.21.apk`. Debuggable or QUERY_ALL builds can fail PackageInstaller UI (silent abort or `INSTALL_FAILED_TEST_ONLY`) while ApkMirror still works. **Uninstall older INTENT once** before installing. v0.0.21 declares `SYSTEM_ALERT_WINDOW` so INTENT appears under Display over other apps.

## Voice conversation (Stage 6 / v0.0.18)

Conversation mic uses platform speech recognition when `RECORD_AUDIO` is granted. Transcripts go through `ConversationCoordinator` — the same path as chips (match a chip phrase, or store as text with an honest non-LLM note). Denied mic → use chips.

## Download

Sideload APK (v0.0.21, release): [INTENT-v0.0.21.apk](https://github.com/DanielMaherDev/Intent-V2/raw/cursor/overlay-settings-v021-930c/artifacts/INTENT-v0.0.21.apk)

(Prior: [INTENT-v0.0.20.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.20.apk) · [INTENT-v0.0.19-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.19-debug.apk) · [INTENT-v0.0.18-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.18-debug.apk) · [INTENT-v0.0.17-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.17-debug.apk) · [INTENT-v0.0.16-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.16-debug.apk) · [INTENT-v0.0.15-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.15-debug.apk) · [INTENT-v0.0.14-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.14-debug.apk) · [INTENT-v0.0.13-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.13-debug.apk) · [INTENT-v0.0.12-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.12-debug.apk) · [INTENT-v0.0.11-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.11-debug.apk) · [INTENT-v0.0.10-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.10-debug.apk) · [INTENT-v0.0.09-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.09-debug.apk) · [INTENT-v0.0.08-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.08-debug.apk) · [INTENT-v0.0.07-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.07-debug.apk) · [INTENT-v0.0.06-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.06-debug.apk) · [INTENT-v0.0.05-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.05-debug.apk) · [INTENT-v0.0.04-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.04-debug.apk) · [INTENT-v0.0.03-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.03-debug.apk) · [INTENT-v0.0.02-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.02-debug.apk) · [INTENT-v0.0.01-debug.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.01-debug.apk))
