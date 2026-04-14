# Premium TV Player — User Flows (V1, Android TV)

**Document owner:** project lead
**Last updated:** 2026-04-12 (Run 2)

All flows below are the authoritative V1 reference. UI, API, and backend runs MUST follow them. If a flow needs to change, update this file and note the change in `CLAUDE.md` → Parking Lot or Run Log.

Conventions
- `App` = Android TV client
- `API` = own NestJS backend
- `FBA` = Firebase Authentication
- `Play` = Google Play Billing
- `EPG-W` = EPG worker service

---

## 1. Onboarding (first launch, no account)

```mermaid
flowchart TD
  A[App launches] --> B{Saved session?}
  B -- no --> C[Welcome screen<br/>logo + tagline + 'Get started']
  C --> D[Explainer carousel<br/>3 slides: player, profiles, trial]
  D --> E{Choose: Sign up / Log in}
  E -- Sign up --> F[Signup flow §2]
  E -- Log in --> G[Login flow §3]
  B -- yes --> H[Profile picker §7]
```

Acceptance
- No account → user reaches Welcome in < 2 s after splash.
- Explainer is skippable.
- No source/content visible before login.

---

## 2. Signup

```mermaid
sequenceDiagram
  participant U as User
  participant App
  participant FBA as Firebase Auth
  participant API

  U->>App: Enter email + password
  App->>FBA: createUserWithEmailAndPassword
  FBA-->>App: ID token
  App->>API: POST /auth/register (ID token, locale, device info)
  API->>API: Upsert account row, issue device token, start trial
  API-->>App: { account, deviceToken, entitlement: {state: trial, ends_at} }
  App->>App: Persist device token securely (EncryptedSharedPreferences)
  App->>U: "Your 14-day trial has started"
  App->>U: Route to Profile creation §6
```

Rules
- Email uniqueness is enforced by Firebase.
- **Trial starts here, server-side.** Client does not compute trial end.
- Device token is an opaque, server-issued string scoped to this install.

Errors
- Invalid email → inline error, don't navigate.
- Weak password → inline error.
- Network → toast + retry button; no partial account created.

---

## 3. Login

```mermaid
sequenceDiagram
  participant U as User
  participant App
  participant FBA
  participant API

  U->>App: Email + password
  App->>FBA: signInWithEmailAndPassword
  FBA-->>App: ID token
  App->>API: POST /auth/login (ID token, device info)
  API->>API: Upsert device, check slot availability
  alt slot available
    API-->>App: { deviceToken, entitlement }
    App->>App: Go to Profile picker §7
  else no slot
    API-->>App: 409 slot_full + existing devices list
    App->>U: "Revoke a device to continue" screen §12
  end
```

Rules
- If entitlement is `expired` or `none`, login still succeeds; user lands in a restricted state with a clear Upgrade CTA.
- Device slots are checked server-side on every login.

---

## 4. Trial activation (automatic)

```mermaid
flowchart LR
  S[Signup success §2] --> T[API starts trial row<br/>state=trial<br/>ends_at=now+14d]
  T --> U[Client reads entitlement<br/>shows 'Trial' badge in Settings]
```

Rules
- One trial per account, ever. Re-registration after deletion does not re-issue a trial to the same email.
- Trial does not auto-convert. When `ends_at` passes, state flips to `expired` and app surfaces an Upgrade screen at next entitlement check.

---

## 5. Purchase (Single or Family Lifetime)

```mermaid
sequenceDiagram
  participant U as User
  participant App
  participant Play as Play Billing
  participant API
  participant BW as Billing Worker

  U->>App: Tap 'Upgrade' (Single or Family)
  App->>Play: launchBillingFlow(productId)
  Play-->>App: Purchase object (purchaseToken, orderId)
  App->>API: POST /billing/verify (purchaseToken, productId, deviceToken)
  API->>BW: enqueue verify job
  BW->>Play: Play Developer API verify
  Play-->>BW: verified + details
  BW->>API: mark entitlement=lifetime_single|lifetime_family
  API-->>App: 200 + new entitlement
  App->>Play: acknowledgePurchase
  App->>U: "You're all set" + confetti-free celebratory screen
```

Rules
- Server is authoritative: app does not flip entitlement on its own.
- Acknowledge only **after** server confirmation. Unacknowledged purchases auto-refund per Play policy.
- On Family purchase, device slot cap raises from 1 → 5.

Errors
- Verification fails → show "We couldn't confirm your purchase. Try Restore."
- Network loss mid-flow → Restore flow §6 recovers.

---

## 6. Restore Purchase

```mermaid
sequenceDiagram
  participant U
  participant App
  participant Play
  participant API

  U->>App: Settings → Restore Purchase
  App->>Play: queryPurchasesAsync(ONE_TIME)
  Play-->>App: [purchases]
  loop for each
    App->>API: POST /billing/verify
    API-->>App: entitlement result
  end
  App->>U: Shows current entitlement
```

Rules
- Always available, never hidden.
- Also runs silently on Login if entitlement is `none` but a signed-in Play account owns the product.

---

## 7. Profile picker (after login)

```mermaid
flowchart TD
  L[Login success] --> P{Profiles exist?}
  P -- no --> C[Create first profile §8]
  P -- yes --> G[Profile grid<br/>up to 5 tiles + 'Manage' + 'Add']
  G --> S{Tile selected}
  S -- adult --> H[Home §10]
  S -- kids --> K[PIN prompt §11] --> H
  S -- Manage --> M[Profile management §8]
```

---

## 8. Profile creation / management

