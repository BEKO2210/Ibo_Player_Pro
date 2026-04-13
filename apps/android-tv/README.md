# Premium TV Player — Android TV (V1 client)

Premium-tier TV player for Android TV / Google TV. Bootstrap landed in
Run 11; design system polish is Run 12; feature screens land in Runs
13-18.

## Stack

- **Kotlin 2.0** + **Gradle 8.11** (Kotlin DSL, version catalog)
- **Android Gradle Plugin 8.7**
- **Jetpack Compose** + **Compose for TV** (`androidx.tv:tv-foundation` /
  `tv-material`)
- **Hilt** (DI) + **Navigation-Compose**
- **Media3 / ExoPlayer** (HLS-aware) — wired now, used in Run 16
- **Retrofit + kotlinx.serialization** — wired now, used in Run 13
- **Firebase Auth** (BoM-managed) — wired now, used in Run 13
- **Room** + **DataStore** — wired now, used in Runs 14-15
- `min SDK 26` (Adaptive Icons baseline; >99% of Android TV install base)
- `target SDK 34`

## applicationId — locked in Run 11

```
com.premiumtvplayer.app
```

This MUST match `BILLING_ANDROID_PACKAGE_NAME` in
`services/api/.env.example`. If you change it, change both.

## Project layout

```
apps/android-tv/
  build.gradle.kts                # root project
  settings.gradle.kts             # only one module today: :app
  gradle.properties
  gradle/
    libs.versions.toml            # version catalog (single source of truth)
    wrapper/gradle-wrapper.properties
  app/
    build.gradle.kts              # application module
    proguard-rules.pro
    src/main/
      AndroidManifest.xml         # Leanback feature + LEANBACK_LAUNCHER
      java/com/premiumtvplayer/app/
        PremiumTvApplication.kt   # @HiltAndroidApp
        MainActivity.kt           # @AndroidEntryPoint, single Activity
        ui/
          PremiumTvApp.kt         # root composable (Run 11 splash placeholder)
          theme/
            Color.kt              # premium dark palette
            Type.kt               # 10-foot typography hierarchy
            Spacing.kt            # 4dp grid (page gutter, row gutter)
            Motion.kt             # easings, durations, focus scale
            Theme.kt              # PremiumTvTheme + CompositionLocals
      res/
        drawable/banner.xml       # Android TV banner (320x180 vector)
        drawable/ic_launcher_foreground.xml
        mipmap-anydpi-v26/ic_launcher{,_round}.xml
        values/{strings,colors,themes,ic_launcher_background}.xml
```

## Design system

The palette / typography / spacing / motion live behind:

- `PremiumColors` (object) — pull color literals from here.
- `PremiumType` (object) — pull text styles from here.
- `LocalPremiumSpacing.current` (CompositionLocal) — pull dp values.
- `LocalPremiumShapes.current` — corner radii.
- `LocalPremiumDurations.current` + `PremiumEasing` + `PremiumTransitions`
  — motion specs.

Cross-platform source of truth is in `packages/ui-tokens/src/index.ts`;
the Kotlin files mirror it verbatim. Always update the TS file first.

See `packages/ui-tokens/README.md` for the token catalog and rationale.

## Component catalog (Run 12)

All premium primitives live under
`app/src/main/java/com/premiumtvplayer/app/ui/components/`.

### Atoms

| Component           | File                  | Purpose                                                                         |
|---------------------|-----------------------|---------------------------------------------------------------------------------|
| `BrandLogo`         | `BrandLogo.kt`        | Renders `res/drawable/brand_logo.png` (mirror of the canonical PNG). 3 sizes. |
| `BootProgress`      | `BootProgress.kt`     | Three-bar accent-cyan pulse for boot / loading. 1.2s linear loop.             |
| `PremiumChip`       | `PremiumChip.kt`      | All-caps `LabelSmall` chip; `Filled` or `Outline` variants.                   |
| `PremiumButton`     | `PremiumButton.kt`    | `Primary` / `Secondary` / `Ghost` variants. Focus 1.04× scale + accent border. |
| `PremiumTextField`  | `PremiumTextField.kt` | TV-friendly input. Focus border switches to `FocusAccent`; error state in `DangerRed`. |
| `PremiumCard`       | `PremiumCard.kt`      | Focusable card primitive. 1.06× scale + glow ring + dim-veil hook on focus.    |

