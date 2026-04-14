# Marketing Web — Premium TV Player

Static marketing + legal site for the Premium TV Player app. Built with [Astro](https://astro.build) (static output).

## Status

- Run 1: Grundgerüst — routes, BaseLayout, page stubs. ✅
- Run 2: Design-System — tokens mirror `packages/ui-tokens`, components, responsive, reduced-motion aware. ✅
- **Run 3 (current): Inhalt** — real EN copy on all 6 pages, SEO meta + Open Graph + Twitter Card + canonical, favicon, sitemap (via `@astrojs/sitemap`), `robots.txt`, Play Store link wired to `com.premiumtvplayer.app`, skip-link a11y affordance. ✅
- Run 3.5 (parked): German (`/de/…`) translations of all pages.

## TODOs before launch

- **Imprint:** `src/pages/legal/imprint.astro` has placeholder fields marked `{Company name to be inserted}` etc. Replace with the final company details and have counsel review the Privacy + Imprint texts before going live.
- **Play Store URL:** `PLAY_STORE_URL` is hard-coded to `https://play.google.com/store/apps/details?id=com.premiumtvplayer.app`. If the app is listed under a different package, update the constant in `index.astro`, `pricing.astro` and `download.astro`.
- **OG image:** `/og-default.png` is referenced in `BaseLayout.astro` but not yet present in `public/`. Drop a 1200×630 PNG there before the site goes public.

## Components (Run 2)

| Component | Purpose |
|---|---|
| `BaseLayout` | Shell: `<html>`, header, main slot, footer, global CSS |
| `Header` | Sticky, blurred backdrop, brand mark + nav + CTA |
| `Footer` | Brand, product + legal columns, copyright |
| `Hero` | Display-hero headline + decorative TV frame placeholder |
| `PageHeader` | Inner-page header (eyebrow + title + lede) |
| `Section` | Uniform vertical padding + optional section heading |
| `FeatureCard` | Gradient-icon + title + body, hover lift |
| `Button` | `primary` / `secondary` / `ghost` variants |

Tokens live in `src/styles/tokens.css` (mirror of `packages/ui-tokens/src/index.ts`).

## Routes (Run 1)

| Path | Purpose |
|---|---|
| `/` | Landing / hero |
| `/features` | Feature overview |
| `/pricing` | Lifetime Single / Family |
| `/download` | Play Store + system requirements |
| `/legal/privacy` | Privacy policy |
| `/legal/imprint` | Imprint / company details |

## Commands

```bash
cd apps/marketing-web
npm install
npm run dev       # local dev server
npm run build     # static build → dist/
npm run preview   # preview production build
```
