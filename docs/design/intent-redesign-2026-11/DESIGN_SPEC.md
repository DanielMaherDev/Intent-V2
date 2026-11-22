# INTENT design specification

Implementation source of truth. 21 November 2026.

This document covers every screen discussed in the redesign thread: the eight first-version screens, the 22 live end-to-end captures, and every redesign. Image mocks are references. Where a mock disagrees with this file, this file wins.

Reference frame: 1080 × 2400 px, xxhdpi. 1 dp = 3 px. Logical canvas 360 × 800 dp. All numbers below are dp unless marked sp.

Machine-readable tokens: [tokens/tokens.json](tokens/tokens.json).
Per-screen structured records: [screens/screens.json](screens/screens.json).
One file per icon: [icons/catalog.json](icons/catalog.json).

---

## 1. Locked decisions

1. The agent classifies the intention. The user does not pick a shape. Do not ship Language / Marathon / Business / Other as a required step. The shape-selection mock is rejected. See screen R-CLASSIFY.
2. No developer language anywhere a person can see. Banned strings include: heuristic, heuristics, NO LIVE LLM, SAID, OBSERVED, NOT_SUPPORTED, QUIET_MODE, SessionCard, local.properties, CASE-, schema, snapshot data, Animator duration scale.
3. No floating badge over content. The purple “INTENT T” chip in the first-version captures is a defect. Remove it.
4. Home has one primary action. Permission explanations, companion links, and errand education do not sit in the middle of home.
5. Self-report numbers use a scroll wheel, one question per screen, with a step count. Not a free-text field.
6. Opening an app is not progress. Never write that time in an app completed a lesson, a run, or a quit attempt.
7. Plain language. “Start an intention”, “Add a quick to-do”, “Capture a thought”, “What we know”, “You told us”.
8. Font is Roboto. Do not add a display font.
9. Icons are 24 dp, stroke 1.75, round caps. Source SVGs use `currentColor`. Brand PNG is #5C4DFF. Ink PNG is #161616.
10. Cards are white with a 1 dp #E6E1D8 border. No drop shadows on the paper background.

---

## 2. Color

| Token | Hex | Use |
|---|---|---|
| paper | #F6F4F0 | Screen background |
| surface | #FFFFFF | Cards, fields, sheets, grouped lists |
| surfaceMuted | #F3F0EA | 40 dp icon wells, wheel disc |
| brand | #5C4DFF | Primary fill, wordmark, active nav, links, selected stroke |
| brandPressed | #4A3DE0 | Primary pressed |
| brandSubtle | #EEEBFF | User chat bubble, optional selected wash |
| ink | #161616 | Headlines, body, default icons |
| inkSecondary | #5E5A55 | Subtitles, helpers |
| inkTertiary | #8A857E | Placeholders, captions, inactive nav, chevrons |
| line | #E6E1D8 | Borders, dividers |
| lineStrong | #D4CEC3 | Wheel ticks |
| onBrand | #FFFFFF | Label on brand fill |
| scrim | #161616 at 40% | Behind the companion sheet |

Contrast: ink on paper and onBrand on brand both clear WCAG AA for body and large text. inkTertiary is for captions only, never for the only copy of an action.

Current app (observed, not the target) is close but not exact: background about #F4F2EE, buttons about #5B4CE0 to #5A46E8. Do not sample the screenshots. Use the tokens.

---

## 3. Typography

Family: Roboto. Weights: 400 regular, 500 medium, 600 semibold, 700 bold.

| Style | Size | Line | Weight | Tracking | Color |
|---|---|---|---|---|---|
| display | 34 sp | 40 | 700 | -0.02 em | ink |
| title | 28 sp | 34 | 700 | -0.015 em | ink |
| titleSm | 22 sp | 28 | 700 | -0.01 em | ink |
| section | 18 sp | 24 | 600 | 0 | ink |
| body | 16 sp | 24 | 400 | 0 | ink |
| bodySecondary | 16 sp | 24 | 400 | 0 | inkSecondary |
| bodyMedium | 16 sp | 24 | 500 | 0 | ink |
| button | 16 sp | 20 | 600 | 0 | onBrand or brand |
| label | 14 sp | 20 | 400 | 0 | inkSecondary |
| caption | 13 sp | 18 | 400 | 0 | inkTertiary |
| overline | 12 sp | 16 | 500 | 0.08 em, uppercase | inkTertiary |
| nav | 11 sp | 14 | 500 | 0 | inkTertiary, brand when active |
| wordmark | 18 sp | 22 | 600 | 0.04 em | brand |
| counter | 12 sp | 16 | 400 | 0 | inkTertiary |

Do not set all-caps on sentences. Overline is the only uppercase style, and it is short: dates, “YOUR SPACE” is retired, do not use it.

---

## 4. Spacing, radius, chrome

Space scale: 4, 8, 12, 16, 20, 24, 28, 32, 40, 48.

| Token | dp |
|---|---|
| Screen horizontal padding | 20 |
| App bar height | 56 |
| Bottom nav height | 64, plus the system gesture inset |
| Minimum touch | 48 |
| Primary button, standard | 52 tall, radius 14 |
| Primary button, home emphasis | 56 tall, radius 16 |
| List row | 56 |
| Icon | 24 |
| Icon well | 40 circle, fill surfaceMuted |
| Field radius | 16 |
| Card radius | 16 |
| Sheet top radius | 20 |
| Hairline | 1 |
| Selected stroke | 2 |

