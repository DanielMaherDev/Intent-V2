# Intent-V2

Greenfield INTENT HOME launcher (`com.intent.launcher`) **v0.0.154** (this branch) · EXPLANATION / PROGRESS Agent-module cards on tip **v0.0.153** (#99) · SCHEMA **10**.

## Build

```bash
./gradlew :app:assembleRelease :app:testDebugUnitTest
```

APK: `app/build/outputs/apk/release/app-release.apk` → publish as `artifacts/INTENT-v0.0.154.apk`

## Scope

Evolving-intention foundation on a brand-new HOME launcher — not the full INTENT vision, and not a reuse of intent-app / `com.intent.coach`.

- **v0.0.154:** FE-A2 depth — typed EXPLANATION + PROGRESS Agent-module Conversation cards from existing ResponseBlocks (skip empty/inactive; no invented OBSERVED metrics); SCHEMA 10 stamp-only. Rebased onto tip **0.0.153** (#99 FE-E3).
- **v0.0.153:** FE-E3 app-level reduced-motion token — `LocalReducedMotion` + Settings Motion (follow system Animator scale / prefer reduced); gates Agent-module Conversation card enters and Home↔Intention pager jumps; never overrides system Remove animations off; SCHEMA 10 stamp-only. Based on tip 0.0.152 (#97).
- **v0.0.152:** FE-C3 date-controls depth — Tracker entry date prev/next (explicit SAID event day; no invented OBSERVED dates); SessionCard / app-duration show local Begin day+clock; SCHEMA 10 stamp-only. Based on tip 0.0.151 (#96).
- **v0.0.151:** Honest Language Conversation chips — user-built language intentions no longer surface Spanish fixture practice catalog (“Everyday conversations” / tutor chips); fixture `intention-spanish` keeps scripted E2E chips; Quit/`other` stay empty; SCHEMA 10 stamp-only
- **v0.0.150:** Agent-module Conversation UI — NEXT FOR YOU / ONE QUESTION / PROPOSED · NOT YET ACTIVE / RECORDED / REPORT PREVIEW cards + NOTED bar, brand+intention pill chrome, “Talk to INTENT…” composer; maps existing FlowCommand / NextAction / Proposal / tracker / report data (no invented OBSERVED facts); SCHEMA 10 stamp-only
- **v0.0.149:** Hold suggestions shape honesty — language catalog (Duolingo) only for language shapes; Builder Quit/`other`/cessation no longer invent Hold Duolingo; SCHEMA 10 stamp-only
- **v0.0.148:** Restore Exploring Intention Space empty-note honesty — default/language `ShapeProjection.emptyNote` again says no invented practice plan (fixes `IntentionDetailCoherenceTest.exploring_surfacesSixSections_withoutInventedPlan`); SCHEMA 10 stamp-only
- **v0.0.147:** Fix duplicate Home header — one `HomeBrandRow` (INTENT + overflow + settings); remove stacked second brand/settings row; SCHEMA 10 stamp-only
- **v0.0.146:** Create-intention UX — stop Spanish cold-start auto-seed; empty Home CTA → Intention Builder; Conversation chips target active intention id (no hardcoded `intention-spanish`); hide CASE explore rows + empty chip catalog for `other`/unknown; SCHEMA 10 stamp-only
- **v0.0.145:** Static Android App Shortcuts XML — Ask / Today / Capture / History wired to companion intents (`OPEN_CONVERSATION` / `OPEN_TODAY` / `CAPTURE_THOUGHT` / `OPEN_HISTORY`); no silence shortcuts; SCHEMA 10 unchanged
- **v0.0.144:** High-contrast accessibility theme — Settings Appearance adds High contrast (follow device light/dark) with elevated HC light/dark tokens via `ThemePreference` / `HomeChromePrefs`; SCHEMA 10 unchanged
- **v0.0.143:** Remaining GatewayStatus Conversation cards — PERMISSION_DENIED / UNSUPPORTED / NEEDS_APPROVAL via `CommandGateway.requestCapabilityAction` + `GatewayStatusCard`; SCHEMA 10 unchanged
- **v0.0.142:** Quiet-mode + deep-link honesty — Settings states `quiet_mode` NOT_SUPPORTED (no fake silence toggle); documents floater Ask/Today/Capture/History companion intents; SCHEMA 10 unchanged
- **v0.0.141:** App-duration criterion UI — SessionCard / attention show completed/target minutes + soft-pause progress; never lesson completion; SCHEMA 10 unchanged
- **v0.0.140:** GatewayStatus Conversation cards — CANCELLED / STALE_REVISION (+ Retry) / idempotent RETRY / FAILED beyond AMBIGUOUS CHOICE_SET; SCHEMA 10 unchanged
- **v0.0.138:** Conversation AMBIGUOUS CHOICE_SET cards — GatewayStatus.AMBIGUOUS renders first-class chips for approval/tracker clarification (§17 case 33/21); SCHEMA 10 unchanged
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
- **v0.0.110:** RoleManager (reclaimed from 0.0.95 / 0.0.75) / HOME restore — Set as HOME (RoleManager + Settings), singleTask/clearTaskOnLaunch, Compose HOME restore after unlock/recents (OS-133–134)
- **v0.0.59:** App connection honesty — PackageManager inventory of Duolingo/Chess/Spotify/Strava candidates; AppConnection DISCONNECTED/NOT_SUPPORTED; intention usage SAID/OBSERVED only; partner_* caps stay NOT_SUPPORTED; Play Store deep link when missing
- **v0.0.108:** Four-core Intention exploring visual — Intention space / builder presentation aligned to `v1-intention-exploring.jpg` (question → what we know → empty note); builder triage + provenance honesty preserved; Home / Conversation / SessionCard untouched (reclaimed from 0.0.93 / 0.0.73)
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
- **v0.0.111:** Intention detail coherence (OS-142) — next action, active paths, plan, track, reports, and resources in one coherent Intention Space layout; existing registry/path model only (reclaimed from 0.0.96 / 0.0.78)
- **v0.0.112:** Parent/child intention breadcrumb (OS-141) — Intention Space Path crumbs + child links from local `parentIntentionId` only; no invented dependency graph; drawer card layout left to 0.0.76/0.0.99 (reclaimed from 0.0.77 → 0.0.97 → 0.0.112)

- **v0.0.99:** Intention drawer card layout (OS-139) — reorder, one/two-column width, local shape cover image, priority border, readable title overlay; not mini-apps (OS-140)
- **v0.0.113:** History + Trash in Home / Intention / Drawer overflow menus (OS-143) — not primary tabs; local undo/trash restore only; honest empty states; no cloud sync (reclaimed from 0.0.82)
- **v0.0.114:** Home ↔ Intention drawer short swipe (OS-137) — Compose HorizontalPager inside the launcher; reverse swipe / back returns home; no OS-level gesture overrides (reclaim from 0.0.81)
- **v0.0.115:** OS-144 back navigation — predictive BackHandler policy; Home never finishes; drawer → Home; live Discuss/voice confirm Minimize vs End (never silent hangup; reclaimed from 0.0.83)
- **v0.0.116:** Configurable top screen-time bar (OS-136) — day/focus UsageStats chrome above brand, Settings toggle, independent of floating agent; reclaim from 0.0.84
- **v0.0.117:** Floater system UI OS-156 — overlay yields to recents, gesture nav, permission dialogs, and IME (reclaimed from 0.0.85; additive with Gate D #53 / 0.0.109)
- **v0.0.118:** Home attention OS-135 — today's recommendation, due work, next deadline, and agent attention without clutter; additive to four-core Home visual reclaim (#52 / 0.0.104); reclaimed from 0.0.100 after tip 0.0.101
- **v0.0.119:** CASE-07 chess rating — manual SAID baseline (900→1200) + practice/review log; Chess.com auto-sync `NOT_SUPPORTED` (reclaim; was 0.0.102 / 0.0.67); coexists with CASE-03 research + OS-154 evening triage
- **v0.0.130:** Gate C frontend — Home MIDDAY Keep plan / Defer later → `recordMiddayCheck`; Tracker Correct entry → `CorrectTrackerEntry` (honest `user_correction` source); IntentionFlowStore unchanged
- **v0.0.131:** Gate D1 — triggers / cooldowns / typed CAPABILITY_GAP (SCHEMA 10)
- **v0.0.132:** Gate D2 — OBSERVED vs INFERRED on intervention UI
- **v0.0.133:** Gate D3 — overlay/service interrupt → saved work + notification
- **v0.0.134:** Gate C dedicated Today view — planned · completed · deferred from day cycle / occurrences / sessions; Floater Today + Home link → `TodayScreen` (not Home-only)
- **v0.0.139:** Dark theme design tokens — `DarkIntentColors` + Material `darkColorScheme`, Settings System/Light/Dark (SharedPreferences; SCHEMA stays 10 / migrate stamp-only). Rebased onto 0.0.138 tip after #81; AMBIGUOUS ChoiceSet already on main.
- **v0.0.137:** Gate E frontend leftovers — History NavHost route (Settings / Companion / overflow), local snapshot import UI (same export JSON; stamp migrate only), ParentRollup child-contributions section on Reports
- **v0.0.136:** Gate E §17 remaining gaps — vague health, physical constraint, guided practice, spoken approval ambiguity, interrupted voice; tighten Duolingo approach / cross-intention link / tutor day change / 15-min app-duration
- **v0.0.135:** Gate E breadth + migration — scenario suite (ambiguous / legal research / stale connected data / parent rollups / cancel / retry / process death), real legacy export migrate fixture, deliverable pack

## Shapes

Shared core records (`Intention`, facts, hypotheses, proposals, sessions, trackerEntries). Shape tag: `language` | `marathon` | `business` | `move` | `decision` | `cessation` | `app_use` | `research` | `chess`.

## Capability registry

`CapabilityRegistry` lists what INTENT can do (`available` / `permission_needed` / `not_supported`, etc.). Conversation chips, recurring-session proposals, and `NextActionEngine` recommendations filter through it — e.g. opening an app is available; observing Duolingo lesson completion is not. Language, marathon, business, move, decision, cessation, app_use, research, and chess shapes consult the same registry.

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

`ShapeProjection` drives Intention Space hero/approach/knowledge sections, Home “Your space” subtitles, and Conversation eyebrow/headline from `shapeTag` + phase — same screens, different composition. Chess (v0.0.119) adds SAID tracker modules for rating/practice alongside CASE-03 research checklist composition.


## Chess rating CASE-07 (v0.0.119)

Home **Explore chess rating** seeds a shared-model intention (`shapeTag = chess`) with manual rapid baseline ~900 → goal 1200, evening practice proposal, and SAID `rating_self_report` / `practice_log` / `game_review` entries. Registry `observe_chess_rating` stays **NOT_SUPPORTED** — no Chess.com scrape or fabricated games. Coexists with CASE-03 `research` and OS-154 evening thought triage.

## UsageStats (Stage 6 / v0.0.14)

`UsageStatsProbe` refreshes `usage_stats` in the capability registry from a live AppOps check. On an active SessionCard with a destination package, INTENT shows package-level foreground time when granted, or an honest “Grant usage access” path when not — **never** auto-complete or lesson claims.

## OBSERVED provenance (v0.0.26)

When UsageStats is granted, package foreground for the active session is persisted via `FlowCommand.RecordObservedEvidence` as **OBSERVED** evidence (`observe_app_usage`). SessionCard and post-Complete check-out show “Observed N min in …”; the user can mark it inaccurate. Observation never Completes the run and never claims lesson completion (`observe_lesson_completion` stays `NOT_SUPPORTED`).

## Overlay permission (Stage 6 / v0.0.15)

`OverlayPermissionProbe` refreshes `floating_overlay` from `Settings.canDrawOverlays`. Home shows an honest note and **Grant overlay permission** when needed.

## Floating companion (Stage 6 / v0.0.16)

When overlay is granted, `FloatingAgentService` shows a bubble for the **same** agent as Conversation (`idle` / `attention_needed` / `active_session`; stub labels for capture/think/reply). **Tap** opens Ask (Discuss → Conversation); **long-press** opens the **Ask / Today / Capture** drawer. When overlay is denied, there is **no** fake bubble — use Home **Companion menu**, Discuss, or **Capture thought**.

## Floater system UI clearance (OS-156 / v0.0.117)

`FloaterSystemUiPolicy` clamps the overlay to status / gesture / nav / IME insets and uses `FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCH_MODAL` so the keyboard and system permission dialogs keep focus and outside touches. When the IME leaves no safe band, the bubble hides instead of covering the keyboard. No system-gesture exclusion rects — recents and edge back stay free. Additive with Gate D sticky / overlay-denied honesty (PR #53 / reclaim 0.0.109).

## Notification summaries (Stage 6 / v0.0.22)

`NotificationListenerProbe` refreshes `notification_listener` from enabled listener packages. Active SessionCard shows an honest note and **Grant notification access** when needed. Quiet mode / screen capture stay `NOT_SUPPORTED`. No content retention and no silence claims.

## Thought capture (v0.0.23)

`FlowCommand.CaptureThought` stores a raw `ThoughtCapture` with SAID provenance. Home and floater share one sheet (text or Stage 6 voice). No LLM summary; classification is deferred.

## Undo / trash (v0.0.24)

Home offers **Undo** for the last reversible local commitment (approve / decline / defer / session / trash). Intention Space soft-deletes intentions and sessions into trash with **Restore** (kept until purge) or permanent **Purge**. Opening another app is logged as irreversible — INTENT cannot undo external actions.

## History + Trash menus (OS-143 / v0.0.113)

Home, Intention Space, and the Intention drawer expose **History** and **Trash** from the overflow (⋯) menu — not as primary bottom-nav destinations. History lists local `FlowTransaction` commitments with undo of the last reversible entry; Trash reuses soft-delete restore/purge. Empty states are honest; nothing syncs to the cloud.

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

## Home attention hierarchy (v0.0.118 / OS-135)

Home shows four calm slots derived from the intention snapshot: (1) today's recommendation on the attention strip, (2) next deadline as a quiet line when distinct from the strip, (3) agent attention Discuss row when warranted, (4) “Other things today” when other intentions have due work. Empty slots are omitted — no invented partner data or multi-stat piles. Additive to four-core Home visual reclaim (#52).

## Home attention strip (v0.0.48)

One calm Home strip: next action + Begin/Discuss/Later (or Complete/Abandon). Optional secondary line is session elapsed **or** OBSERVED package minutes when UsageStats is granted — never both, never invented. Grant prompts, companion menu, and session notification rows stay quiet; builder/triage/move explore stay under Your space.

## Intention drawer / hold-for-apps (v0.0.49) · card chrome (v0.0.99)

Home **Intentions** opens an accessible drawer of active intentions (tap → Intention Space). Long-press enables reorder persisted via `FlowCommand.ReorderIntentions` (undoable when undo is LIVE). Intention Space **Apps you hold** lets you confirm package affinity as SAID — no UsageStats auto-ranking, not a classic app grid; the system app drawer stays available. Held apps prefer destination launch over shape defaults.

## Sideload packaging (v0.0.122–v0.0.122)

At evening check-out (after what-happened), RAW SAID thoughts are walked one at a time. User picks **task / intention / reminder / keep / discard** — no LLM invent. Then Keep schedule / Defer tomorrow as before.

## Sideload packaging (v0.0.123–v0.0.124)

Published APKs use `keystore/intent-sideload.jks`, **`isDebuggable = false`**, and **no `QUERY_ALL_PACKAGES`** (destination / connection candidates via `<queries>`). Prefer the **release** artifact `INTENT-v0.0.124.apk` on this branch (main tip: `INTENT-v0.0.123.apk`).

## Voice conversation (Stage 6 / v0.0.18)

Conversation mic uses platform speech recognition when `RECORD_AUDIO` is granted. Transcripts go through `ConversationCoordinator` — the same path as chips (match a chip phrase, or store as text with an honest non-LLM note). Denied mic → use chips.

## App-linked session pause (v0.0.60)

Active sessions with a destination affinity (Spanish → Duolingo) soft-pause the local elapsed timer when UsageStats shows the foreground package is away for >15s. The Home attention strip shows **Away from app**, a pause-aware secondary elapsed line, and **Resume**. Without UsageStats or affinity, the timer keeps the local clock with an honest note. Soft-pause never Completes or claims lesson progress.

## App connections (v0.0.59)

Settings → Connections inventories Duolingo / Chess.com / Spotify / Strava via PackageManager + `<queries>` (never invents installs). `AppConnection` statuses are `DISCONNECTED` / `NOT_SUPPORTED` — unlink is a no-op when disconnected. Intention Space shows which intentions use each connection (SAID holds / OBSERVED foreground only). `partner_spotify` / `partner_chess` / `partner_strava` stay `NOT_SUPPORTED`. Missing candidates offer a source-labeled Play Store deep link.

## Live LLM Discuss (v0.0.61 honesty · v0.0.126 OpenAI · v0.0.127 voice)

Registry capability `live_llm` is **NOT_SUPPORTED** without a key, **DENIED** when a key may exist but the user has not opted in, and **CONNECTED** when key + Settings **Use live OpenAI** opt-in + shipped OpenAI client. Without that, Discuss labels **Local heuristics** and never invents. With CONNECTED, typed free-text **and** unmatched voice/STT free-text both call `api.openai.com` Chat Completions via the same `OpenAiDiscussClient` path; agent turns are labeled **Model suggestion**. Supply keys only as Cursor/CI secret **`OPENAPI_GENERAL`** (env / gitignored `local.properties`) or EncryptedSharedPreferences on device — never commit secrets; public APKs keep BuildConfig empty. Model output must never write OBSERVED or auto-Complete.

## Four-core Home visual (v0.0.104)

Home restores the four-core brand-first composition: ✦ INTENT brand, date, greeting headline + lede, then one next-action attention strip (pills + CTAs). Soft-pause Away/Resume, errands inbox, quiet grants, companion drawer, and Settings → Connections stay intact below the greeting.

## Gate D1 — triggers / cooldowns / capability gaps (v0.0.131)

SCHEMA 10: `TriggerCooldownPolicy` + `UsageInterventionTrigger` via `IntentionFlowStore` — dismiss / leave-me-alone arm cooldown; day-window usage queues ≤1 intervention; typed `CAPABILITY_GAP` ResponseBlocks for Shorts/unavailable; floater ↔ Conversation share intervention turn/job id. Version bumped past #71’s 0.0.130 claim.

## Gate D2 — OBSERVED vs INFERRED intervention labels (v0.0.132)

Every app-usage intervention surface (Home Callout, floater bubble / presence, Conversation QUESTION bubble) always shows **OBSERVED** or **INFERRED** via `InterventionProvenance` + `ProgressProof` / `ObservedProvenance` patterns — never silent mix. JVM: `GateDObservedInferredLabelsTest`.

## Gate D3 — overlay / service interrupt → saved work + notification (v0.0.133)

Overlay revoke or process death while the floating companion was active stops the bubble (never invents overlay grant), keeps `IntentionFlowStore` mutations (atomic persist + `.tmp` recovery), and posts a **permitted** notification that opens Conversation — reusing intervention turn/job ids when active. JVM: `GateDOverlayInterruptTest` + `FloatingAgentRestartPolicyTest`. Cloud KVM proof under Project `media/gate-d/`.

## Download

Sideload APK (v0.0.154, release): [INTENT-v0.0.154.apk](https://github.com/DanielMaherDev/Intent-V2/raw/cursor/fe-a2-explanation-progress-fdda/artifacts/INTENT-v0.0.154.apk)

(Prior tip: [INTENT-v0.0.153.apk](https://github.com/DanielMaherDev/Intent-V2/raw/main/artifacts/INTENT-v0.0.153.apk) after #99.)
