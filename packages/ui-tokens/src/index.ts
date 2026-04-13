/**
 * Premium TV Player — design tokens (cross-platform spec).
 *
 * **This file is the source of truth.** The Android TV theme
 * (`apps/android-tv/app/src/main/java/com/premiumtvplayer/app/ui/theme`)
 * mirrors these values verbatim. Future platforms (Apple TV, Tizen,
 * webOS, admin web) consume this package directly.
 *
 * Naming + role conventions follow Material 3 so values can be plugged
 * into MaterialTheme on every target without renames. The actual palette
 * is bespoke "premium dark" (cinematic black + brand blue/cyan).
 */

// ── Colors ──────────────────────────────────────────────────────────────
export const colors = {
  // Surface stack (deepest → highest)
  backgroundBase: '#050608',
  surfaceBase: '#0B0D11',
  surfaceElevated: '#14171C',
  surfaceFloating: '#1D2128',
  surfaceHigh: '#272C35',

  // Foreground
  onSurfaceHigh: '#FFFFFF',
  onSurface: '#E8EAEE',
  onSurfaceMuted: '#9CA3AF',
  onSurfaceDim: '#5C6471',

  // Brand accent (matches the play-button gradient in the logo)
  accentBlue: '#3B82F6',
  accentBlueDeep: '#2563EB',
  accentCyan: '#60A5FA',
  accentViolet: '#8B5CF6',

  // Semantic
  successGreen: '#10B981',
  warningAmber: '#F59E0B',
  dangerRed: '#EF4444',

  // Focus + selection
  focusBorder: '#FFFFFF',
  focusAccent: '#60A5FA',
  unfocusedVeil: 'rgba(0, 0, 0, 0.4)',
} as const;

export type ColorToken = keyof typeof colors;

// ── Typography ──────────────────────────────────────────────────────────
// Sizes are sp on Android, pt on iOS / tvOS, px on web (1px = 1pt at TV
// scale). Line-height is in the same unit. Letter spacing is in tracking
// (px / pt). Weights follow CSS convention (100-900).
export const type = {
  displayHero: { size: 64, lineHeight: 72, weight: 300, tracking: -0.5 },
  displayLarge: { size: 48, lineHeight: 56, weight: 300, tracking: -0.25 },
  headline: { size: 32, lineHeight: 40, weight: 400, tracking: 0 },
  titleLarge: { size: 24, lineHeight: 32, weight: 500, tracking: 0.1 },
  title: { size: 20, lineHeight: 28, weight: 500, tracking: 0.1 },
  body: { size: 18, lineHeight: 26, weight: 400, tracking: 0.15 },
  bodySmall: { size: 16, lineHeight: 22, weight: 400, tracking: 0.2 },
  label: { size: 14, lineHeight: 20, weight: 500, tracking: 0.4 },
  labelSmall: { size: 12, lineHeight: 16, weight: 600, tracking: 1.2 },
} as const;

export type TypeToken = keyof typeof type;

// ── Spacing (4dp grid) ──────────────────────────────────────────────────
export const spacing = {
  xxs: 2,
  xs: 4,
  s: 8,
  sm: 12,
  m: 16,
  l: 24,
  xl: 32,
  xxl: 48,
  huge: 64,
  hero: 96,
  pageGutter: 48,
  rowGutter: 16,
} as const;

export type SpacingToken = keyof typeof spacing;

// ── Shape radii ─────────────────────────────────────────────────────────
export const radii = {
  xs: 4,
  s: 8,
  m: 12,
  l: 20,
  xl: 28,
  poster: 16,
} as const;

export type RadiusToken = keyof typeof radii;

// ── Motion ──────────────────────────────────────────────────────────────
export const easing = {
  /** Material standard — utility transitions. */
  standard: 'cubic-bezier(0.2, 0.0, 0.0, 1.0)',
  /** Apple-style premium curve — focus + hover. */
  premium: 'cubic-bezier(0.32, 0.72, 0.0, 1.0)',
  /** Slow-in/slow-out — hero crossfades, ambient parallax. */
  cinematic: 'cubic-bezier(0.4, 0.0, 0.2, 1.0)',
} as const;

export const durations = {
  micro: 60,
  short: 200,
  medium: 400,
  long: 800,
} as const;

export const focusScale = 1.06;