App bar contents, vertically centered in the 56 dp bar:

- Back icon 24, left inset 16, tint ink. Hit target 48.
- Diamond logo 22, then 8, then wordmark.
- Screen name (Builder, Settings) 16 sp / 500, inkTertiary, right inset 16.

Bottom nav, three equal columns:

- Home, icon `ic_home`, label “Home”
- Intentions, icon `ic_intentions`, label “Intentions”
- Agent, icon `ic_agent`, label “Agent”
- Icon 24, gap 4, label nav style.
- Active: icon and label brand, weight 600.
- Inactive: icon and label inkTertiary.
- Top hairline 1 dp line.
- No center FAB.

Pressed: primary fill becomes brandPressed. Rows get a 4% ink wash. No scale animation required.

---

## 5. Components

### Primary button

Height 52 (56 on empty home). Full width inside the 20 dp screen padding. Fill brand, radius 14 (16 on the 56 dp home button). Label button style, onBrand, centered. Optional leading icon 18 dp, gap 8, tint onBrand. Pressed: brandPressed. Disabled: fill line, label inkTertiary, not tappable.

### Text button

No fill, no border. Label 16 sp / 600, brand, centered. Hit height 48. Used for Cancel, Change, Edit.

### Text field

Min height 120 for the intention prompt, 48 for a single line (API key). Fill surface, border 1 dp line, radius 16, padding 16. Placeholder 16 sp / 400 inkTertiary. Value 16 sp / 400 ink. Focus: border 2 dp brand, no glow. Counter, when the field has a max: 12 sp inkTertiary, bottom-end, inside the padding. Key field is obscured (password transformation).

### Grouped list

One surface card, radius 16, border 1 dp line, clip children. Rows 56 tall, padding horizontal 16. Label bodyMedium. Trailing icon 20, inkTertiary, or a 20 check in brand when selected. Divider 1 dp line, inset 16 from the left, not on the last row.

### Icon row (home secondary)

Height 56. Not inside a card. Leading well 40, icon 20 brand. Gap 12. Label bodyMedium. Trailing `ic_chevron_right` 20, inkTertiary. Pressed wash as above.

### Section header

18 sp / 600 ink. Margin top 24 from the previous block, margin bottom 12 before the card. No all-caps.

### Toggle row

Same row height. Label bodyMedium. Helper 14 sp / 400 inkSecondary, directly under the label, 4 dp gap; the row grows to fit, padding vertical 12. Switch is the platform Material switch. Track off: line. Track on: brand. Thumb: surface.

### Progress steps

Three (or N) circles, 28 dp. Active fill brand, numeral 13 sp / 700 onBrand. Upcoming fill line, numeral 13 sp / 600 inkTertiary. Connector: 2 dp line, vertically centered, flex between circles. Margin horizontal matches screen padding.

### Number wheel

Card: surface, radius 20, border 1 dp line, padding 20, margin top 24.
Ruler height 88. Major ticks 16 tall, 1 dp lineStrong. Minor ticks 8 tall.
Values in a horizontal scroller, snap to center. Selected value sits in a 64 dp disc, fill surfaceMuted, numeral 28 sp / 700 ink. Neighbors 18 sp / 500 inkTertiary. One neighbor visible each side, then an ellipsis if the range continues.
Under the ruler, centered: the resolved phrase, 16 sp / 500 ink (“20 minutes per day”). Under that, 13 sp / 400 inkTertiary: “Scroll or tap to choose”.
Ranges are finite and labeled. Do not accept typed numbers on this screen.

### Chat bubbles

Agent: no bubble. Body text, ink, 16/24, start-aligned, max width 100% of the content column.
Person: fill brandSubtle, radius 16, padding 12 × 16, text ink, end-aligned, max width 85%.
Action card under a turn: surface, radius 16, border 1 dp line, padding 16. Title bodyMedium. Reason label. One text button, start-aligned, “Edit”.

### Knowledge row

Height at least 64, padding vertical 12, horizontal 16. Title bodyMedium. Second line label, inkSecondary: “You told us” or “From Duolingo · observed”. Trailing text button “Edit”, 14 sp / 600 brand. Divider inset 16.

### Companion sheet

Scrim 40% ink. Sheet surface, top radius 20, padding top 8, horizontal 8, bottom 24 plus gesture inset. Grabber: 36 × 4, radius pill, fill line, centered, margin bottom 8. Rows 56, icon 24 ink, gap 16, label bodyMedium. No card around each row.

---

## 6. Icons

Each icon is its own file. SVG is the source (`icons/svg/<id>.svg`, 24×24 viewBox, `currentColor`). PNGs are transparent, brand tint, at 24, 48, 96, 256 (`icons/png/<size>/<id>.png`). Ink tint copies are in `icons/png-ink/<size>/`.

