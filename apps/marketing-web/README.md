# Marketing Web — Premium TV Player

Static marketing + legal site for the Premium TV Player app. Built with [Astro](https://astro.build) (static output). Pre-launch — all CTAs route to a waitlist mailto.

## Status

| Run | What | State |
|---|---|---|
| MW-1 | Astro scaffold, routes, BaseLayout, real EN copy, SEO, sitemap | ✅ |
| MW-2 | Design-system tokens, GH Pages CI, mobile-first polish, animated hero GIF, SVG icons | ✅ |
| MW-3 | DE default locale, `ComingSoonBanner` removed, full German copy across all 6 pages | ✅ |
| MW-4 | Multi-language architecture: DE default `/`, EN at `/en/…`, locale switcher, `link()` / `asset()` helpers | ✅ |
| MW-5 | Albanian (SQ) at `/sq/…`, `asset()` bug fix (logo no longer prefixed with locale) | ✅ |

**Current:** trilingual (DE · EN · SQ), pre-launch, deploys to `https://beko2210.github.io/Ibo_Player_Pro/`.

---

## Routes

The site has three locale trees. DE is the default (no prefix), EN and SQ are prefixed.

| Logical path | DE | EN | SQ |
|---|---|---|---|
| Home | `/` | `/en/` | `/sq/` |
| Features | `/features` | `/en/features` | `/sq/features` |
| Pricing | `/pricing` | `/en/pricing` | `/sq/pricing` |
| Download | `/download` | `/en/download` | `/sq/download` |
| Privacy | `/legal/privacy` | `/en/legal/privacy` | `/sq/legal/privacy` |
| Imprint | `/legal/imprint` | `/en/legal/imprint` | `/sq/legal/imprint` |

---

## Local development

```bash
cd apps/marketing-web
npm install
npm run dev       # http://localhost:4321
npm run build     # static build → dist/ (18 pages + sitemap)
npm run preview   # preview production build
```

---

## Internationalisation

Adding a new locale takes four steps:

1. `src/utils/i18n.ts` — add entry to `locales`, `localeLabels`, `localeShort`, `ogLocaleTag`
2. `src/utils/link.ts` — add `WAITLIST_COPY.<locale>` (subject + body for the waitlist mailto)
3. `src/components/Header.astro` + `Footer.astro` — add nav labels for the new locale
4. `src/pages/<locale>/` — create the 6 page files (copy from an existing locale tree)

**`link(path, locale)`** — page URLs (adds locale prefix + BASE_URL).  
**`asset(path)`** — locale-independent assets: logo, favicon, OG image, sitemap (adds only BASE_URL, never a locale segment).

---

## Deploying to GitHub Pages

A CI workflow lives at `.github/workflows/deploy-marketing-web.yml`. It runs on pushes to `main` that touch `apps/marketing-web/`, and can be triggered manually from the Actions tab.

The workflow uses `actions/configure-pages@v5` with `enablement: true` — Pages is auto-enabled on first run. If the first run fails with `HttpError: Not Found`:

1. Repo **Settings → Pages → Build and deployment → Source** → choose **"GitHub Actions"**.
2. Re-run the failed workflow.

Environment variables (consumed by `astro.config.mjs`):

| Var | Default | Purpose |
|---|---|---|
| `SITE` | `https://premiumtvplayer.app` | Canonical origin for OG tags + sitemap |
| `BASE_PATH` | `/` | Subpath the site is served under |

### Switching to a custom domain

1. Add `premiumtvplayer.app` CNAME in DNS → `beko2210.github.io`.
2. Change `SITE` to `https://premiumtvplayer.app` and `BASE_PATH` to `/` in the workflow.
3. Repo **Settings → Pages → Custom domain** → enter `premiumtvplayer.app`, enable HTTPS.
4. Re-run — all internal links are base-path-aware via `link()` / `asset()`.

---

## Open TODOs before launch

- **Business registration.** Imprint notes that Gewerbeanmeldung and VAT ID are in progress. Update `src/pages/legal/imprint.astro` (and `/en/` + `/sq/` mirrors) once issued.
- **Counsel review.** Have the DE privacy policy + imprint checked by a German lawyer.
- **OG image.** `public/og-default.png` (1200 × 630) is referenced in `BaseLayout.astro` but not yet committed. Drop it in before sharing links on social.
- **Albanian proofreading.** The SQ copy is best-effort. Have a native speaker review all pages under `src/pages/sq/` before public launch.
- **hreflang tags.** Add `<link rel="alternate" hreflang="…">` in `<head>` for proper cross-locale SEO. Each page should point to its DE, EN and SQ equivalents.
- **Swap waitlist CTAs.** When the app ships, replace `waitlistMailto(locale)` usages with the Google Play URL and update the hero eyebrow copy on home, pricing, and download.
