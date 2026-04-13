# Contributing — the Doc-Drift Contract

This file exists for **one reason**: to make sure documentation, contracts,
and code never drift apart. Drift is the only realistic way this project
loses quality, and we close that door deliberately.

The contract is short:

> **Every code change updates the documentation surface that owns it. No exceptions.**

If the change touches an API path, the OpenAPI yaml updates in the same
commit. If the change touches a Compose route, `Routes.kt` and the NavHost
update in the same commit. If you find yourself thinking "I'll update the
docs after", **stop, stash, update the docs first**.

---

## Pre-commit checklist (copy-paste, every commit)

```bash
./scripts/check-drift.sh        # 8 invariants, exits non-zero on drift
cd services/api && npm test     # backend + workers + parsers
cd apps/android-tv && ./gradlew :app:testDebugUnitTest
```

Drift check covers, in 8 invariants:

1. Every Nest controller route is on the Retrofit interface (or in the
   exempt list inside the script with a documented reason)
2. Every Retrofit route exists in a Nest controller
3. Every `R.string.X` referenced in Kotlin exists in `values/strings.xml`
4. Every `R.string.X` referenced in Kotlin exists in `values-de/strings.xml`
5. Every `Routes.X` reference resolves to a constant in `Routes.kt`
6. Every `composable(Routes.X)` in NavHost references a defined route
7. No raw `Color(0x…)` literals outside `ui/theme/Color.kt`
8. No raw `TextStyle(…)` literals outside `ui/theme/Type.kt`

A failing drift check is a build failure, not a warning.

---

## Per-surface ownership matrix

When you change a surface in the **left column**, you also update **every
file in the right column in the same commit**. If a row doesn't fit your
change, your change probably crosses surfaces — stop and split it.

| Surface you touched | Files you must update |
|---|---|
| **HTTP API** (any controller method, body shape, status code, error code) | `services/api/src/**/*.controller.ts` &middot; `packages/api-contracts/openapi.yaml` &middot; `packages/api-contracts/src/zod.ts` &middot; `apps/android-tv/.../data/api/PremiumPlayerApi.kt` (if reachable from app) &middot; `apps/android-tv/.../data/api/ApiModels.kt` |
| **Entitlement state machine** (states, events, transitions, caps) | `services/api/src/entitlement/entitlement.state-machine.ts` &middot; `docs/architecture/entitlement-state-machine.md` &middot; `services/api/src/entitlement/state-machine.spec.ts` |
| **Database schema** (any column, index, constraint, enum) | `services/api/prisma/schema.prisma` &middot; new migration in `services/api/prisma/migrations/` &middot; `docs/architecture/data-model.md` |
| **Nav route** (add, remove, rename, change args) | `apps/android-tv/.../ui/nav/Routes.kt` &middot; `apps/android-tv/.../ui/PremiumTvApp.kt` (NavHost) &middot; the originating Screen's call sites |
| **Compose screen** (new screen, new callback param) | the screen file &middot; `Routes.kt` if it has a route &middot; the NavHost composable wiring &middot; `apps/android-tv/README.md` "Screens" section |
| **i18n string** (add a key, change English copy) | `apps/android-tv/.../res/values/strings.xml` &middot; `apps/android-tv/.../res/values-de/strings.xml` (mark `TODO-i18n` if seed) |
| **Design token** (color, type, spacing, motion, radius) | `apps/android-tv/.../ui/theme/{Color,Type,Spacing,Motion,Theme}.kt` &middot; `packages/ui-tokens/src/index.ts` (cross-platform mirror) |
| **Billing event mapping** (new SKU, new state mapping) | `services/api/src/billing/billing.service.ts` &middot; `services/api/src/billing/billing.service.spec.ts` &middot; `docs/architecture/entitlement-state-machine.md` (Billing Events section) &middot; `services/api/.env.example` |
| **Encryption format** (source credential wire format, key derivation) | `services/api/src/sources/source-crypto.service.ts` &middot; `services/api/src/sources/source-crypto.service.spec.ts` &middot; `docs/operations/SECRETS.md` (rotation procedure) |
| **Worker** (new poller, changed cadence, new env) | `services/{billing,epg}-worker/src/**/*.ts` &middot; the worker's `README.md` &middot; `services/api/.env.example` for shared env keys |
| **Operations runbook** (a new failure mode you encountered) | `docs/operations/RUNBOOKS.md` &middot; the relevant `docs/operations/*.md` if it changes the recovery path |
| **Project plan** (Run completed, Run rescoped, Parking Lot item) | `CLAUDE.md` (Roadmap tick + Run Log entry + Next Run block + Current State) |

---

## Branch rules (absolute)

- All work happens on the **single** branch named in `CLAUDE.md` →
  **Current State → Current branch**. Today: `claude/fix-api-timeout-vFqPP`.
- Push target is the same branch (`git push -u origin <branch>`). Network
  errors retry up to 4 times with exponential backoff (2s, 4s, 8s, 16s).
- **Never** push to `main` directly.
- **Never** force-push (`--force`, `--force-with-lease`) without an explicit
  request from the user in the same session.
- **Never** open a Pull Request unless the user explicitly asks for one.
- **Never** skip pre-commit hooks (`--no-verify`). If a hook fails, fix the
  underlying issue.

---

## Commit message format

```
<area>: <imperative summary> (Run N)

Optional body explaining *why*, not what. The diff shows what.
```

`<area>` is one of: `docs`, `api`, `tv`, `worker`, `infra`, `release`,
`packages`, `ops`. Examples in the existing log:

```
api:    add billing module with Play verify, idempotent persist, and restore (Run 9 part 1)
tv:     bootstrap android-tv app with Compose-TV + Hilt + premium theme (Run 11)
docs:   add 10-language overview block to README
ops:    add restore + hardening runbooks
```

---

## When a doc *should* be ahead of the code

There is exactly one legal direction for drift: **specs land before the
implementation that fulfills them.** PRD, user flows, entitlement state
machine, OpenAPI — all of these can describe a future state. When that
happens, the file carries an explicit marker:

```markdown
> **Status:** spec, not yet implemented (target: Run 22).
```

Without that marker, the doc is a contract. Code must match it.

---

## When in doubt

Run the drift check. If it passes and the tests pass, you're good. If it
fails, the failure message tells you exactly which file to update.

```bash
./scripts/check-drift.sh --verbose
```