| id | Draw | Default tint | Where |
|---|---|---|---|
| logo_diamond | Filled diamond, brand only | #5C4DFF | App bar, empty-home header. 28 dp on home, 22 dp in the app bar |
| ic_home | House | ink / brand when active | Bottom nav |
| ic_intentions | Two rings and a center dot | ink / brand when active | Bottom nav. Replaces the old stacked-diamond nav icon |
| ic_agent | Large sparkle plus two small | ink / brand when active | Bottom nav, Agent |
| ic_back | Arrow left with shaft | ink | App bar |
| ic_chevron_right | Chevron | inkTertiary | Rows |
| ic_chevron_left | Chevron | ink | Not in these screens; included for back-affordance parity |
| ic_more | Three vertical dots | ink | Menus if a screen still needs them. Empty home does not |
| ic_settings | 8-tooth gear | ink | Only if a screen has no room for the word Settings. The empty-home gear is removed |
| ic_bell | Bell | inkSecondary | Empty-home header, 24 dp |
| ic_check | Check | brand or onBrand | Selected list row, suggested card |
| ic_check_circle | Check in a circle | brand | Quick to-do well, confirmed |
| ic_plus | Plus | brand | Not on the current targets |
| ic_plus_circle | Plus in a circle | brand | Not on the current targets |
| ic_lightbulb | Bulb | brand inside a well | Capture a thought |
| ic_sparkle | Single 4-point spark | onBrand | Inside the home primary button, 18 dp |
| ic_globe | Globe | brand | Language confirmation only, not a picker row |
| ic_shoe | Shoe | ink | Do not use. Shape picker is rejected |
| ic_briefcase | Briefcase | ink | Do not use. Shape picker is rejected |
| ic_circle_dotted | Dotted ring | ink | Do not use. Shape picker is rejected |
| ic_edit | Pencil | brand | “Edit” may be text instead. Icon is available |
| ic_trash | Bin | ink | Trash menu, not a primary action |
| ic_pause | Two bars | ink | Pause an intention, menu |
| ic_chat | Bubble | ink | Companion “Ask agent”, intention Discuss |
| ic_mic | Microphone | ink | Voice capture. Text fallback remains |
| ic_calendar | Calendar | ink | Dates inside an intention, not a new nav item |
| ic_link | Link | ink | Connected apps |
| ic_apps | Four tiles | ink | Apps list |
| ic_close | X | ink | Dismiss sheet |
| ic_info | i in a circle | inkTertiary | Only beside a limitation, never as a paragraph |
| ic_undo | Hook arrow | ink | Undo snackbar |
| ic_clock | Clock | ink | Timing on a recommendation, optional |
| ic_flag | Flag | ink | Not required on these screens |

Nav icons are 24 dp. Do not stroke them heavier than 1.75. Do not place them on a colored square except the 40 dp surfaceMuted well on home secondary rows.

---

## 7. File inventory — live captures, 21 Nov

These files are the current build. Several are the same screen.

| File | What it is | Treat as |
|---|---|---|
| 00-android-home-proof.png | Android system “Home app” picker. INTENT is listed under the system title. Pixel Launcher is selected. | Not an INTENT screen. No redesign. |
| 01-home-launcher.png | Empty home, 9:21 | Canonical empty home. Spec S-HOME-EMPTY |
| 01b-empty-home-ready.png | Same layout, 9:23 | Duplicate of 01 |
| 02-settings-open.png | Settings, appearance and motion, top | Canonical settings top. Spec S-SETTINGS |
| 02b-settings-live-openai-section.png | Settings scrolled: quiet mode, history, live key | Canonical settings key section |
| 02c-settings-after-key-optin.png | Same as 02b after opt-in. Switch on, key masked `•••…z2wA`, “Use live OpenAI” checked | State of the key section |
| 02d-home-after-settings.png | Empty home again | Duplicate of 01 |
| 02e-settings-after-clipboard-key.png | Same bytes as 02g (236271). Key section, switch on | Duplicate of 02c |
| 02g-settings-optin-final.png | Identical to 02e | Duplicate |
| 03-home-before-create.png | Empty home | Duplicate of 01 |
| 04-builder-open.png | Builder, empty field, 9:30 | Canonical builder empty. Spec S-BUILDER |
| 10-home-after-embed-install.png | Empty home | Duplicate of 01 |
| 11-settings-open-embed.png | Settings top | Duplicate of 02 |
| 12-settings-live-status-before-optin.png | Key section, switch off, empty key field | Same as 02b |
| 13-settings-after-optin.png | Key section, switch on, masked key | Same as 02c |
| 14-home-live-ready.png | Empty home | Duplicate of 01 |
| 15-builder-open.png | Builder empty | Duplicate of 04 |
| 16-builder-typed.png | Builder, text entered, keyboard open | Spec S-BUILDER-TYPED |
| 17-builder-shape-screen.png | Shape list. 166288 bytes | Spec S-SHAPE-CURRENT |
| 18-builder-shape-chosen.png | Same bytes as 17 | Not a distinct state. The file name is wrong |
| 19-builder-step1.png | Same bytes as 17 | Not a new step |
| 19-builder-step2.png | Same screen, 166210 bytes | Not a new step |

The builder never advanced. Step 1 and step 2 are the shape list again.

---

## 8. Screen specs

### S-SYS — Android Home picker

File: `00-android-home-proof.png`.

This is the system UI. INTENT does not draw it. Do not restyle it. The only product requirement is that INTENT appears in this list under its own name and can be set as Home. No icon change is specified here.

### S-HOME-EMPTY — current

Files: 01, 01b, 02d, 03, 10, 14.

Background paper. Status bar is the system (9:21 on 01).

Top row: diamond  about 18 dp, gap, wordmark “INTENT” about 14 sp medium, ink (the current wordmark is black, not brand). Right: 40 dp circle with `ic_more`, 8 dp gap, 40 dp circle with `ic_settings`. Both circles are white.

Below, inset 20: overline “SATURDAY · 21 NOVEMBER”, about 12 sp, tracking wide, inkSecondary.

