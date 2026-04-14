import { defaultLocale, type Locale } from "./i18n";

/**
 * Prefix an internal href with Astro's base path and, if needed, a
 * locale prefix.
 *
 *   link("/features")        // → "/features"                  (default locale)
 *   link("/features", "en")  // → "/en/features"               (English)
 *   link("/features", "de")  // → "/features"                  (German = default)
 *
 * On a GitHub Pages project-site deploy the BASE_URL adds another
 * prefix on top (e.g. "/Ibo_Player_Pro/en/features").
 */
export function link(path: string, locale: Locale = defaultLocale): string {
  const base = import.meta.env.BASE_URL ?? "/";
  const baseClean = base.endsWith("/") ? base.slice(0, -1) : base;
  const pathClean = path.startsWith("/") ? path : `/${path}`;

  const localeSegment = locale === defaultLocale ? "" : `/${locale}`;
  const joined = `${baseClean}${localeSegment}${pathClean}`;
  return joined === "" ? "/" : joined;
}

/**
 * Compare a path against the current URL pathname.
 * Works for both locale-prefixed and un-prefixed paths — the caller
 * passes the CURRENT locale so the right prefix is matched.
 */
export function isActive(
  currentPath: string,
  target: string,
  locale: Locale = defaultLocale,
): boolean {
  const resolved = link(target, locale);
  return currentPath === resolved || currentPath.startsWith(resolved + "/");
}

/** Operator contact email used across the site. */
export const WAITLIST_EMAIL = "belkis.aslani@gmail.com";

/**
 * Per-locale subject + body for the waitlist mailto link. Anything the
 * operator ends up writing in their inbox client respects the locale
 * the visitor is browsing in.
 */
const WAITLIST_COPY: Record<Locale, { subject: string; body: string }> = {
  de: {
    subject: "Premium TV Player — Warteliste",
    body:
      "Hallo,\n\n" +
      "bitte benachrichtige mich, sobald Premium TV Player im Google Play Store verfügbar ist.\n\n" +
      "Danke!",
  },
  en: {
    subject: "Premium TV Player — Waitlist",
    body:
      "Hi,\n\n" +
      "I'd like to be notified when Premium TV Player launches on Google Play.\n\n" +
      "Thanks!",
  },
};

/** Build the mailto: URL for the waitlist CTA in a given locale. */
export function waitlistMailto(locale: Locale = defaultLocale): string {
  const { subject, body } = WAITLIST_COPY[locale];
  return (
    `mailto:${WAITLIST_EMAIL}` +
    `?subject=${encodeURIComponent(subject)}` +
    `&body=${encodeURIComponent(body)}`
  );
}