### Molecules

| Component       | File              | Purpose                                                                                          |
|-----------------|-------------------|--------------------------------------------------------------------------------------------------|
| `RowOfTiles<T>` | `RowOfTiles.kt`   | Compose-TV `TvLazyRow` of `PremiumCard`s with the **focus-veil pattern** (40% dim on siblings). |
| `HeroSection`   | `HeroSection.kt`  | Full-bleed hero with backdrop slot, bottom-up scrim, title/subtitle stack, CTA row.            |

### Focus-veil pattern (premium TV signature)

`RowOfTiles` tracks the focused index in row state and passes
`unfocusedDim = 0.4f` to every `PremiumCard` whose index ≠ focused
index. Each card animates its veil alpha through `PremiumTransitions`,
so as the user navigates left/right the dim swaps fluidly. This is the
visual cue that lifts a row above a generic carousel and matches the
behaviour seen on Sony Bravia, Apple TV, and Samsung Tizen Premium.

### Token usage rules

- Never hard-code `Color(...)`, `dp`, or `TextStyle(...)` in component
  bodies. Pull from `PremiumColors`, `LocalPremiumSpacing.current`,
  `LocalPremiumShapes.current`, and `PremiumType`.
- Animations always go through `PremiumTransitions` /
  `PremiumEasing` / `LocalPremiumDurations.current`. Standalone
  `tween(...)` calls are an anti-pattern — they bypass the design
  language.

### `@Preview`

Every component has at least one `@Preview` exercising it under
`PremiumTvTheme {}` with `backgroundColor = 0xFF050608` (the brand
`BackgroundBase`). Open the file in Android Studio → "Split" / "Design"
view to see the rendering live.

## Onboarding flow (Run 13)

The app now owns a full onboarding graph on top of the design system:

```
Boot ──► Welcome ──► Signup / Login ──► TrialActivation ──► ProfilePicker ──► Home
```

All screen transitions go through `PremiumEasing.Premium` fades over
`LocalPremiumDurations.current.short` — editorial, not jittery.

### Runtime config — `BuildConfig` + `local.properties`

Network + Firebase wiring is fed by `BuildConfig` fields set at build
time. Defaults are dev placeholders so the project imports without
secrets.

Override in `apps/android-tv/local.properties` (this file is
`.gitignore`d) **or** on the Gradle command line:

```properties
apiBaseUrl=http://10.0.2.2:3000/v1/
firebaseApiKey=AIza…
firebaseProjectId=premium-player-prod
firebaseApplicationId=1:000000000000:android:0000000000000000000000
```

Firebase is initialized **programmatically** from those fields (see
`di/FirebaseModule.kt`) — no `google-services.json` plugin or file
required. Drop your real Firebase project values into `local.properties`
and rebuild.

Base URL defaults:

| Target                       | Value                         |
|------------------------------|-------------------------------|
| Emulator pointing at local API | `http://10.0.2.2:3000/v1/`  |
| Staging / Production         | Override via `-PapiBaseUrl=…` |

### Layer map

