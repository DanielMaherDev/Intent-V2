# Intent-V2

Greenfield INTENT HOME launcher (`com.intent.launcher`) **v0.0.111**.

## Build

```bash
./gradlew :app:assembleRelease :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/release/app-release.apk` → publish as `artifacts/INTENT-v0.0.111.apk`

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
- **v0.0.26:** OBSERVED provenance — UsageStats package foreground stored as `Evidence` distinct from SAID/INFERRED; SessionCard / check-out surface; never auto-Complete or lesson claims
- **v0.0.27:** Stage 4 deferral deadline preview — Later shows window + siblings; confirm before defer
- **v0.0.28:** Quiet mode / DND honesty — SessionCard request never claims silence; deep link to system DND; `quiet_mode` stays `NOT_SUPPORTED`
- **v0.0.29:** Stage 6 screen-capture consent — MediaProjection at-ask; no OCR / lesson completion / continuous recording
- **v0.0.30:** Tracker modules + PathSummary / progress proof — shape-default modules in the same intention store; SAID/OBSERVED/INFERRED labeled; no fabricated charts
- **v0.0.31:** Privacy-safe session notification review — package counts only; no titles/bodies; no auto-Complete
- **v0.0.44:** CASE-08 six-week move — finite dependency / landlord-gated plan on shared intention model (`Plan a six-week move` on Home)
- **v0.0.46:** Floater Ask / Today / Capture drawer — long-press menu; same agent as Conversation (reclaimed from 0.0.33)
- **v0.0.47:** Guided intention builder + multi-thought triage — heuristic shape/split (no LLM); SAID facts only on confirm; rejected triage keeps RAW evidence (reclaimed; was staged as 0.0.32)
- **v0.0.48:** Home clutter-free attention strip — one next-action strip + optional elapsed/OBSERVED line; quiet grants; no first-viewport clutter
- **v0.0.49:** Intention drawer + hold-for-apps reorder — accessible intention list, SAID app affinity, not a classic app grid (reclaim; was planned as 0.0.36)
- **v0.0.59:** App connection honesty — PackageManager inventory of Duolingo/Chess/Spotify/Strava candidates; AppConnection DISCONNECTED/NOT_SUPPORTED; intention usage SAID/OBSERVED only; partner_* caps stay NOT_SUPPORTED; Play Store deep link when missing
- **v0.0.60:** App-linked session soft-pause — when UsageStats shows foreground ≠ destination affinity for >15s, freeze elapsed (“Away from app”); Resume / Complete / Abandon; never auto-Complete
- **v0.0.61:** Live LLM Discuss honesty path — `live_llm` stays NOT_SUPPORTED/DENIED without key+opt-in+client; Discuss labels local heuristics; optional key presence wiring without claiming LIVE (reclaimed from 0.0.51)
- **v0.0.64:** Job-offer decision shape (CASE-10) — SAID criteria matrix + decision-review session; never chooses accept/decline for the user
- **v0.0.65:** CASE-05 quit smoking shape — baseline cigarettes/day, evening check-in, honest SAID cessation tracker modules (`cessation`)
- **v0.0.80:** CASE-06 app-use reduction fixture — Instagram/YouTube 45-min target, Shorts honesty on OBSERVED package evidence (no auto-Complete; reclaimed from 0.0.63 / 0.0.53 / 0.0.40)
- **v0.0.98:** CASE-01 small errand / quick to-do inbox (OS-073) — “Buy milk tonight” as first action + reminder, not an exploring Intention (reclaimed from 0.0.79 / 0.0.62 / 0.0.52)
- **v0.0.101:** CASE-03 EU-citizen work Spain — research/checklist shape (`research`); SAID citizenship/arrangement/duration/documents; sourced checklist questions; no invented law; `web_research` NOT_SUPPORTED (reclaimed from 0.0.66/0.0.86 after tip 0.0.98)
- **v0.0.103:** Evening thought triage (OS-154) — at check-out, classify RAW SAID thoughts as task / intention / reminder / keep / discard (user-picked only; was 0.0.88 / 0.0.68 / 0.0.58)
- **v0.0.107:** Local snapshot export honesty — Settings exports IntentionFlowStore JSON via share/save; local device copy only, not cloud backup; import not claimed (reclaim from 0.0.92 / 0.0.72)
- **v0.0.105:** Four-core Conversation visual — find-a-way framing, chip/composer/brand atmosphere vs conversation mock (Home hero owned by sibling visual PR; was 0.0.90 / 0.0.70; reclaimed after tip 0.0.98)
- **v0.0.106:** Four-core SessionCard visual — SoftCard + 2×2 meta grid aligned to approval weekly mock; active-session Open/Complete/Abandon/Discuss/Resume + soft-pause honesty unchanged (Home exploring hero / Conversation untouched; reclaimed from 0.0.91 / 0.0.71)
- **v0.0.104:** Four-core Home visual alignment — brand-first greeting hierarchy (date → headline → lede) above the attention strip; pills restored; strip / soft-pause / errands / drawer / settings intact (reclaim from 0.0.89 / 0.0.69 / 0.0.60)
- **v0.0.109:** Gate D accessibility + floater sticky honesty — TalkBack container labels on SessionCard / next-action strip / floater bubble; overlay-denied Home/Companion fallback; `START_STICKY` only while overlay granted (never invent grant; reclaim from 0.0.94 / 0.0.74)