Display “Where would you like to begin?” black, bold, two lines.

Body “No active intention yet — nothing is scheduled.” inkSecondary.

Card, white, radius about 16, top hairline in brand:
- Overline “YOUR SPACE” in brand, about 11 sp.
- Title again “Where would you like to begin?” about 22 sp bold.
- Same subtitle again.
- Button, full width inside the card, height about 48, radius about 12, fill near brand, label “Start a new intention” 16 sp medium, onBrand.

Then a paragraph, 14 sp inkSecondary, four lines:

“Overlay permission not granted — no floating bubble. Use Home’s Companion menu, Discuss, or Agent for the same Conversation. Grant overlay only from system Settings if you want the bubble.”

Then three centered links, 16 sp medium brand, stacked with about 28 dp between them:

- Grant overlay permission
- Companion menu
- Capture thought

Section label “ERRANDS”, 12 sp tracking wide, inkTertiary.

Row: 40 dp well, pale brand wash, small check-plus glyph. Title “Add a quick to-do” bodyMedium. Subtitle “Small errand with a reminder — not an intention” caption. Trailing chevron.

Near the bottom, another “YOUR SPACE” label with nothing under it.

Bottom nav as in section 4, except the Intentions icon is the old stacked diamond, and Home is brand.

Problems, all to be removed: duplicated headline, the permission paragraph, three mid-page links, the errand lecture, the empty YOUR SPACE label, the two top-right icon buttons.

### S-HOME-EMPTY — target

State: no active intention and nothing due.

- Background paper. No card around the headline.
- Header, top inset 8 below the status bar, height 48, padding horizontal 20.
  - `logo_diamond` 28 dp.
  - Gap 8.
  - Wordmark “INTENT”, wordmark style.
  - `ic_bell` 24, tint inkSecondary, end-aligned. Hit 48. This is a placeholder for later attention. It does not open settings.
  - Remove the more button and the gear. Settings stays reachable from the system or from a later account menu, not from this empty state.
- Overline, margin top 28: “SATURDAY · 21 NOVEMBER”.
- Display, margin top 8: “Where would you like to begin?”
- bodySecondary, margin top 8: “Nothing scheduled yet.”
- Primary button, margin top 28, height 56, radius 16. Leading `ic_sparkle` 18. Label “Start a new intention”.
- Icon row, margin top 20: well + `ic_check_circle` + “Add a quick to-do” + chevron.
- Icon row, margin top 4: well + `ic_lightbulb` + “Capture a thought” + chevron.
- Nothing else above the nav. No errands header. No overlay copy.
- Bottom nav, Home active.

### S-HOME-ACTIVE — target

This is the other home state, from the first-version redesign. Use it when there is a recommended next action. Do not also show the empty-state headline.

- Same header as empty home (diamond, wordmark, bell).
- Overline: the date, same as empty.
- Display: “Today”.
- bodySecondary, margin top 8: one sentence on why this is the recommendation. Example: “Speaking practice is the next useful step.”
- Card, margin top 20, surface, radius 16, border 1 dp line, padding 16.
  - Title, titleSm: the action. Example “Practise Spanish speaking”.
  - Label, margin top 4: “About 15 minutes”.
  - Reason, label, margin top 8: one line. Example “You said you want everyday conversation.”
  - Primary button, margin top 16, height 52: “Begin now”. No sparkle.
  - Two text buttons in a row, margin top 4, split the width: “Later today” and “Discuss”. Both link style. Hit height 48.
- Caption, margin top 16, start-aligned: “2 other actions today”. Trailing chevron. The whole line is one target, height 48. The count is today’s eligible actions, not the backlog.
- Bottom nav, Home active.

If the count is 0, omit the caption. If the only work is discovery, the card title is “Decide the next step” and the button is “Continue”, not “Begin now”.

### S-BUILDER — current

Files: 04, 15.

App bar: back, diamond, “INTENT”, end label “Builder”.

Overline “NEW INTENTION” brand.

Display “Start an intention”.

Paragraph, bodySecondary:

“Describe what you want. INTENT suggests a shape with labeled heuristics — not a live LLM — then asks only a couple of useful questions.”

Overline brand: “HEURISTIC SHAPE SUGGESTION — NO LIVE LLM.”

Card:
- Caption “WHAT DO YOU WANT TO WORK ON?”
- Field, min height about 96, placeholder “e.g. Speak Spanish more confidently at work”.
- Primary “Continue”, about 48 tall.

Text button “Cancel builder”.

Large empty paper below the card.

### S-BUILDER — target

- App bar as in section 4. End label “Builder”, 16 sp / 500 inkTertiary.
- No overline.
- Display, margin top 16: “Start an intention”.
- bodySecondary, margin top 12, two lines max: “Tell us what you want to work on. We’ll ask a few useful questions.”
- Field, margin top 24, min height 120, placeholder “e.g. Speak Spanish more confidently at work”. No label above the field.
- Primary “Continue”, margin top 20, height 52. Disabled until the trimmed text is at least 3 characters.
- Text button “Cancel”, margin top 4.
- No card wrapper. No heuristic line. No “NEW INTENTION”.

### S-BUILDER-TYPED — current

File: 16. Same chrome and banned copy as the empty builder. Field contains “Learn Spanish for everyday conversation” with the last word underlined by the spellchecker. Keyboard open, suggestion bar showing conversation / conversations / conversational. Continue is enabled.

### S-BUILDER-TYPED — target