```
data/
  api/
    ApiModels.kt              # kotlinx.serialization DTOs (AccountSnapshot,
                              #   EntitlementStatus, ProfileList,
                              #   ApiErrorEnvelope, FirebaseTokenRequest)
    PremiumPlayerApi.kt       # Retrofit interface
    ApiError.kt               # ApiException sealed hierarchy + ApiErrorMapper
  auth/
    FirebaseTokenSource.kt    # suspend wrapper around FirebaseUser#getIdToken
    AuthRepository.kt         # register / login / refresh — Firebase + /auth
  entitlement/
    EntitlementRepository.kt  # /entitlement/status + /entitlement/trial/start
  profiles/
    ProfileRepository.kt      # /profiles (read-only in V1 onboarding)
di/
  NetworkModule.kt            # Retrofit + OkHttp + Json + AuthInterceptor
  FirebaseModule.kt           # programmatic FirebaseApp + FirebaseAuth
ui/
  nav/Routes.kt               # route constants
  onboarding/
    WelcomeScreen.kt          # logo + copy + Sign In / Create Account
    AuthFormScaffold.kt       # shared email/password layout (Signup + Login)
    SignupScreen.kt    + ViewModel
    LoginScreen.kt     + ViewModel
    TrialActivationScreen.kt  + ViewModel  (handles TRIAL_ALREADY_CONSUMED)
    ProfilePickerScreen.kt    + ViewModel  (loads /profiles, focus-veil row)
  PremiumTvApp.kt             # owns NavHost + Boot + transitions + Home stub
```

### Error envelope → user copy

`ApiErrorCopy.forCode(code, fallback)` maps stable `ErrorCode` values
from the V1 backend (`services/api/src/common/errors.ts`) into English
strings. i18n hooks in Run 19 replace this with a resource lookup.

### Running onboarding locally end-to-end

1. Bring up the V1 backend + deps:
   ```bash
   docker compose -f infra/docker/docker-compose.yml up -d
   cd services/api && npm install && cp .env.example .env && \
     npm run prisma:migrate:deploy && npm run start:dev
   ```
2. In Android Studio, set `local.properties` with a real Firebase
   project's API key / project id / application id.
3. Install + launch on a Google TV emulator. The app boots into
   Welcome, you can Sign Up, the backend logs show
   `POST /v1/auth/register` followed by `POST /v1/entitlement/trial/start`,
   and the Profile Picker loads the account's profiles.
4. Tearing down trial on the same account twice surfaces the friendly
   "already used" state (driven by `TRIAL_ALREADY_CONSUMED`).

## Home screen (Run 14)

After profile pick, the app lands on `HomeScreen` (`ui/home/HomeScreen.kt`).
Two presentation modes driven by the sources count:

### Populated (≥ 1 source)

```
┌──────────────────────────────────────────────────┐
│ [inline logo]                        [Alex · Adult] │  HomeHeader
├──────────────────────────────────────────────────┤
│ HERO CAROUSEL — 3 cards, 21:9, focus-veil between │
│   (chips · Headline · subtitle · deep-link CTA)   │
├──────────────────────────────────────────────────┤
│ Continue Watching  ▸  card card card card …      │  RowOfTiles
│ Favorites          ▸  card card card card …      │
│ Suggested for you  ▸  card card card card …      │
│ Your Sources       ▸  card card card …           │
└──────────────────────────────────────────────────┘
```

- Hero carousel **auto-focuses the first tile on first composition**
  (Apple TV / Bravia convention). Users land "inside" the page — no
  "press D-pad to start".
- Every row runs the **focus-veil pattern** from Run 12: as focus slides
  left/right, sibling tiles dim to 40% through `PremiumEasing.Standard`.
- Hero cards render `PremiumCard` with a 21:9 aspect ratio and a brand-
  accent linear gradient backdrop, paired with a `HeroCaption` column
  (outline chips, `Headline` title, muted subtitle).

### Empty source (0 sources)

```
┌──────────────────────────────────────────────────┐
│ [inline logo]                        [Alex · Adult] │
├──────────────────────────────────────────────────┤
│ ── chips · "Step 1 of 1" + "M3U · XMLTV · M3U+EPG"│
│ Add your first source                             │  SourcePickerRail
│ Premium TV Player ships empty — you bring the     │
│ content. Paste a playlist or EPG URL …           │
│ [ Add Source ]    Sign Out (ghost)                │
└──────────────────────────────────────────────────┘
```

