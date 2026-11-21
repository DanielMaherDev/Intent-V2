# INTENT design handoff

Read `DESIGN_SPEC.md` first. It is the source of truth and supersedes the image mocks.

- `tokens/tokens.json` — color, type, space, radius
- `screens/screens.json` — one record per screen, with the source files and the target measurements
- `icons/svg/` — one SVG per icon, 24×24, `currentColor`
- `icons/png/24|48|96|256/` — brand `#5C4DFF`, transparent
- `icons/png-ink/24|48|96|256/` — ink `#161616`, transparent
- `icons/catalog.json` — id, paths, default tint, where it is used
- `references/current/` — the 22 live captures
- `references/side_by_side_first/` — the eight first-version comparisons
- `references/side_by_side_live/` — the live-build comparisons

Shape selection is not a user step. See `R-CLASSIFY` in the spec.
