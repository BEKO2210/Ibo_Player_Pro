import { defineConfig } from "astro/config";
import sitemap from "@astrojs/sitemap";

export default defineConfig({
  site: "https://premiumtvplayer.app",
  output: "static",
  trailingSlash: "ignore",
  integrations: [sitemap()],
});