Visible whenever `GET /v1/sources?profileId=…` returns an empty list.
The primary CTA routes to the source-management flow (Run 15).

### Data flow

```
ProfilePicker ──(profileId)──► Home route
                                   │
                                   ▼
                          HomeViewModel
                             │    (SavedStateHandle[profileId])
                             ▼
                      HomeRepository.snapshot(profileId)
                      ├─ SourceRepository.list(profileId)   → /v1/sources
                      ├─ continue-watching rows (STUB)      → /v1/continue-watching in Run 16
                      ├─ favorites list (STUB)              → /v1/favorites in Run 15
                      └─ hero carousel derived from sources
```

`HomeRepository` builds the populated snapshot deterministically from
the live source list — no feature flags. Continue-watching, favorites,
and suggested become real endpoints in Runs 15-16 with no screen-side
change (the repo keeps the same return shape).

### Nav wiring

`Routes.HomePattern = "home?profileId={profileId}"` — profileId is
optional. The NavHost in `PremiumTvApp` reads it into
`SavedStateHandle[Routes.ProfileIdArg]`.

### State model (`HomeUiState`)

| State          | When                                    |
|----------------|-----------------------------------------|
| `Loading`      | Initial load and after `refresh()`      |
| `EmptySource`  | API returned zero sources               |
| `Populated`    | Sources exist — show hero + rows        |
| `Error`        | `/v1/sources` failed — shows retry/sign-out |

Unit tests in `ui/home/HomeViewModelTest.kt` cover all four states
(MockK + Turbine + `UnconfinedTestDispatcher`).

## Source management (Run 15)

Home's "Add Source" CTA, the source hero tile, and the Sources row all
route into `ui/sources/SourceManagementScreen`. Three flows:

- **List / edit / pause / delete** — `SourceManagementScreen`. Inline
  rename via `SourceEditScreen` callsite; Pause ↔ Resume with
  `PUT /v1/sources/{id}` `{isActive}`; Delete goes through a full-screen
  confirmation overlay (`ConfirmDeleteOverlay`) before firing.
- **Add source** — `AddSourceWizardScreen` with a 4-step machine:
  1. `Kind` — three focusable radio cards (M3U, XMLTV, M3U+EPG)
  2. `Endpoint` — name + URL + optional credentials via `PremiumTextField`
  3. `Preview` — deterministic preview (channels / programme estimates)
     derived from the URL hash. Real preview (fetch + parse) is gated
     behind the Run 16 EPG worker so we don't block the wizard on a
     network round-trip.
  4. `Confirm` — `POST /v1/sources`; 402 → premium "requires active
     entitlement" copy; 409 SLOT_FULL maps to a friendly banner.
- **EPG browse** — `EpgBrowseScreen` for any source row: channels on
  the left gutter, programmes on a 30-minute timeline to the right.
  Focus any programme block to light up the Bravia-style
  `FocusedProgrammeOverlay` at the top with title, subtitle,
  description, and time range.

### Data

| Layer            | Artifact                                                    |
|------------------|-------------------------------------------------------------|
| API DTOs         | `CreateSourceRequest`, `UpdateSourceRequest`, `SingleSourceResponse` |
| Retrofit         | `listSources`, `createSource`, `updateSource(PUT)`, `deleteSource(DELETE 204)` |
| Repositories     | `SourceRepository.{list, create, rename, setActive, delete}`, `EpgRepository.browse(sourceId)` |
| Domain           | `SourceKind` enum (mirrors `source_kind` on the backend); `EpgChannel`, `EpgProgramme`, `EpgBrowseSnapshot` |

`EpgRepository.browse` returns fixture data in Run 15 — deterministic
programme blocks derived from the live source list so the screen is
exercisable without a running EPG worker. Run 16 swaps it for live
`/v1/epg/*` calls; `EpgBrowseSnapshot` keeps its shape.

### Nav routes