Same layout as the empty target. Differences:

- Field value, body, ink: the person’s text. No spellcheck underline required.
- Counter inside the field, bottom end: “38/60” in counter style. Max 60 characters for the title. If they pass 60, the counter turns danger and Continue stays disabled.
- Keyboard is the system keyboard. Do not draw a custom one.
- Continue enabled.

On Continue, go to classification. Do not open the shape list.

### S-SHAPE-CURRENT — do not keep

Files: 17, 18, 19-builder-step1, 19-builder-step2. They are the same screen.

Still shows the builder intro and “HEURISTIC SHAPE SUGGESTION — NO LIVE LLM.”

Card title “SUGGESTED SHAPE: LANGUAGE”. Subtitle “Learn Spanish for everyday conversation”.

Four outline buttons, about 48 tall, radius 12:

- “Language (suggested)” — 2 dp brand stroke, label brand.
- “Marathon / race”
- “Business”
- “Other”

Orange caption: “Heuristic shape suggestion — no live LLM. Suggested shape: language.”

“Cancel builder”.

18 is not a selected state. The pixels match 17. There is no step 2.

### R-CLASSIFY — target (replaces the shape screen)

The agent assigns a kind from the sentence. The person is not asked to choose among unrelated kinds.

High confidence (the Spanish sentence): no screen. Advance to question 1. The kind is stored. A one-line confirmation can appear later inside the intention, not as a gate: “Treated as a language intention.” with a text button “Change”.

Low confidence, only if the sentence is ambiguous: one screen, not a menu of four products.

- App bar, end label “Builder”.
- Progress hidden. This is not a question.
- Title, title style: “Is this about learning a language?”
- bodySecondary: the person’s sentence, quoted on the next line.
- Primary, margin top 28: “Yes, continue”.
- Text button: “No, I’ll describe it again”. Returns to the builder field with the text kept.

Do not list Marathon, Business, or Other. Do not use `ic_shoe`, `ic_briefcase`, or `ic_circle_dotted` on this flow. The globe icon is optional, 24 dp, brand, 12 dp above the title, and may be omitted.

The generated mock that shows “What kind of intention is this?” plus four cards is rejected. It contradicts the decision that classification is the agent’s.

### S-QUESTION — target (new; the wheel)

There is no current-build screenshot of a real question. 19-builder-step1 and step2 are the shape list. This screen is the replacement.

Example question for the Spanish intention. Other quantities use the same component with a different title, unit, and range.

- App bar: back, title “INTENT” wordmark only. No “Builder” on the end. Back returns to the previous question or the prompt.
- Progress, margin top 8: step 1 of 3. See Progress steps.
- Title, margin top 28: “How often can you practise?”
- bodySecondary, margin top 8: “We’ll build a simple routine around this.”
- Number wheel. Domain for this question: 5, 10, 15, 20, 30, 45, 60. Default 20. Unit phrase “{n} minutes per day”.
- Primary pinned 16 dp above the gesture inset: “Continue”, height 52.

Question 2 and 3 are separate screens, same chrome, progress advanced. Ask only what changes the next action. For this intention the next two are:

2. “Where do you want to use it?” Single choice rows, not a wheel. Options: “Everyday conversation”, “Work”, “Travel”, “Not sure yet”. Row height 56, selected row 2 dp brand border.
3. “How do you want to practise?” Single choice: “A little every day”, “Longer sessions a few times a week”, “I already have a tutor or app”.

Do not ask all three if the sentence already answered one. “Learn Spanish for everyday conversation” already answers question 2. Skip it and show 2 steps, not 3.

After the last answer, do not show a generated plan. Open the intention with a discovery or a single next action.

Wheel values for other self-reports, same component:

| Intention | Question | Values | Phrase |
|---|---|---|---|
| Cigarettes | “About how many a day right now?” | 0–40 step 1 | “{n} a day” |
| Marathon | “About how far is a typical run?” | 1–30 | “{n} km” |
| Screen limit | “What daily limit do you want?” | 15, 30, 45, 60, 90 | “{n} minutes” |

0 is a valid cigarette answer. It is not the same as “not answered”.

### S-SETTINGS — current, top

Files: 02, 11.

App bar: back, diamond, INTENT, “Settings”.

Display “Settings”.

Paragraph:

“Local device tools only. Connections inventory partners; snapshot export/import stays on this device. Live OpenAI stays off until you opt in with a key on this device.”

Section “APPEARANCE”.

Paragraph about follow system, lock light / dark, high-contrast tokens, HC palette, “not a fake checkbox over the standard theme.”

Then four full-width buttons, about 52 tall, radius 12, stacked with 12 dp gaps:

- “System (follow device)” — fill brand, label onBrand. This is the selected one.
- “Light” — surface, border line, label ink.
- “Dark” — same.
- “High contrast (follow device light/dark)” — same.

Micro line, tracking wide: “STORED ON DEVICE ONLY · DOES NOT CHANGE SCHEMA OR SNAPSHOT DATA”.

Section “Motion”.

Paragraph naming Animator duration scale, Accessibility, Remove animations, Agent-module card, Home ↔ Intention pager.

Buttons:

- “Follow system (Animator duration scale)” — brand fill.
- “Prefer reduced motion” — surface.

### S-SETTINGS — current, scrolled

Files: 02b and 12 are switch off. 02c, 02e, 02g, 13 are switch on with a masked key. 02e and 02g are the same file.

