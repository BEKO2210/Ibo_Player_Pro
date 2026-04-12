# Premium TV Player — Product Requirements Document (PRD)

**Document owner:** project lead
**Last updated:** 2026-04-12 (Run 2)
**Status:** V1 scope locked. Changes require explicit update to `CLAUDE.md` *Locked Product Decisions*.

---

## 1. Vision

Premium TV Player is a **neutral, premium-quality media player** for Android TV (and later Mobile/Web/tvOS/Tizen/webOS) that lets users bring their own authorized media sources (M3U / M3U8 / XMLTV) and enjoy them with a Netflix-/Apple-grade experience: elegant UI, 5 household profiles, kids safety, EPG, cross-device sync, and a fair lifetime pricing model.

The product ships **empty by default** — no content, no catalog, no pre-configured sources. The user authorizes and adds their own sources. The company operates only identity, entitlement, sync, and EPG-cache infrastructure.

---

## 2. Why this exists

Existing third-party TV players on Android either look dated, lock users into a single hardware vendor, rely on fragile MAC-based device locking, or bundle unlicensed content. The market opportunity is a **premium, content-neutral, multi-platform player** with a modern stack, account-based entitlements, and a Netflix-like household experience.

---

## 3. Target users (Personas)

### P1 — The enthusiast (primary)
- 30–55, tech-literate, already manages own M3U/XMLTV sources
- Values UI polish, responsiveness, and reliability
- Owns 1–3 TVs + a phone; wants sync across them

### P2 — The family (primary)
- Household of 2–5 people sharing one account
- Needs separate profiles and a safe kids profile with PIN
- Cares about parental controls and clean recommendations per profile

### P3 — The casual viewer (secondary, future)
- Single TV, single profile, values ease of onboarding
- Buys once, expects it to "just work"

### Non-user
- Anyone expecting the app to *provide* content — app is a player, not a catalog.

---

## 4. V1 scope (Android TV only)

### 4.1 In scope for V1 launch

**Account & entitlements**
- Email/password signup + login (Firebase Auth)
- 14-day **server-side** trial, auto-activated on first login
- One-time purchases via Google Play Billing:
  - **Lifetime Single** (€19.99–24.99)
  - **Lifetime Family** (€39.99–49.99)
- Restore Purchase
- Server-managed device slots: 5 for Family, 1 for Single
- Device list in Settings: view + rename + revoke

**Profiles**
- Up to 5 profiles per account (Family) / 1 profile (Single)
- Avatar, display name, locale
- **Kids profile** flag with PIN gate and age-based filter
- Profile switcher from Settings and from Home

**Sources (user-provided)**
- Add source: URL (M3U/M3U8/XMLTV), optional credentials, friendly name
- Validate source, show import progress and errors
- List, edit, remove sources
- App ships **empty** — no default sources

**EPG**
- Ingest XMLTV from user-provided sources (server-cached via `epg-worker`)
- Browse EPG: channel grid, now/next, day navigation
- Link EPG entries to Live channels when IDs match