| Constant                        | Path                                  |
|---------------------------------|---------------------------------------|
| `Routes.Sources`                | `sources`                             |
| `Routes.AddSource`              | `sources/add`                         |
| `Routes.EpgBrowsePattern`       | `sources/{sourceId}/epg`              |

`Routes.epgBrowse(sourceId)` builds the path. `SourceIdArg` is the
non-nullable nav argument read by `EpgBrowseViewModel` via
`SavedStateHandle`.

### Tests

- `data/sources/SourceRepositoryTest.kt` — MockWebServer. Covers:
  create (POST body shape + parse), 402 → `ApiException.Server`,
  rename (PUT), setActive (PUT), delete 204 path, 404 mapping, 409
  SLOT_FULL mapping.
- `ui/sources/AddSourceWizardViewModelTest.kt` — MockK + Turbine.
  Covers: initial Kind state, guard "pick kind", Kind→Endpoint advance,
  Endpoint validation failure, preview generation for M3U+EPG
  (non-zero programmes) and plain M3U (zero programmes), Confirm
  happy-path → Done, Confirm with ENTITLEMENT_REQUIRED surfaces friendly
  error, back from Endpoint returns to Kind, cancel resets draft.

## Playback (Run 16)

`ui/player/PlayerScreen` hosts a Media3 `ExoPlayer` inside an
`AndroidView` and drives `PlayerViewModel`, which owns the backend
session lifecycle:

1. **Start** — `POST /v1/playback/start { profileId, sourceId, itemId, itemType }`
2. **Progress** — 1-second UI loop pumps `player.currentPosition` into
   `onProgress(...)`; state transitions driven by ExoPlayer's
   `Player.Listener`.
3. **Heartbeat** — every 10 s (`PlayerClock.heartbeatIntervalMs`) the
   VM posts `POST /v1/playback/heartbeat { sessionId, positionSeconds, state }`.
   Server updates `playback_sessions` + upserts `continue_watching`
   (for non-live items) in the same transaction.
4. **Stop** — `POST /v1/playback/stop { sessionId, finalPositionSeconds, completed }`
   writes a `watch_history` event and upserts (or clears, when
   `completed=true`) the CW row.

The overlay is bespoke — focusable `PremiumButton`s for -10s /
Play-Pause / +10s / Exit plus a `PremiumChip` stack for state +
position. No stock Media3 `PlayerView` UI.

### Nav route

```
play/{sourceId}/{itemId}/{itemType}
  ?profileId={profileId}&mediaUrl={urlEncoded}&itemTitle={urlEncoded}
```

`Routes.play(profileId, sourceId, itemId, itemType, mediaUrl, title)`
URL-encodes the item id / mediaUrl / title.

> **mediaUrl:** Run 16 passes the playback URL through the nav graph
> because a proper server-side URL resolver (decrypts source creds,
> signs short-lived playback URLs) is a follow-up — see
> CLAUDE.md Parking Lot "Playback URL resolver". Home deep-links fall
> back to Apple's public BipBop HLS feed + Big Buck Bunny MP4 so
> playback is exercisable today.

### Tests

- `PlaybackRepositoryTest` (MockWebServer) — 5 cases: start / heartbeat /
  stop body shape + parse, 402 ENTITLEMENT_REQUIRED, 404 mapping
- `PlayerViewModelTest` (MockK + UnconfinedTestDispatcher) — 5 cases:
  init → Buffering, onProgress → Playing, onPlayingStateChanged(false)
  → Paused, heartbeat fires exactly once when clock completes a sleep,
  stop posts final position + emits Stopped

### EPG worker integration

