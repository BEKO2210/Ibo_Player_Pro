import { defineConfig } from "astro/config";
import sitemap from "@astrojs/sitemap";

// Deploy configuration
// ────────────────────────────────────────────────────────────────────
// Root-domain deploy (custom domain / user site):
//   SITE=https://premiumtvplayer.app BASE_PATH=/ npm run build
//
// GitHub Pages project site (default for this repo):
//   SITE=https://beko2210.github.io BASE_PATH=/Ibo_Player_Pro/ npm run build
//
// The deploy workflow (.github/workflows/deploy-marketing-web.yml)
// sets both env vars automatically.
const SITE = process.env.SITE ?? "https://premiumtvplayer.app";
const BASE_PATH = process.env.BASE_PATH ?? "/";

export default defineConfig({
  site: SITE,
  base: BASE_PATH,
  output: "static",
  trailingSlash: "ignore",
  integrations: [sitemap()],
});
