# @premium-player/ui-tokens

Cross-platform design tokens for Premium TV Player. **`src/index.ts` is the
source of truth.** Other targets (Android TV, future Apple TV / Tizen /
webOS / admin web) MUST mirror these values exactly.

## What's in here

- **Colors** — surface stack (cinematic dark), foreground hierarchy, brand
  accent (blue → cyan), semantic states, focus/selection.
- **Typography** — 10-foot UI hierarchy: display, headline, title, body,
  label.
- **Spacing** — 4dp grid: `xxs` (2) → `hero` (96) plus `pageGutter` (48)
  and `rowGutter` (16).
- **Radii** — soft corners; `poster` (16) is the canonical hero/poster
  art radius.
- **Motion** — three named easings (`standard`, `premium`, `cinematic`)
  + four duration buckets (60/200/400/800ms) + `focusScale` (1.06).

## Mirror map

| Target | Mirror file |
|---|---|
| Android TV | `apps/android-tv/app/src/main/java/com/premiumtvplayer/app/ui/theme/{Color,Type,Spacing,Motion,Theme}.kt` |
| Apple TV (later) | `apps/apple-tv/PremiumTV/Theme/Tokens.swift` |
| Tizen (later) | `apps/samsung-tv/src/theme/tokens.ts` |
| webOS (later) | `apps/lg-tv/src/theme/tokens.ts` |
| Admin web (later) | `apps/admin-web/src/theme/tokens.ts` |

## Updating tokens

1. Change `src/index.ts` first.
2. Mirror the change in every active platform target.
3. PR title prefix: `tokens: <what changed>`.

Tests are deliberately minimal — these are constants. The contract is
"every target equals this file"; that contract is enforced at code-review
time.
