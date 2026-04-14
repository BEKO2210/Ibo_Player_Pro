/**
 * Prefix an internal href with Astro's base path.
 *
 * When the site is served at a subpath (e.g. GitHub Pages project site
 * `/Ibo_Player_Pro/`), Astro exposes the base via `import.meta.env.BASE_URL`.
 * On a custom domain or root-path deploy, BASE_URL is `/` and this is a no-op.
 */
export function link(path: string): string {
  const base = import.meta.env.BASE_URL ?? "/";
  const baseClean = base.endsWith("/") ? base.slice(0, -1) : base;
  const pathClean = path.startsWith("/") ? path : `/${path}`;
  const joined = `${baseClean}${pathClean}`;
  return joined === "" ? "/" : joined;
}

/**
 * Compare a path against the current URL pathname, base-path-aware.
 */
export function isActive(currentPath: string, target: string): boolean {
  const resolved = link(target);
  return currentPath === resolved || currentPath.startsWith(resolved + "/");
}

/** Email the operator uses as the waitlist / contact address. */
export const WAITLIST_EMAIL = "belkis.aslani@gmail.com";

/** mailto: link for the "Notify me when it launches" CTA. */
export const WAITLIST_MAILTO =
  `mailto:${WAITLIST_EMAIL}?subject=${encodeURIComponent(
    "Premium TV Player — Waitlist"
  )}&body=${encodeURIComponent(
    "Hi,\n\nI'd like to be notified when Premium TV Player launches on Google Play.\n\nThanks!"
  )}`;
