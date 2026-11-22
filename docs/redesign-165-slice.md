# Redesign + §8 leftovers — 0.0.167

**Branch:** `cursor/redesign-and-s8-gaps-4552` · **Version:** 0.0.167 / 167  
**Authority:** uploaded `INTENT_Design_With_Screenshots` → `docs/design/intent-redesign-2026-11/DESIGN_SPEC.md`  
**Version lock:** tip **0.0.166** (#111 SessionMetaGrid); **this slice = 0.0.167** (was claimed as 165; rebased past 166)

## Shipped

| Area | Change |
|------|--------|
| Tokens | `LightIntentColors` aligned to DESIGN_SPEC (`#F6F4F0` paper, `#5C4DFF` brand, ink / line / brandSubtle) |
| Brand | Diamond PNG + brand-colored wordmark (`IntentBrandMark`) |
| S-HOME-EMPTY | No hero card; emphasized Start CTA + sparkle; todo / capture icon rows; no mid-page overlay essay / ERRANDS header; bell + overflow (Settings in overflow) |
| Primary button | 52/56 height, 14/16 radius, optional leading icon |
| §8.1 freshness | Create utterance with relative move → anchored `KEY_MOVE_ESTIMATE`; Intention Space reconfirm hero after elapsed time |
| §8.9 Cancel | `cancelDiscussThinking()` + **Cancel** banner while `freeTextBusy`; late replies ignored; `CommandGateway.cancelTurn` marker |
| Spec in repo | `docs/design/intent-redesign-2026-11/{DESIGN_SPEC,README,tokens}.json/md` |
| Icons | `res/drawable/logo_diamond.png` (+ nav/action PNGs staged) |
| S-SETTINGS | Appearance / Motion / Assistant (“Use a live assistant”) / Quiet hours / History / Connected apps; key field only when opted in; no schema/heuristic primary copy |
| APK | `artifacts/INTENT-v0.0.167.apk` |

## Non-regression

- Does **not** modify `ProposalReviewScreen` / `SessionMetaGrid` 4-cell fix from **0.0.166** (#111).

## Out of scope / follow-ons

- Full screen-by-screen redesign (Settings, Intention Space, Builder copy bans, nav icon swap) — iterate after empty-home land
- Remaining §8 AVD (freshness month skew, multimodal STT, research, date correction) — continue after this PR

## Evidence

- Store media: `media/redesign-165/00-home-empty.png`
- Adaptive pack: `media/adaptive-builder/de-slice/15-redesign-home-empty-165.png`

Updated: 2026-11-22 (S-SETTINGS pass)
