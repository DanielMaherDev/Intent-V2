# Redesign + §8 leftovers — 0.0.165

**Branch:** `cursor/redesign-and-s8-gaps-4552` · **Version:** 0.0.165 / 165  
**Authority:** uploaded `INTENT_Design_With_Screenshots` → `docs/design/intent-redesign-2026-11/DESIGN_SPEC.md`  
**Version lock:** 0.0.164 = Propose-path (IV-197); **this slice = 0.0.165**

## Shipped

| Area | Change |
|------|--------|
| Tokens | `LightIntentColors` aligned to DESIGN_SPEC (`#F6F4F0` paper, `#5C4DFF` brand, ink / line / brandSubtle) |
| Brand | Diamond PNG + brand-colored wordmark (`IntentBrandMark`) |
| S-HOME-EMPTY | No hero card; emphasized Start CTA + sparkle; todo / capture icon rows; no mid-page overlay essay / ERRANDS header; bell + overflow (Settings in overflow) |
| Primary button | 52/56 height, 14/16 radius, optional leading icon |
| §8.9 Cancel | `cancelDiscussThinking()` + **Cancel** banner while `freeTextBusy`; late replies ignored; `CommandGateway.cancelTurn` marker |
| Spec in repo | `docs/design/intent-redesign-2026-11/{DESIGN_SPEC,README,tokens}.json/md` |
| Icons | `res/drawable/logo_diamond.png` (+ nav/action PNGs staged) |
| APK | `artifacts/INTENT-v0.0.165.apk` |

## Out of scope / follow-ons

- Full screen-by-screen redesign (Settings, Intention Space, Builder copy bans, nav icon swap) — iterate after empty-home land
- §8.4 Review AVD — owned by Propose-path **0.0.164** / fork
- Remaining §8 AVD (freshness month skew, multimodal STT, research, date correction, cross-surface after approve) — continue after this PR installs

## Evidence

- Store media: `media/redesign-165/00-home-empty.png`
- Adaptive pack: `media/adaptive-builder/de-slice/15-redesign-home-empty-165.png`

Updated: 2026-11-21
