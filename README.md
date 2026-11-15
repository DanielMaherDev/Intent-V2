# Intent-V2

Greenfield INTENT HOME launcher (`com.intent.launcher`) **v0.0.25**.

## Build

```bash
./gradlew :app:assembleRelease :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/release/app-release.apk` → publish as `artifacts/INTENT-v0.0.25.apk`

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
- **v0.0.22:** Stage 6 notification listener probe — honest registry + SessionCard grant path (no silence / content inbox)
- **v0.0.23:** Floater / Home quick thought capture — `FlowCommand.CaptureThought`, SAID provenance, no LLM summary
- **v0.0.24:** Undo last reversible FlowCommand via transaction history; soft-delete intentions/sessions to recoverable trash with restore; honest irreversible notes for external opens / purge
- **v0.0.25:** Daily check-in / check-out (worksheet §10) — morning focus confirm/adjust, evening SAID review + defer/keep; same Home model; SessionCard unchanged
- **v0.0.27:** Stage 4 deferral deadline preview — Later shows window + siblings; confirm before defer
- **v0.0.26:** OBSERVED provenance — UsageStats package foreground stored as `Evidence` distinct from SAID/INFERRED; SessionCard / check-out surface; never auto-Complete or lesson claims
- **v0.0.28:** Quiet mode / DND honesty — SessionCard request never claims silence; deep link to system DND; `quiet_mode` stays `NOT_SUPPORTED`

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

## OBSERVED provenance (v0.0.26)

When UsageStats is granted, package foreground for the active session is persisted via `FlowCommand.RecordObservedEvidence` as **OBSERVED** evidence (`observe_app_usage`). SessionCard and post-Complete check-out show “Observed N min in …”; the user can mark it inaccurate. Observation never Completes the run and never claims lesson completion (`observe_lesson_completion` stays `NOT_SUPPORTED`).

## Overlay permission (Stage 6 / v0.0.15)

`OverlayPermissionProbe` refreshes `floating_overlay` from `Settings.canDrawOverlays`. Home shows an honest note and **Grant overlay permission** when needed.

## Floating companion (Stage 6 / v0.0.16)

When overlay is granted, `FloatingAgentService` shows a bubble for the **same** agent as Conversation (`idle` / `attention_needed` / `active_session`; stub labels for capture/think/reply). **Tap** opens Discuss → Conversation; **long-press** opens Capture thought. When overlay is denied, there is **no** fake bubble — use Home Agent / Discuss / **Capture thought**.

## Notification summaries (Stage 6 / v0.0.22)

`NotificationListenerProbe` refreshes `notification_listener` from enabled listener packages. Active SessionCard shows an honest note and **Grant notification access** when needed. Quiet mode / screen capture stay `NOT_SUPPORTED`. No content retention and no silence claims.

## Thought capture (v0.0.23)

`FlowCommand.CaptureThought` stores a raw `ThoughtCapture` with SAID provenance. Home and floater share one sheet (text or Stage 6 voice). No LLM summary; classification is deferred.

## Undo / trash (v0.0.24)

Home offers **Undo** for the last reversible local commitment (approve / decline / defer / session / trash). Intention Space soft-deletes intentions and sessions into trash with **Restore** (kept until purge) or permanent **Purge**. Opening another app is logged as irreversible — INTENT cannot undo external actions.

## Daily check-in / check-out (worksheet §10 / v0.0.25)

Morning Home **check-in** surfaces today’s intention focus + next-action preview; **Confirm focus** or **Adjust in Discuss** via the command path (`ConfirmDailyCheckIn`). Active session continues Begin / Open / Complete / Abandon + SessionCard. Evening **check-out** records user-reported what-happened as **SAID** (optional SAID hypothesis), then **Keep schedule** or **Defer tomorrow** (local deferral only). Same intention snapshot — no separate daily-task store. No fake calendar/LLM.

## Deferral preview (v0.0.27)

Home **Later** shows a one-line `DeferralPreview` (new clock window + sibling sessions on the same intention). Confirm dialog before `FlowCommand.DeferNextSession`. Local schedule only — no invented dependency graph or calendar write-back.

## Quiet mode / DND honesty (v0.0.28)

Active SessionCard offers **Request quiet for this session**. INTENT never claims notifications were silenced — registry keeps `quiet_mode` as `NOT_SUPPORTED`. Optional **Open system Do Not Disturb** deep link. OEM variance and emergency alerts stay outside INTENT control.

## Sideload packaging (v0.0.17–v0.0.28)

Published APKs use `keystore/intent-sideload.jks`, **`isDebuggable = false`**, and **no `QUERY_ALL_PACKAGES`** (destination apps via `<queries>`). Prefer the **release** artifact `INTENT-v0.0.25.apk`. Debuggable or QUERY_ALL builds can fail PackageInstaller UI (silent abort or `INSTALL_FAILED_TEST_ONLY`) while ApkMirror still works. **Uninstall older INTENT once** before installing. Keep `SYSTEM_ALERT_WINDOW` so INTENT appears under Display over other apps.

## Voice conversation (Stage 6 / v0.0.18)

Conversation mic uses platform speech recognition when `RECORD_AUDIO` is granted. Transcripts go through `ConversationCoordinator` — the same path as chips (match a chip phrase, or store as text with an honest non-LLM note). Denied mic → use chips.

## Download

Sideload APK (v0.0.25, release): [INTENT-v0.0.25.apk](https://github.com/DanielMaherDev/Intent-V2/raw/cursor/daily-checkin-checkout-d3de/artifacts/INTENT-v0.0.25.apk)

(Prior: [INTENT-v0.0.28.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.28.apk) · [INTENT-v0.0.27.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.27.apk) · [INTENT-v0.0.26.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.26.apk) · [INTENT-v0.0.24.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.24.apk) · [INTENT-v0.0.23.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.23.apk) · [INTENT-v0.0.22.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.22.apk))
