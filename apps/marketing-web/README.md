# Marketing Web — Premium TV Player

Static marketing + legal site for the Premium TV Player app. Built with [Astro](https://astro.build) (static output).

## Status

- **Run 1 (current): Grundgerüst** — routes, BaseLayout, page stubs, build succeeds. No styling, no real copy.
- Run 2: visual design system (mirrors `packages/ui-tokens`: dark theme, AccentBlue, DisplayHero typography)
- Run 3: real copy (EN + DE), SEO meta, Open Graph, sitemap, Play Store link, privacy/imprint text

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