Block title “NOT SUPPORTED · QUIET_MODE” in brand.

Paragraph beginning “INTENT has no interruption filter — quiet_mode stays not supported…” through “No Settings silence toggle in this build.”

Button “Open system Do Not Disturb”, surface.

Micro: “SYSTEM DND ONLY — INTENT DOES NOT CLAIM SILENCE WAS APPLIED.”

Section “LOCAL HISTORY”. Button “Open History”.

Section “LIVE OPENAI DISCUSS”.

Paragraph: “No LLM API key configured. Discuss uses labeled local heuristics (scripted chips / templates) — not a live model. Never writes OBSERVED from model output; never auto-completes sessions.”

Status line brand: “STATUS · LOCAL HEURISTICS · KEY MISSING”. On the opted-in shots this area is replaced by the checked control.

Row “Use live OpenAI” with a switch. Off in 02b/12. On in 02c/13, and the label shows a check plus “Use live OpenAI”.

All-caps paragraph beginning “NO NETWORK CALL UNTIL THIS IS ON…” through “NEVER PASTE SECRETS INTO CHAT AGAIN.” It names gitignored local.properties.

Field placeholder “Runtime OpenAI API key”. On opted-in shots the value is a masked key ending `z2wA`.

Button “Save key on device”, brand fill.

Button “Clear on-device key”, surface.

Section label “APP CONNECTIONS” at the bottom edge. The connections list is not in these shots.

### S-SETTINGS — target

One screen, scrollable. App bar as specified, end label “Settings”.

Display “Settings”, margin top 8.

No intro paragraph.

Section “Appearance”. Grouped list, four rows, single selection:

- System
- Light
- Dark
- High contrast

Selected row shows `ic_check` 20, brand, trailing. Default selection: System. Row height 56. No explanatory paragraph. No “stored on device” microcopy.

Section “Motion”. Grouped list, two rows, same selection treatment:

- Match this phone
- Reduce motion

Default: Match this phone. Do not name Animator, pager, or cards.

Section “Assistant”. Not “Live OpenAI”.

Toggle row: “Use a live assistant”. Helper: “Optional. Your key stays on this device.” Switch off by default.

When the switch is off, the key field, save, and clear are not on screen.

When the switch is on, single-line field, margin top 12, height 48, placeholder “Key”, transformation password. Primary “Save”, margin top 12, height 52. Text button “Remove key”.

Do not mention heuristics, OBSERVED, chat paste warnings, local.properties, or git. Do not show a status line.

Section “Quiet hours”. One row, not a paragraph. Title “Open Do Not Disturb”. Label “INTENT cannot silence notifications itself.” Trailing chevron. The row opens the system screen. No “NOT SUPPORTED” banner.

Section “History”. One row “See history”, chevron. No “LOCAL HISTORY” label.

Section “Connected apps”. One row “Apps you use”, chevron, opens S-APPS. If the list is empty, the destination says so. Do not leave a bare heading at the bottom of settings.

### S-DRAWER — first version, current

Side-by-side `redesigns/2_drawer_side_by_side.jpg`, left pane.

App bar: diamond, INTENT, gear.

Overline “INTENTIONS”.

Display “Your intentions”.

Control row, three text buttons: “Reorder”, “Columns”, and a columns icon.

Seven cards, white, radius about 16, padding 16, stacked with about 12 dp. Each has a title and a status line:

| Title | Status line |
|---|---|
| Learn Spanish | Exploring |
| Quit smoking | Active |
| Barcelona to Valencia | Planning |
| Chess rating | Active |
| Less Instagram | Active |
| Job offer | Deciding |
| Side business | Exploring |

Gear at the top right. No search. No bottom nav in that capture (the drawer may be a paged surface).

Floating badge present in the original family; if it appears here it is the same defect.

### S-DRAWER — target

- Header: back or the swipe-back affordance, wordmark, no gear.
- Title, title style: “Intentions”. No overline.
- Search field, margin top 16, height 48, radius 14, placeholder “Search”, leading no icon or `ic_edit` omitted. This field filters titles only.
- One control, end-aligned text button under the search: “Reorder”. Columns is a display setting, not a primary control. Default one column. Do not show a grid icon on this screen.
- Cards, margin top 12, gap 12. Surface, radius 16, border 1 dp line, padding 16, min height 72.
  - Title bodyMedium, max 2 lines, then ellipsis.
  - Status label, caption: one of Exploring, Active, Planning, Deciding, Paused. These are the only status words.
- No images on the card until a person sets one. No priority border in this version.
- Tapping a card opens S-INTENTION.
- Bottom nav, Intentions active, if this screen is a root tab. If it is reached by a swipe from home, the nav stays.

### S-CHAT — first version, current

Left pane of `1_conversation_side_by_side.jpg`.

Header “Agent” with a chat glyph and an info glyph. Gear top right.

Thread title “Work legally in Spain”. Meta “10 turns”.

Purple badge “INTENT T” overlapping the first bubble. Remove.

User bubble, brand fill, white text: “EU citizen living in Spain. I want to work legally.”

Agent bubble, white card: “CASE-03 — Work legally in Spain. I can track the steps you actually have to take. I won’t invent a visa outcome.”

A second agent card titled “What we know” whose body is about a language intention (“Practise a language for 15 minutes…”). That body does not belong on this intention. It is a content bug, not a layout to preserve.

Footer: “EVIDENCE · local-only · no model calls in this build.” A check chip “SAID”. A clock chip.

