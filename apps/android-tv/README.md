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