## Shapes

Shared core records (`Intention`, facts, hypotheses, proposals, sessions, trackerEntries). Shape tag: `language` | `marathon` | `business` | `move` | `decision` | `cessation` | `app_use` | `research`.

## Capability registry

`CapabilityRegistry` lists what INTENT can do (`available` / `permission_needed` / `not_supported`, etc.). Conversation chips, recurring-session proposals, and `NextActionEngine` recommendations filter through it — e.g. opening an app is available; observing Duolingo lesson completion is not. Language, marathon, business, move, decision, cessation, app_use, and research shapes consult the same registry.

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

When overlay is granted, `FloatingAgentService` shows a bubble for the **same** agent as Conversation (`idle` / `attention_needed` / `active_session`; stub labels for capture/think/reply). **Tap** opens Ask (Discuss → Conversation); **long-press** opens the **Ask / Today / Capture** drawer. When overlay is denied, there is **no** fake bubble — use Home **Companion menu**, Discuss, or **Capture thought**.

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

## Screen capture (Stage 6 / v0.0.29)

`ScreenCaptureProbe` tracks MediaProjection consent-at-ask. Active SessionCard offers **Capture screen** until denied; grant is in-process only. No OCR, continuous recording, frame retention, or lesson completion.

## Tracker + reports (v0.0.30)

Intention Space opens **Tracker** (shape-default modules; manual SAID entries) and **Path & progress** (PathSummary + labeled SAID/OBSERVED/INFERRED proof from the same store). No fabricated completion charts; missing partner/OBSERVED data stays empty with a registry limit note. Cessation modules (v0.0.65) stay SAID self-report — `observe_cessation` is NOT_SUPPORTED.

## Session notification review (v0.0.31)

When Notification access is granted, INTENT tallies **package + coarse category counts** during an active session window. Titles/bodies are not retained. Rows can be dismissed; counts never auto-Complete. Denied → SessionCard grant path.

## Floater action drawer (v0.0.46)

Long-press (or Home **Companion menu**) shows **Ask** (Conversation + optional intention context chip), **Today** (Home next-action focus), and **Capture** (existing thought sheet). Same agent / `ConversationCoordinator` path — no second bot and no LLM unless `live_llm` is LIVE.

## Intention builder + triage (v0.0.47)

Home **Start a new intention** opens a guided builder (heuristic shape suggestion, missing-fact prompts). Confirm writes SAID facts only — no auto-INFERRED. **Triage thoughts** splits a dump or raw captures into proposed intentions; confirm creates one, reject leaves RAW evidence. No live LLM.

## Quick to-do / errands (v0.0.98)

`FlowCommand.CaptureErrand` stores a lightweight `ErrandTodo` (schema v6 `errandTodos`) — first action + optional reminder, never an exploring Intention. Home **Errands** → **Add a quick to-do**.

## Home attention strip (v0.0.48)