Composer: rounded field “Message”, send arrow.

### S-CHAT — target

- App bar: back, title of the open intention in bodyMedium, one line, ellipsis. End: text button “Info” only if a source sheet exists. No gear. No turn count in the header.
- No badge. No footer. No SAID chip. No “no model calls”.
- User bubble: brandSubtle fill, ink text, radius 16, end-aligned. Text: the person’s words, unchanged.
- Agent text: no CASE id. Example for this intention: “I can help you track the steps that apply to you. I can’t decide a visa or a permit.”
- If a card is shown, it must be about this intention. A “What we know” card lists only facts for “Work legally in Spain”. If there are none: “Nothing confirmed yet.” and a text button “Add a fact”.
- Composer pinned above the gesture inset: height 48, radius 24, placeholder “Message”, padding horizontal 16. Send is `ic_chevron_right` inside a 36 dp brand circle, only enabled when there is text. Gap 8 from the field.
- Bottom nav is hidden while the thread is open.

### S-KNOW — first version, current

Left pane of `3_what_we_know_side_by_side.jpg`.

Back, title “What we know”.

Card “Learn Spanish”, body “Practise a language for 15 minutes, three days a week.” Chip “SAID”. “From conversation”.

Card “Quit smoking”, body “Eight cigarettes a day.” Chip “SAID”. “You told us”.

Paragraph: “OBSERVED facts from apps appear here only after you connect them. Nothing is invented.”

Link “Edit or remove”.

The screen mixes two intentions with no way to see which conversation they came from beyond the title.

### S-KNOW — target

Scoped to the open intention. Title “What we know”. No chips.

Rows, grouped list, height grows with two lines:

- Title: the fact, bodyMedium. Example “Eight cigarettes a day”.
- Second line, label: “You told us” or “Observed · Duolingo” or “Inferred · not confirmed”. Inferred rows are visually the same but the second line must say they are not confirmed.
- Trailing “Edit”.

Empty: bodySecondary “Nothing confirmed yet.” Text button “Add a fact”.

Do not put other intentions’ facts on this screen. A parent intention may list a child fact only with the child name on the second line: “From Quit smoking · you told us”.

Remove the OBSERVED paragraph. The second line already says the source.

### S-INTENTION — first version, current

Left pane of `4_intention_detail_side_by_side.jpg`. Title “Work legally in Spain”. Status “Exploring”.

Blocks: Summary (“EU citizen in Spain, wants to work legally.”), Next action (“Tell INTENT which permits you already have.” button “Continue”), What we know (two SAID chips: EU citizen, Living in Spain), Connected apps (“None yet.”).

Menu rows: Edit, Pause, Discuss, Move to trash.

Again a language “What we know” card appears in the upper thread area of that capture and does not belong to this intention. Do not reproduce that mix.

### S-INTENTION — target

Scroll column, padding 20. No bottom nav if pushed from the drawer; show it if this is a root.

- Back.
- Overline: the status word only. “Exploring”.
- Display: the intention title. Max 3 lines.
- Section “Summary”. Body, the person’s outcome in one or two sentences. If unknown: “We’re still working out what this is.” Text button “Edit”.
- Section “Next”. Card as in S-HOME-ACTIVE’s action card, one action. If there is no action: title “No step yet”, body “We haven’t worked out a next step yet.”, button “Work it out”.
- Section “What we know”. At most three rows, then text button “See all” opening S-KNOW. Same row anatomy.
- Section “Apps”. One line. “None connected.” or “Duolingo · observed time only”. Text button “Manage” opens S-APPS filtered to this intention.
- Overflow, not four permanent rows: one `ic_more` in the app bar opens a sheet with Edit, Pause, Discuss, Move to trash. Trash uses ink, not brand, and asks for confirmation. Discuss opens S-CHAT.

### S-APPS — first version, current

Left pane of `5_apps_connections_side_by_side.jpg`.

Title “Apps you hold”. Subtitle “Connect only what an intention needs.”

Rows: Duolingo, Spotify, Strava, Chess.com, Instagram. Each has a one-line description and a trailing “Connect” or “Connected”.

Footer paragraph about usage access and that time in an app is not completion.

### S-APPS — target

Title “Apps”. bodySecondary “Only apps this intention can actually use.”

Grouped list. Each row height 72:

- Title bodyMedium: the app name.
- Second line, caption: the exact data. Examples:
  - Duolingo — “Time open. Not lessons finished.”
  - Spotify — “What’s playing, if a device is active.”
  - Strava — “Activities you grant. Not connected.”
  - Chess.com — “Rating, if the feed is current.”
  - Instagram — “Time open. Not which videos.”
- Trailing text button “Connect” or, if granted, “Connected” in inkSecondary (not a second brand button).

If usage access is off, one row at the top, not a paragraph: “Phone usage is off” / “INTENT can’t see time in apps.” / text button “Allow”.

Empty filter: “Nothing to connect for this intention.”

### S-SPACE — first version, current

Left pane of `6_your_space_side_by_side.jpg`.

Overline “YOUR SPACE”. Display “Your space”. Gear.

Cards: “Add a quick to-do”, “Capture a thought”, “Intentions” (subtitle “7 intentions”), “Recent” with “Learn Spanish” / “Quit smoking”.

### S-SPACE — target

Do not make “Your space” a second home. The empty home and the drawer already cover this. If the entry remains:

