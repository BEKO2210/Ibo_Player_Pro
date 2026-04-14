# Marketing Web — Premium TV Player

Static marketing + legal site for the Premium TV Player app. Built with [Astro](https://astro.build) (static output).

## Status

- Run 1: Grundgerüst — routes, BaseLayout, page stubs. ✅
- **Run 2 (current): Design-System** — tokens mirror `packages/ui-tokens`, dark theme, AccentBlue gradient, Header/Footer/Hero/Section/FeatureCard/PageHeader/Button, responsive, reduced-motion aware. ✅
- Run 3: real copy (EN + DE), SEO meta, Open Graph, sitemap, Play Store link, privacy/imprint text.

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