**Home & discovery**
- Hero carousel (featured items from user's own sources/favorites)
- Rows: Continue Watching, Favorites, Recently Added (from sources), Live Now
- Search across the user's own sources

**Playback**
- Live TV, VOD (single items), Series (episodes) via Media3/ExoPlayer
- Resume (server-synced via heartbeat)
- Subtitle track selection
- Audio track selection
- Basic error surfaces (stream down, auth failure, format unsupported)

**Parental controls**
- PIN-protected kids profile
- Age rating filter for kids profile
- PIN also gates device-management screens

**Settings**
- Account (email, logout, delete account request)
- Subscription/entitlement status
- Devices (this device, other devices, revoke)
- Profiles (CRUD, PIN change)
- Sources (CRUD)
- Playback defaults (preferred audio/sub language, buffering hints)
- Diagnostics (last errors, app/build info, log export)
- Language (English default; i18n-ready for later languages)

**Cloud sync**
- Profiles, favorites, continue-watching, watch history, source list
- Via own API (source of truth = server)

**Design**
- Dark premium theme, large hero surfaces, clean Apple/Netflix-influenced typography and motion
- Fully keyboard/remote-focusable (D-pad), 10-foot UX

### 4.2 Explicitly OUT of scope for V1

- Android Mobile, Web, tvOS/iOS, Samsung Tizen, LG webOS (later phases)
- Recording (schedule-only UI may appear, execution is V1.5)
- Timeshift / buffered rewind on Live (V2+)
- Chromecast / AirPlay sending
- Download-for-offline
- Social features, reviews, comments
- Content catalog / recommendations from the company (only per-user lists)
- Multiple subscription tiers beyond Single/Family Lifetime
- Reseller/partner panel
- Admin web portal (moved to V2)

---

## 5. Monetization

| Plan | Price (target) | Devices | Profiles | Notes |
|---|---|---|---|---|
| Trial | €0 | 1 active device | 1 profile | 14 days, server-side, non-renewable per account |
| Lifetime Single | €19.99–24.99 | 1 active device slot | 1 profile | One-time Play Billing product |
| Lifetime Family | €39.99–49.99 | 5 active device slots | up to 5 profiles | One-time Play Billing product |

Rules
- Trial starts automatically on first successful login.
- Purchase is account-level; any device signed into the account inherits the entitlement, subject to device slot limits.
- Refunds/revocations flip entitlement back to `expired` (if trial already consumed) or `none`.
- No automatic renewals. No subscriptions in V1.

---

## 6. Key product principles

1. **Server is authoritative** for trial, purchase, entitlement, device slots. Client never self-grants access.
2. **Account-based, not hardware-based.** No MAC binding. Device identity is a server-issued token per install.
3. **Content-neutral.** The app does not ship, bundle, or promote any third-party content. Users bring and authorize their own.
4. **Premium feel everywhere.** Every screen is focus-correct, fast, and visually calm. No ads. No upsell pop-ups mid-session.
5. **Privacy-respecting.** Collect only what runs the service: email, entitlement state, device list, per-profile sync data.
6. **i18n from day one.** All user-visible strings are keys; English is default; adding a locale is drop-in JSON + strings.xml.

---

## 7. Success metrics (V1)

Since this is a private test build first, metrics are observational:

- **Trial → Purchase conversion:** ≥ 10% of trials that reach day 7 convert to Lifetime (internal target; measured after public availability)
- **Stream start time:** < 2.5 s median on Android TV from tile click to first frame
- **Crash-free sessions:** ≥ 99.5% on Android TV
- **Cloud sync latency:** profile/favorite change reflected on a second device in < 10 s
- **Source add success rate:** ≥ 95% for valid M3U/XMLTV URLs

Instrumentation plan: Crashlytics (optional) + minimal own analytics event log via the API. Personal data kept to a minimum.

---

## 8. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Users expect pre-loaded content | Clear onboarding copy: "Bring your own authorized sources" |
| MAC-based device locking is unreliable on modern Android | Server-managed device slots, not MAC |
| Play Store review flags due to IPTV keywords | Position strictly as a neutral player; no built-in content; explicit user-authorization flow |
| Trial gamed by reinstalling | Trial tied to account (not install); one trial per email |
| Source URL leaks via logs | Redact URLs in diagnostics export; never send source body to our backend |
| Family plan abuse across households | 5 active device slots, not "unlimited"; revoke flow in Settings |

---

## 9. Open questions (to resolve in later runs)

- Exact Lifetime pricing (Run 9 — align with Play Billing product creation)
- Crashlytics opt-in default (Run 19)
- Log-export format for support (Run 19)
- Localization target set for V1.1 (post-launch)

---

## 10. Glossary

- **Account** — the top-level user entity, identified by email. Owns entitlements and profiles.
- **Profile** — a persona inside an account. Has its own watch history, favorites, PIN settings.
- **Device slot** — a server-managed permission for one installed app instance to use the account.
- **Entitlement** — the current access level of an account: `none` / `trial` / `lifetime_single` / `lifetime_family` / `expired` / `revoked`.
- **Source** — a user-provided URL to M3U/M3U8 (streams) or XMLTV (EPG).
- **Heartbeat** — periodic client → server ping during playback to sync resume state.
