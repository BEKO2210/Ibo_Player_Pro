# Marketing Web — Premium TV Player

Static marketing + legal site for the Premium TV Player app. Built with [Astro](https://astro.build) (static output). Currently in pre-launch "Coming Soon" mode — all CTAs route to a waitlist mailto.

## Status

- Run 1: Grundgerüst — routes, BaseLayout, page stubs. ✅
- Run 2: Design-System — tokens mirror `packages/ui-tokens`, components, responsive. ✅
- Run 3: Inhalt — real EN copy, SEO, sitemap, robots, favicon. ✅
- Run 4 (current): **Coming-Soon state + mobile-first polish + GitHub Pages CI.** ✅
- Run 5 (parked): German (`/de/…`) translations.

## Routes

| Path | Purpose |
|---|---|
| `/` | Hero + feature grid + "How it works" + CTA panel |
| `/features` | 4-section feature breakdown |
| `/pricing` | Lifetime Single (€19.99) / Family (€39.99) + FAQ |
| `/download` | Launch status + system requirements + roadmap |
| `/legal/privacy` | Privacy policy |
| `/legal/imprint` | § 5 TMG / § 18 MStV imprint |

## Local development

```bash
cd apps/marketing-web
npm install
npm run dev       # http://localhost:4321
npm run build     # static build → dist/
npm run preview   # preview production build
```

## Deploying to GitHub Pages

A CI workflow lives at `.github/workflows/deploy-marketing-web.yml`. It runs on pushes to `main` that touch `apps/marketing-web/`, and can also be triggered manually from the Actions tab.

### One-time setup (first deploy only)

The workflow uses `actions/configure-pages@v5` with `enablement: true`, so it will **try to auto-enable** Pages on the first run. In most cases no manual step is needed — just push to `main` and watch the Actions tab.

**If the first run still fails with `HttpError: Not Found — Get Pages site failed`**, Pages hasn't been enabled for the repo yet and the token couldn't auto-enable it. Fix:

1. Repo **Settings → Pages**.
2. Under **Build and deployment → Source**, choose **"GitHub Actions"**.
3. Leave the branch/folder fields empty (GitHub Actions mode ignores them).
4. Re-run the failed workflow: Actions → *Deploy marketing-web to GitHub Pages* → the failed run → **Re-run jobs**.

When the workflow finishes, the **deploy** job prints the live URL in its logs — for this repo, that's `https://beko2210.github.io/Ibo_Player_Pro/`.

> **Deprecation warning about Node.js 20 actions.** GitHub recently started flagging actions that still run on Node.js 20 (upstream actions will migrate to Node 24). The warning is cosmetic — the workflow still runs and deploys correctly. It will go away when `actions/checkout`, `setup-node`, `configure-pages`, `upload-pages-artifact` and `deploy-pages` release their Node-24 versions.

### Switching to a custom domain

When the `premiumtvplayer.app` domain is ready:

1. Add the CNAME in your DNS (e.g. `premiumtvplayer.app` → `beko2210.github.io`).
2. In the workflow file, change `SITE` to `https://premiumtvplayer.app` and `BASE_PATH` to `/`.
3. In repo Settings → Pages → Custom domain, enter `premiumtvplayer.app` and enable HTTPS.
4. Re-run the workflow — all internal links are base-path-aware, so they flip automatically.

## Base-path support

All internal links go through `src/utils/link.ts`, which prefixes `import.meta.env.BASE_URL` onto every internal URL. In local dev (`BASE_URL = /`) this is a no-op. In production behind `/Ibo_Player_Pro/`, every link, asset and favicon reference resolves correctly.

Environment variables (consumed by `astro.config.mjs`):

| Var | Default | Purpose |
|---|---|---|
| `SITE` | `https://premiumtvplayer.app` | Absolute origin — used for canonical URLs, OG tags, sitemap |
| `BASE_PATH` | `/` | Subpath the site is served under |

## Coming Soon state

The app is not yet live on Google Play. The site reflects this consistently:

- Top banner (`ComingSoonBanner.astro`) on every page
- Hero eyebrow: *"Launching soon on Google Play"*
- All primary CTAs route to `mailto:belkis.aslani@gmail.com` with a "Premium TV Player — Waitlist" subject (`WAITLIST_MAILTO` in `src/utils/link.ts`)
- Copy is written in present tense for features ("the app *is* neutral") but future tense for availability ("at launch, every account *will* get a 14-day trial")

When the app ships, swap `WAITLIST_MAILTO` usages back to the Google Play URL (`https://play.google.com/store/apps/details?id=com.premiumtvplayer.app`), remove `ComingSoonBanner` from `BaseLayout`, and adjust the eyebrow/CTA copy on home, pricing and download.

## Open TODOs before launch

- **Business registration entry.** The imprint currently notes that a sole-proprietor Gewerbeanmeldung and VAT ID are in progress. Update `src/pages/legal/imprint.astro` with the final entries (business name, Gewerbe registration number, VAT ID per § 27 a UStG) once issued.
- **Counsel review.** Have the privacy policy + imprint checked by a German lawyer before the public launch.
- **OG image.** `public/og-default.png` (1200 × 630) is referenced in `BaseLayout.astro` but not yet committed. Drop it in before sharing links on social.
- **German translation.** Parking lot — all pages are EN-only today.
