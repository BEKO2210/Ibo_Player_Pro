import { defineConfig } from "astro/config";

// Run 1: minimal static config. Styling/integrations added in Run 2.
export default defineConfig({
  site: "https://premiumtvplayer.app",
  output: "static",
  trailingSlash: "ignore",
});