- Title “Your space”, title style. No overline. No gear.
- Three rows only, grouped: “Add a quick to-do”, “Capture a thought”, “Intentions” with caption “7” as the trailing text, not a subtitle essay.
- Recent is a section header plus up to three intention rows (title + status). No nested cards.

### S-COMPANION — first version, current

Left pane of `7_companion_menu_side_by_side.jpg`. A floating round button with a diamond, and a small menu: Ask agent, Today, Check in, Check out, Capture thought. The button covers content. That is the defect called out in the first critique.

### S-COMPANION — target

The menu is a sheet, not a stack of labels beside the bubble.

- Bubble: 56 dp circle, fill brand, `logo_diamond` 28 dp in onBrand (or white). Anchored to the right edge, vertically centered in the lower third, 8 dp off the screen edge. It must not cover the primary button or the composer. When idle and against the edge, it may sit half off-screen (28 dp visible) after 3 seconds; a tap brings it back. It never covers the system gesture bar, the keyboard, or a permission dialog.
- Tap opens the sheet in section 5. Rows, in order, each 56 dp, icon 24 ink:
  1. `ic_chat` — Ask agent
  2. `ic_home` — Today
  3. `ic_clock` — Check in
  4. `ic_flag` — Check out
  5. `ic_mic` — Capture a thought
- “Capture a thought” stores the raw text or audio immediately, with the time, before any classification.
- If overlay permission is missing, there is no bubble and no sentence about it on home. The same five actions live under the Agent tab.

### S-SYS copy that must not return

Remove these strings from every screen, including settings:

- HEURISTIC SHAPE SUGGESTION — NO LIVE LLM
- Heuristic shape suggestion — no live LLM
- labeled heuristics
- NOT SUPPORTED · QUIET_MODE
- quiet_mode
- LOCAL HISTORY
- LIVE OPENAI DISCUSS
- LOCAL HEURISTICS
- KEY MISSING
- OBSERVED
- SAID
- EVIDENCE · local-only · no model calls in this build
- STORED ON DEVICE ONLY · DOES NOT CHANGE SCHEMA OR SNAPSHOT DATA
- NO NETWORK CALL UNTIL THIS IS ON
- local.properties
- Animator duration scale
- CASE-03 and any CASE- prefix
- Overlay permission not granted — no floating bubble
- Small errand with a reminder — not an intention

---

## 9. What changed, screen by screen

### Empty home
Removed the duplicate card, the overlay paragraph, Grant overlay permission, Companion menu, the mid-page Capture thought link, the ERRANDS header, the errand subtitle, the second YOUR SPACE label, the more button, and the gear. Shortened the subtitle to “Nothing scheduled yet.” Added the bell, the sparkle on the primary button, and two icon rows.

### Active home
New state. One recommendation card, Begin now / Later today / Discuss, and a count of other actions today. Not an empty-state screen.

### Builder empty and typed
Removed both heuristic sentences, the NEW INTENTION overline, the field label, and the card wrapper. Renamed Cancel builder to Cancel. Added a 60-character counter on the typed state. Continue stays disabled under 3 characters and over 60.

### Shape list
Removed as a step. Agent classifies. Ambiguous input gets one yes/no, not four kinds. The four-card mock is not to be built.

### Questions
New. One question per screen, progress dots, wheel for quantities, rows for choices. Skip a question the sentence already answered.

### Settings
Removed the intro, the appearance essay, the schema line, the motion essay, the quiet-mode banner, the history button styling, the OpenAI essay, the status line, the all-caps key warning, and the bare APP CONNECTIONS heading. Appearance and motion became single-select lists. The key field appears only when the assistant switch is on. Quiet hours and history became one row each.

### Drawer
Removed the INTENTIONS overline and the Columns control. Added search. Status is one word from a fixed set.

### Conversation
Removed the badge, the turn count, the CASE id, the evidence footer, the SAID chip, and any card whose fact belongs to a different intention.

### What we know
Removed SAID chips and the OBSERVED paragraph. Each fact has a source line and Edit. The list is scoped to the open intention.

### Intention
Removed the mixed-intention card. Edit, Pause, Discuss, and Trash moved into the overflow sheet. Next action uses the same card as active home.

### Apps
Removed the footer essay. Each row states the exact data and its limit. Usage access is a row, not a paragraph.

### Your space
Removed the overline and the gear. Four stacked cards became one list plus a short recent section. This screen should not compete with home.

### Companion
The side menu became a sheet. The bubble cannot cover the primary action, the keyboard, or system UI.

---

## 10. Build notes

- Implement from this file and `tokens/tokens.json`, not by tracing the generated mockups.
- SVGs tint with `currentColor`. Do not ship the checkerboard that appears in some earlier generated icon sheets.
- The first-version side-by-side JPEGs in `references/` are directional. Several right-hand panes still show a shape picker or slightly different type. Those panes are superseded by sections 8 and 9.
- Dark mode, when the appearance row “Dark” or “System” resolves dark: paper #161616, surface #22201C, ink #F6F4F0, inkSecondary #C8C2B8, inkTertiary #8A857E, line #3A372F, brand unchanged #5C4DFF, onBrand #FFFFFF, brandSubtle #2A2648. Do not invent a second brand color.
- Dynamic type: layouts scroll. Do not cap the display below the user’s scale. Buttons stay at least 48 dp tall.
- The gear PNG was redrawn as an 8-tooth gear. Use `icons/svg/ic_settings.svg`, not an older sheet.