In Run 16 `EpgRepository.browse` calls the real
`GET /v1/epg/channels?sourceId=` + `GET /v1/epg/programmes?channelId=...`
endpoints. Data is populated by `services/epg-worker/` (see that
service's README) which polls XMLTV-bearing sources, parses via
`@premium-player/parsers`, and upserts `epg_channels` +
`epg_programs`. `EpgBrowseSnapshot` shape is identical to the Run 15
fixture — the UI didn't need to change.

Continue-Watching rail on the home screen is also live now: the row is
served by `GET /v1/continue-watching?profileId=` and populated by
playback heartbeats + the stop event.

## Billing flow (Run 17)

Google Play Billing wired into a bespoke premium paywall. Two one-time
products:

| Catalog | Product id                |
|---------|---------------------------|
| Single  | `premium_player_single`   |
| Family  | `premium_player_family`   |

These MUST match `BILLING_PRODUCT_ID_{SINGLE,FAMILY}` in
`services/api/.env.example`.

### Layer map

```
data/billing/
  ProductCatalog.kt       # PremiumProduct.Single / Family + display copy
  BillingClientWrapper.kt # Hilt @Singleton wrapper around BillingClient:
                          #   ensureReady(), queryProducts(),
                          #   launchPurchase(), queryExistingPurchases(),
                          #   purchaseFlow (Channel of success/cancel/error)
  BillingRepository.kt    # Facade used by PaywallViewModel:
                          #   querySkus, launchPurchase, acknowledgeAndVerify,
                          #   restore → /v1/billing/{verify,restore}
ui/billing/
  PaywallScreen.kt        # side-by-side plan cards; Family highlighted;
                          # Restore + Not Now bottom row
  PaywallViewModel.kt     # loads SKUs on init, collects BillingClient
                          # purchase results, routes success through
                          # acknowledgeAndVerify → PurchaseSucceeded
```

### Entitlement-aware gating

`HomeUiState.Error.isEntitlementGated` is `true` when the server
returned `code = "ENTITLEMENT_REQUIRED"`. `HomeScreen` watches the
state in a `LaunchedEffect` and calls `onOpenPaywall()` — users never
see a raw "requires active entitlement" banner, they see the paywall.

### Nav route

`Routes.Paywall = "paywall"` — registered in `PremiumTvApp`. The
paywall pops itself when the user completes a purchase, when restore
succeeds, or when the user explicitly presses "Not Now".

### Tests

- `BillingRepositoryTest` (MockWebServer, 4 cases): `acknowledgeAndVerify`
  POST body + parse, 402 ENTITLEMENT_REQUIRED mapping, `restore` POST,
  401 UNAUTHORIZED mapping.
- `PaywallViewModelTest` (MockK + UnconfinedTestDispatcher, 6 cases):
  init loads SKUs, init Error on failure, full purchase flow
  (BillingClient emit → verify → PurchaseSucceeded), user cancel
  clears submitting, restore success, restore failure surfaces
  friendly copy.

### Not unit-tested (requires Play-Store-equipped device)

- `BillingClient.launchBillingFlow(...)` — run manually on an
  emulator with Google Play Services + a signed test account linked to
  Play Console's test-track offers
- `BillingClientStateListener.onBillingSetupFinished` handshake

The server-side verification path (`BillingService.verifyAndApply`)
already has full test coverage in `services/api/src/billing/` from
Run 9, so the only untested layer on-device is the Play-Store handshake.

## Parental controls (Run 18)

Three new screens plus an `AgeFilter` helper round out Phase C.

### PinGate

`ui/parental/PinGateScreen` — full-screen PIN entry invoked before
switching into a PIN-protected profile. Numeric-only input via
`PremiumTextField`, lockout state surfaces a live countdown chip with
`MM:SS` until expiry. Server is the source of truth — we never
validate PINs locally. The VM handles the four server outcomes: `ok`
(Unlocked), `mismatch` (increment counter, clear PIN), `locked` (set
`lockedUntilIso` + red countdown chip), and `no_pin` (defensive
fallback → Unlocked).

### Profile management

`ui/parental/ProfileManagementScreen` — per-profile row editor:
inline rename, age-cap edit (0–21), PIN set / change / clear, default
flip, delete with confirmation overlay. Plus an "Add profile" form
underneath the list. All mutations route through the Run 10
`/v1/profiles` CRUD endpoints via `ProfileRepository`
(`create`, `update`, `delete`, `verifyPin`).

### Device management

`ui/parental/DeviceManagementScreen` — lists every device from
`/v1/devices`. Each row shows the platform chip, "This device" chip for
the current device, last-seen timestamp. "Revoke" button on each
non-revoked device opens a confirmation overlay; revoking the current
device calls the button "Sign Out This Device" instead. Wired to Run 8's
`POST /v1/devices/{id}/revoke`.

### Age filter

`data/parental/AgeFilter` — pure helper. `isAllowed(profileAgeLimit,
ratingString)` compares the caller's cap against the item's rating.
`ratingToMinAge(ratingString)` handles MPAA, US TV, BBFC, FSK, and
bare-number rating vocabularies. Unknown ratings default to allowed
(adult-safe default; kids-strict enforcement is a source-validation
polish pass).

### HomeHeader overflow

The top bar now exposes optional "Profiles" + "Devices" ghost buttons
next to the profile indicator. `HomeScreen` wires them to
`onOpenProfileSettings()` + `onOpenDeviceSettings()` which the NavHost
maps to `Routes.ProfileManagement` + `Routes.DeviceManagement`.

### Routes

| Constant                     | Path                                               |
|------------------------------|----------------------------------------------------|
| `Routes.ProfileManagement`   | `profiles/manage`                                  |
| `Routes.DeviceManagement`    | `devices/manage`                                   |
| `Routes.PinGatePattern`      | `profiles/{profileId}/pin-gate?profileName={name}` |

`Routes.pinGate(profileId, profileName)` URL-encodes the profile name.

### Tests

- `data/parental/AgeFilterTest` (7 cases) — null cap allows all,
  null rating allows under any cap, MPAA + TV-MA + FSK + BBFC rating
  mappings, unknown rating returns null.
- `data/profiles/ProfileRepositoryCrudTest` (7 cases, MockWebServer) —
  create POST body + parse, update partial body + `clearPin` flag, 409
  SLOT_FULL, delete 204 happy path, 409 VALIDATION_ERROR on last-
  profile delete, verify-pin result parse.
- `ui/parental/PinGateViewModelTest` (7 cases, MockK) — initial state,
  onPinChange filter/cap, short-PIN guard, successful verify → Unlocked,
  mismatch clears PIN + increments counter, locked carries
  lockedUntilIso, no_pin defensive Unlocked.
- `ui/parental/DeviceManagementViewModelTest` (5 cases, MockK) — init
  loads, init Error on 401, requestRevoke + confirmRevoke updates list,
  confirmRevoke without request is a no-op, revoke error surfaces
  errorMessage.

## Build + run

> **Tooling required (cannot be run in this repo's CI sandbox — Android
> SDK absent):** Android Studio Hedgehog or newer, JDK 17, an Android TV
> emulator (Google TV image, API 30+ recommended).

From `apps/android-tv/`:

```bash
# Generate the Gradle wrapper jar on first checkout (Studio does this
# automatically when you import the project).
gradle wrapper --gradle-version 8.11

# Build a debug APK
./gradlew :app:assembleDebug

# Install + launch on a connected emulator / device
./gradlew :app:installDebug
adb shell am start -n com.premiumtvplayer.app/.MainActivity
```

You should see a centered cinematic splash:

- Deep dark gradient (brand-tinted lift in the upper-left)
- Brand-blue circular play-button glyph
- "Premium TV Player" — large display type
- Tagline in muted secondary
- Three accent-cyan progress bars
- `v0.1.0 · build 1` build pill bottom-right

## Verifying the Leanback registration

```bash
aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep -i leanback
# Expected: uses-feature: name='android.software.leanback'
#           uses-feature-not-required: name='android.hardware.touchscreen'
```

## License

Proprietary — see repo root `LICENSE`.