One calm Home strip: next action + Begin/Discuss/Later (or Complete/Abandon). Optional secondary line is session elapsed **or** OBSERVED package minutes when UsageStats is granted — never both, never invented. Grant prompts, companion menu, and session notification rows stay quiet; builder/triage/move explore stay under Your space.

## Intention drawer / hold-for-apps (v0.0.49)

Home **Intentions** opens an accessible drawer of active intentions (tap → Intention Space). Long-press enables reorder persisted via `FlowCommand.ReorderIntentions` (undoable when undo is LIVE). Intention Space **Apps you hold** lets you confirm package affinity as SAID — no UsageStats auto-ranking, not a classic app grid; the system app drawer stays available. Held apps prefer destination launch over shape defaults.

## Sideload packaging (v0.0.111–v0.0.111)

Published APKs use `keystore/intent-sideload.jks`, **`isDebuggable = false`**, and **no `QUERY_ALL_PACKAGES`** (destination / connection candidates via `<queries>`). Prefer the **release** artifact `INTENT-v0.0.111.apk`. Debuggable or QUERY_ALL builds can fail PackageInstaller UI (silent abort or `INSTALL_FAILED_TEST_ONLY`) while ApkMirror still works. **Uninstall older INTENT once** before installing. Keep `SYSTEM_ALERT_WINDOW` so INTENT appears under Display over other apps.

## Gate D accessibility + floater sticky (v0.0.111)

TalkBack `contentDescription` on the Home next-action / SessionCard container and the floater bubble `FrameLayout` (button text labels unchanged). Overlay denied or service stopped → honest Home Companion menu / Discuss / Agent path — no invented bubble. `FloatingAgentService` returns `START_STICKY` only while overlay is granted; sticky restart re-checks overlay and defaults to IDLE.

## Voice conversation (Stage 6 / v0.0.18)

Conversation mic uses platform speech recognition when `RECORD_AUDIO` is granted. Transcripts go through `ConversationCoordinator` — the same path as chips (match a chip phrase, or store as text with an honest non-LLM note). Denied mic → use chips.

## App-linked session pause (v0.0.60)

Active sessions with a destination affinity (Spanish → Duolingo) soft-pause the local elapsed timer when UsageStats shows the foreground package is away for >15s. The Home attention strip shows **Away from app**, a pause-aware secondary elapsed line, and **Resume**. Without UsageStats or affinity, the timer keeps the local clock with an honest note. Soft-pause never Completes or claims lesson progress.

## App connections (v0.0.59)

Settings → Connections inventories Duolingo / Chess.com / Spotify / Strava via PackageManager + `<queries>` (never invents installs). `AppConnection` statuses are `DISCONNECTED` / `NOT_SUPPORTED` — unlink is a no-op when disconnected. Intention Space shows which intentions use each connection (SAID holds / OBSERVED foreground only). `partner_spotify` / `partner_chess` / `partner_strava` stay `NOT_SUPPORTED`. Missing candidates offer a source-labeled Play Store deep link.

## Live LLM Discuss honesty (v0.0.61)

Registry capability `live_llm` is **NOT_SUPPORTED** without a configured key, **DENIED** when a key may exist but the user has not opted in, and **TEMPORARILY_UNAVAILABLE** when key+opt-in are ready but the live client is not shipped. Discuss / Conversation clearly labels **Local heuristics**. Optional `llm.api.key.configured` BuildConfig flag wires future app-secret presence without claiming LIVE. Model output must never write OBSERVED or auto-Complete.

## Four-core Home visual (v0.0.104)

Home restores the four-core brand-first composition: ✦ INTENT brand, date, greeting headline + lede, then one next-action attention strip (pills + CTAs). Soft-pause Away/Resume, errands inbox, quiet grants, companion drawer, and Settings → Connections stay intact below the greeting.

## Download

Sideload APK (v0.0.111, release): [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/cursor/gate-d-a11y-floater-93b3/artifacts/INTENT-v0.0.111.apk)

(Prior: [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.111.apk) · [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.111.apk) · [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.111.apk) · [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.111.apk) · [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.111.apk) · [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.111.apk) · [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.111.apk) · [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.111.apk) · [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.111.apk) · [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.111.apk) · [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.111.apk) · [INTENT-v0.0.111.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.111.apk))