```mermaid
sequenceDiagram
  participant U
  participant App
  participant API

  U->>App: 'Add profile'
  App->>U: name + avatar + is_kids + locale
  alt is_kids = true
    App->>U: Set PIN (4 digits, confirm)
  end
  App->>API: POST /profiles
  API->>API: Enforce 5-profile cap<br/>Hash PIN (bcrypt/argon2)
  API-->>App: profile row
  App->>U: Back to profile grid
```

Rules
- Max 5 profiles per account (Family); 1 profile (Single).
- PIN is stored as hash only, salted per profile.
- PIN reset requires account password re-auth.

---

## 9. Add source (M3U / XMLTV)

```mermaid
sequenceDiagram
  participant U
  participant App
  participant API
  participant EPGW as EPG Worker

  U->>App: Settings → Sources → Add
  App->>U: URL + friendly name + optional user/pass + type (M3U / XMLTV / M3U+EPG)
  App->>API: POST /sources (validate)
  API->>API: HEAD/GET probe, MIME check, size guard
  API-->>App: 200 { source_id, item_count_estimate }
  App->>U: Import progress
  alt type includes EPG
    API->>EPGW: enqueue EPG ingest
    EPGW->>EPGW: parse XMLTV, cache in DB
  end
```

Rules
- URL is stored encrypted at rest; optional credentials are stored encrypted.
- Source body is never proxied or stored in full; only metadata + EPG cache.
- Max N sources per profile (default 10, configurable).

Errors
- Unreachable URL → inline error with retry.
- Unsupported format → explain supported types.

---

## 10. Home screen

```mermaid
flowchart TD
  H[Home] --> R1[Hero carousel<br/>top item = Continue Watching or Featured]
  H --> R2[Continue Watching row]
  H --> R3[Favorites row]
  H --> R4[Live Now row]
  H --> R5[Recently Added row]
  H --> R6[Search entrypoint]
  H --> R7[Profile avatar → Settings]
```

Rules
- All rows are populated from the active profile's data + user's own sources.
- Empty state copy when a row has nothing yet.
- First focus lands on the hero on cold launch.

---

## 11. Kids PIN gate

```mermaid
sequenceDiagram
  participant U
  participant App
  participant API
  U->>App: Select kids profile OR enter Device settings
  App->>U: PIN pad
  U->>App: 4-digit PIN
  App->>API: POST /profiles/{id}/verify-pin
  API-->>App: ok | rate_limited | wrong
  alt ok
    App->>U: Proceed
  else wrong
    App->>U: Shake + retry (after 5 wrongs → 60s cool-down)
  end
```

Rules
- PIN verification is server-side (prevents local bypass).
- Rate limit: 5 attempts per 60 s per profile per device.

---

## 12. Device management

```mermaid
flowchart LR
  S[Settings → Devices] --> L[Current device info]
  S --> O[Other devices list]
  O --> A{Action}
  A -- Rename --> R[PUT /devices/{id}]
  A -- Revoke --> V[DELETE /devices/{id}]
  V --> N[Revoked device forced to logout on next API call]
```

Rules
- User can always revoke other devices; cannot revoke current device (must log out instead).
- Revocation is instant server-side; affected device shows "Signed out" screen on next request.

---

## 13. Playback (Live, VOD, Resume)

```mermaid
sequenceDiagram
  participant U
  participant App
  participant API

  U->>App: Tap item
  App->>API: POST /playback/start (item_id, profile_id, source_id)
  API-->>App: { stream_url, headers, resume_position }
  App->>App: Media3/ExoPlayer prepare + play
  loop every 15s
    App->>API: POST /playback/heartbeat (position, state)
  end
  U->>App: Stop / back
  App->>API: POST /playback/stop (final_position)
  API->>API: Upsert watch_history + continue_watching
```

Rules
- Heartbeat interval: 15 s.
- On network loss: buffer heartbeats locally, flush on reconnect.
- Resume prompt ("Resume from 34:12?" / "Start over") if last position is between 30 s and end-minus-60 s.

---

## 14. Logout

```mermaid
sequenceDiagram
  participant U
  participant App
  participant FBA
  participant API

  U->>App: Settings → Logout
  App->>API: POST /auth/logout (deviceToken)
  API->>API: Revoke device token, free slot
  App->>FBA: signOut
  App->>App: Clear local session + EncryptedSharedPreferences
  App->>U: Welcome screen §1
```

---

## 15. Expired / Revoked state

```mermaid
flowchart TD
  C[Any API call] --> R{Response}
  R -- 401 revoked --> X[Force logout + signed-out screen]
  R -- 402 expired --> U[Upgrade screen: Single / Family / Restore]
  R -- 403 slot_full --> D[Device management screen §12]
```

Rules
- These states are handled by a single global API interceptor in the app.
- User can still browse Settings → Account → Logout.
- No playback allowed in `expired` / `revoked` states.

---

## 16. Error & diagnostics surfaces

- Every flow must render a consistent error card: title, short message, primary action ("Try again"), secondary ("Report").
- "Report" generates a redacted diagnostics bundle (no source URLs, no PII beyond account id).
- Diagnostics screen accessible from Settings for advanced users.

---

## 17. First-run happy path (summary)

1. Welcome → Sign up
2. Account created + 14-day trial started server-side
3. First profile created (adult)
4. Add source (user provides M3U or XMLTV URL)
5. Source validates, EPG ingests if applicable
6. Home populates — user watches something
7. Day 14 approaches → in-app reminder → Upgrade → Play Billing → Lifetime
