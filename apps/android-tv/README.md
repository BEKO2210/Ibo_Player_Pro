# Premium TV Player — Android TV (V1 client)

Premium-tier TV player for Android TV / Google TV. Bespoke design system,
Compose-for-TV, Media3 / ExoPlayer, Hilt, Firebase Auth, Play Billing,
talking to the V1 NestJS backend over Retrofit.

> **Per-feature deep dives** (Run 12 design system, Run 13 onboarding, …
> Run 19 i18n + Diagnostics) live in
> [`docs/run-log.md`](./docs/run-log.md). This README is the slim entry
> point — start here, jump there for detail.

---

## Stack

- **Kotlin 2.0** + **Gradle 8.11** (Kotlin DSL, version catalog at
  `gradle/libs.versions.toml`)
- **Android Gradle Plugin 8.7**
- **Jetpack Compose** + **Compose for TV** (`androidx.tv:tv-foundation`,
  `tv-material`)
- **Hilt** (DI) + **Navigation-Compose**
- **Media3 / ExoPlayer 1.4** (HLS-aware)
- **Retrofit + kotlinx.serialization**
- **Firebase Auth** (programmatic init — no `google-services.json` plugin)
- **Google Play Billing 7.1**
- **Room** + **DataStore**
- `min SDK 26` (Adaptive Icons baseline; >99% of Android TV install base)
- `target SDK 34`

## applicationId — locked

```
com.premiumtvplayer.app
```

This **must** match `BILLING_ANDROID_PACKAGE_NAME` in
`services/api/.env.example`. If you change one, change both, and verify
with `./scripts/check-drift.sh`.

---

## Project layout

```
apps/android-tv/
  build.gradle.kts                        # root project
  settings.gradle.kts                     # one module: :app
  gradle/libs.versions.toml               # version catalog (single source of truth)
  app/
    build.gradle.kts                      # application module + BuildConfig fields
    proguard-rules.pro
    src/main/
      AndroidManifest.xml                 # Leanback feature + LEANBACK_LAUNCHER
      java/com/premiumtvplayer/app/
        PremiumTvApplication.kt           # @HiltAndroidApp
        MainActivity.kt                   # @AndroidEntryPoint, single Activity
        di/                               # NetworkModule, FirebaseModule
        data/                             # api · auth · entitlement · profiles ·
                                          #   sources · epg · billing · playback ·
                                          #   home · diagnostics · devices · parental
        ui/
          PremiumTvApp.kt                 # NavHost, transitions, Boot
          theme/                          # Color · Type · Spacing · Motion · Theme
          components/                     # design-system primitives (see below)
          nav/Routes.kt                   # all route constants
          onboarding/  home/  sources/
          player/  billing/  parental/
          diagnostics/
      res/
        drawable/                         # brand_logo.png · banner · launcher fg
        mipmap-anydpi-v26/                # adaptive launcher icons
        values/strings.xml                # ~138 i18n keys (EN baseline)
        values-de/strings.xml             # German seed (TODO-i18n review)
        values/{colors,themes,ic_launcher_background}.xml
  docs/run-log.md                         # per-run feature notes (Run 12-19)
```

---

## Design system

Tokens live behind these access points — never hard-code:

| Pull from | What |
|---|---|
| `PremiumColors` | every color literal |
| `PremiumType` | every text style |
| `LocalPremiumSpacing.current` | every dp value |
| `LocalPremiumShapes.current` | every corner radius |
| `LocalPremiumDurations.current` + `PremiumEasing` + `PremiumTransitions` | every animation |

Cross-platform source of truth is `packages/ui-tokens/src/index.ts`;
the Kotlin files mirror it verbatim. Always update the TS file first.

`./scripts/check-drift.sh` enforces zero raw `Color(0x…)` and zero raw
`TextStyle(…)` outside `ui/theme/`.

---

## Component catalog

All premium primitives under `ui/components/`. Detail in
[`docs/run-log.md`](./docs/run-log.md#component-catalog-run-12).

| Atoms | Molecules |
|---|---|
| `BrandLogo` `BootProgress` `PremiumChip` `PremiumButton` `PremiumTextField` `PremiumCard` `PremiumErrorBanner` | `RowOfTiles<T>` (focus-veil pattern) `HeroSection` |

Every component has at least one `@Preview` exercising it under
`PremiumTvTheme {}` against `BackgroundBase = 0xFF050608`.

---

## Build + run

> Tooling required (cannot be run in this repo's CI sandbox — Android SDK
> absent): Android Studio Hedgehog or newer, JDK 17, an Android TV emulator
> (Google TV image, API 30+ recommended).

```bash
# First checkout: generate the wrapper jar (Studio does this for you on import)
gradle wrapper --gradle-version 8.11

# Build, install, launch
./gradlew :app:assembleDebug
./gradlew :app:installDebug
adb shell am start -n com.premiumtvplayer.app/.MainActivity

# Unit tests
./gradlew :app:testDebugUnitTest
```

### `local.properties` — your dev secrets (gitignored)

```properties
firebaseApiKey=AIza…
firebaseProjectId=premium-player-prod
firebaseApplicationId=1:000000000000:android:0000000000000000000000

# optional override; default is the emulator → host loopback
# apiBaseUrl=http://192.168.1.50:3000/v1/
```

Firebase is initialized **programmatically** via `di/FirebaseModule.kt` —
no `google-services.json` plugin or file required. Drop your project
values into `local.properties` and rebuild.

| Target | Default `apiBaseUrl` |
|---|---|
| Emulator → local API | `http://10.0.2.2:3000/v1/` |
| Staging / Production | override via `-PapiBaseUrl=…` |

---

## Verifying the Leanback registration

```bash
aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep -i leanback
# expected:
#   uses-feature: name='android.software.leanback'
#   uses-feature-not-required: name='android.hardware.touchscreen'
```

If either line is missing, the app won't appear on the Google TV launcher.

---

## Reading order for new contributors

1. This file — layout, build, design-system pointer
2. [`docs/run-log.md`](./docs/run-log.md) — per-feature deep dives (Run 12-19)
3. Repo-root [`CLAUDE.md`](../../CLAUDE.md) — locked product decisions, current run, run log
4. Repo-root [`docs/CONTRIBUTING.md`](../../docs/CONTRIBUTING.md) — doc-drift contract you must satisfy on every PR
5. [`packages/ui-tokens/README.md`](../../packages/ui-tokens/README.md) — the cross-platform token source of truth

---

## License

Proprietary &middot; All Rights Reserved &middot; see repo-root
[`LICENSE`](../../LICENSE).
