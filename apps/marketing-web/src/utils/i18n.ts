/**
 * i18n configuration + helpers for the marketing site.
 *
 * Adding a new language is two steps:
 *   1. Add the locale code to `locales` (and to `localeLabels`).
 *   2. Create the localised `.astro` pages under `src/pages/<locale>/`.
 *
 * The default locale lives at the URL root (no prefix). All other locales
 * live under `/<locale>/…`. This keeps German URLs stable for search
 * engines and lets us add new languages as purely additive changes.
 */

export const locales = ["de", "en", "sq"] as const;
export type Locale = (typeof locales)[number];

/** Locale whose pages live at the URL root (no prefix). */
export const defaultLocale: Locale = "de";

/** Human-readable label for the language switcher. */
export const localeLabels: Record<Locale, string> = {
  de: "Deutsch",
  en: "English",
  sq: "Shqip",
};

/** Short label used inside pill-style switchers. */
export const localeShort: Record<Locale, string> = {
  de: "DE",
  en: "EN",
  sq: "SQ",
};

/** BCP 47 / Open Graph locale tag for each language. */
export const ogLocaleTag: Record<Locale, string> = {
  de: "de_DE",
  en: "en_US",
  sq: "sq_AL",
};

/**
 * Strip Astro's BASE_URL prefix from a pathname so we can reason about
 * the logical route.
 */
function stripBase(pathname: string): string {
  const base = import.meta.env.BASE_URL ?? "/";
  const baseClean = base.endsWith("/") ? base.slice(0, -1) : base;
  if (baseClean && pathname.startsWith(baseClean)) {
    return pathname.slice(baseClean.length) || "/";
  }
  return pathname;
}

/**
 * Detect which locale the visitor is currently viewing from the pathname.
 * Returns the default locale if no `/<locale>/` segment is present.
 */
export function getLocaleFromPath(pathname: string): Locale {
  const clean = stripBase(pathname);
  for (const loc of locales) {
    if (loc === defaultLocale) continue;
    if (clean === `/${loc}` || clean.startsWith(`/${loc}/`)) {
      return loc;
    }
  }
  return defaultLocale;
}

/**
 * Produce the logical route (without any locale prefix). Used when we
 * want to switch locales while staying on the same logical page.
 * Examples: "/en/pricing" → "/pricing", "/" → "/", "/de" → "/".
 */
export function pathWithoutLocale(pathname: string): string {
  const clean = stripBase(pathname);
  for (const loc of locales) {
    if (loc === defaultLocale) continue;
    if (clean === `/${loc}`) return "/";
    if (clean.startsWith(`/${loc}/`)) return clean.slice(`/${loc}`.length);
  }
  return clean;
}

/**
 * Return the logical path the visitor is currently viewing, stripped of
 * any locale prefix. Callers pass this path plus a target locale into
 * `link()` to build the final href. `link()` owns the locale prefix.
 */
export function logicalPath(pathname: string): string {
  return pathWithoutLocale(pathname);
}
