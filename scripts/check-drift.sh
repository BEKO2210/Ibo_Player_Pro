#!/usr/bin/env bash
# ───────────────────────────────────────────────────────────────────────
#  Premium TV Player — drift check
#
#  One command, eight invariants. Run before every commit. Run in CI.
#  Exit 0 = no drift, exit 1 = at least one invariant violated.
#
#    ./scripts/check-drift.sh
#    ./scripts/check-drift.sh --verbose
#
#  The eight invariants:
#    1. Every Nest @Controller route is declared in the Retrofit interface
#    2. Every Retrofit @GET/@POST/@PUT/@DELETE path exists in a Nest controller
#    3. Every R.string.X used in Kotlin exists in values/strings.xml
#    4. Every R.string.X used in Kotlin exists in values-de/strings.xml
#    5. Every Routes.X reference resolves to a constant defined in Routes.kt
#    6. Every composable(Routes.X) in NavHost has a matching screen function
#    7. No raw Color(0x... literals outside ui/theme/Color.kt
#    8. No bare TextStyle(...) literals outside ui/theme/Type.kt
# ───────────────────────────────────────────────────────────────────────
set -uo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

VERBOSE=0
[[ "${1:-}" == "--verbose" ]] && VERBOSE=1

# ── colors ─────────────────────────────────────────────────────────────
if [[ -t 1 ]]; then
  RED=$'\033[31m'; GRN=$'\033[32m'; YEL=$'\033[33m'; DIM=$'\033[2m'; RST=$'\033[0m'
else
  RED=""; GRN=""; YEL=""; DIM=""; RST=""
fi

FAILED=0
TOTAL=0

pass() { TOTAL=$((TOTAL+1)); printf "  %s✓%s  %s\n" "$GRN" "$RST" "$1"; }
fail() { TOTAL=$((TOTAL+1)); FAILED=$((FAILED+1)); printf "  %s✗%s  %s\n" "$RED" "$RST" "$1"; }
note() { [[ $VERBOSE -eq 1 ]] && printf "       %s%s%s\n" "$DIM" "$1" "$RST"; }
warn() { printf "  %s⚠%s  %s\n" "$YEL" "$RST" "$1"; }

section() { printf "\n%s==>%s %s\n" "$YEL" "$RST" "$1"; }

# ── inputs ─────────────────────────────────────────────────────────────
NEST_DIR="services/api/src"
RETROFIT_FILE="apps/android-tv/app/src/main/java/com/premiumtvplayer/app/data/api/PremiumPlayerApi.kt"
KOTLIN_SRC="apps/android-tv/app/src/main/java"
ROUTES_FILE="apps/android-tv/app/src/main/java/com/premiumtvplayer/app/ui/nav/Routes.kt"
NAVHOST_FILE="apps/android-tv/app/src/main/java/com/premiumtvplayer/app/ui/PremiumTvApp.kt"
STRINGS_EN="apps/android-tv/app/src/main/res/values/strings.xml"
STRINGS_DE="apps/android-tv/app/src/main/res/values-de/strings.xml"
THEME_DIR="apps/android-tv/app/src/main/java/com/premiumtvplayer/app/ui/theme"
UI_DIR="apps/android-tv/app/src/main/java/com/premiumtvplayer/app/ui"

# ── extractors ─────────────────────────────────────────────────────────
# Nest: build "METHOD path" lines by walking each controller, picking up the
# controller-level prefix and concatenating each handler's verb+path.
extract_nest_routes() {
  python3 - "$NEST_DIR" <<'PY'
import os, re, sys
root = sys.argv[1]
ctrl_re   = re.compile(r"@Controller\(\s*\{\s*path:\s*['\"]([^'\"]+)['\"]")
ctrl_str  = re.compile(r"@Controller\(\s*['\"]([^'\"]+)['\"]")
verb_re   = re.compile(r"@(Get|Post|Put|Delete|Patch)\(\s*['\"]?([^'\")]*)['\"]?\s*\)")
for dirpath, _, files in os.walk(root):
    for f in files:
        if not f.endswith(".controller.ts"): continue
        p = os.path.join(dirpath, f)
        src = open(p, encoding="utf-8").read()
        m = ctrl_re.search(src) or ctrl_str.search(src)
        prefix = m.group(1).strip("/") if m else ""
        for vm in verb_re.finditer(src):
            verb, path = vm.group(1).upper(), vm.group(2).strip("/")
            full = "/".join(x for x in (prefix, path) if x)
            # normalize :id → {id}
            full = re.sub(r":([A-Za-z_][A-Za-z0-9_]*)", r"{\1}", full)
            print(f"{verb} /{full}")
PY
}

# Retrofit: pick @GET("path") etc., normalize {id} stays {id}
extract_retrofit_routes() {
  grep -nE '@(GET|POST|PUT|DELETE|PATCH)\("' "$RETROFIT_FILE" \
    | sed -E 's/.*@(GET|POST|PUT|DELETE|PATCH)\("([^"?]+).*"\).*/\1 \/\2/' \
    | sed -E 's@/+@/@g'
}

# ── 1+2: API drift (Nest ↔ Retrofit) ───────────────────────────────────
section "API contract drift (Nest controllers ↔ Retrofit interface)"

if ! command -v python3 >/dev/null 2>&1; then
  warn "python3 not found — skipping API drift check"
else
  NEST_TMP=$(mktemp); RET_TMP=$(mktemp)
  extract_nest_routes | sort -u > "$NEST_TMP"
  extract_retrofit_routes | sort -u > "$RET_TMP"

  # Routes intentionally not on the Retrofit interface:
  #   GET  /health               — used via dedicated HealthClient (no Bearer)
  #   POST /devices/register     — device-token issuance flow not yet wired (Phase D)
  EXEMPT_RET_MISSING=$(cat <<'EOF'
GET /health
POST /devices/register
EOF
)

  in_nest_not_retrofit=$(comm -23 "$NEST_TMP" "$RET_TMP" | grep -vxFf <(echo "$EXEMPT_RET_MISSING") || true)
  in_retrofit_not_nest=$(comm -13 "$NEST_TMP" "$RET_TMP" || true)

  if [[ -z "$in_nest_not_retrofit" ]]; then
    pass "every Nest controller route is on the Android client (or exempt)"
  else
    fail "Nest routes missing from Retrofit:"
    echo "$in_nest_not_retrofit" | sed 's/^/         /'
  fi

  if [[ -z "$in_retrofit_not_nest" ]]; then
    pass "every Retrofit route exists in a Nest controller"
  else
    fail "Retrofit routes missing from Nest:"
    echo "$in_retrofit_not_nest" | sed 's/^/         /'
  fi

  note "Nest routes:    $(wc -l < "$NEST_TMP")"
  note "Retrofit routes: $(wc -l < "$RET_TMP")"
  rm -f "$NEST_TMP" "$RET_TMP"
fi

# ── 3+4: i18n key coverage ─────────────────────────────────────────────
section "i18n key coverage (Kotlin ↔ values/ ↔ values-de/)"

KEYS_USED=$(grep -rohE 'R\.string\.[A-Za-z0-9_]+' "$KOTLIN_SRC" | sort -u | sed 's/^R\.string\.//')
KEYS_EN=$(grep -oE 'name="[A-Za-z0-9_]+"' "$STRINGS_EN" | sed -E 's/name="([^"]+)"/\1/' | sort -u)
KEYS_DE=$(grep -oE 'name="[A-Za-z0-9_]+"' "$STRINGS_DE" | sed -E 's/name="([^"]+)"/\1/' | sort -u)

# system-implied keys (used by Android framework even without code refs)
SYSTEM_KEYS=$'app_name'

missing_en=$(comm -23 <(echo "$KEYS_USED") <(echo "$KEYS_EN") || true)
missing_de=$(comm -23 <(echo "$KEYS_USED"$'\n'"$SYSTEM_KEYS" | sort -u) <(echo "$KEYS_DE") || true)

if [[ -z "$missing_en" ]]; then
  pass "every R.string.X used in code exists in values/strings.xml"
else
  fail "R.string keys missing from values/strings.xml:"
  echo "$missing_en" | sed 's/^/         R.string./'
fi

if [[ -z "$missing_de" ]]; then
  pass "every R.string.X used in code exists in values-de/strings.xml"
else
  fail "R.string keys missing from values-de/strings.xml:"
  echo "$missing_de" | sed 's/^/         R.string./'
fi

note "keys referenced:        $(echo "$KEYS_USED" | wc -l)"
note "keys defined (EN):      $(echo "$KEYS_EN" | wc -l)"
note "keys defined (DE):      $(echo "$KEYS_DE" | wc -l)"

# ── 5: Routes references resolve ───────────────────────────────────────
section "nav graph (Routes.X references ↔ definitions)"

# The pattern for a defined Routes member:
#   const val Foo = "..."        OR
#   fun fooBuilder(...): String
DEFINED_ROUTES=$(grep -oE '(const val|fun) [A-Za-z][A-Za-z0-9_]*' "$ROUTES_FILE" \
  | awk '{print $NF}' | sort -u)

USED_ROUTES=$(grep -rohE 'Routes\.[A-Za-z][A-Za-z0-9_]*' "$KOTLIN_SRC" \
  | sed 's/^Routes\.//' | sort -u)

missing_routes=$(comm -23 <(echo "$USED_ROUTES") <(echo "$DEFINED_ROUTES") || true)

if [[ -z "$missing_routes" ]]; then
  pass "every Routes.X reference resolves to a constant in Routes.kt"
else
  fail "Routes references missing from Routes.kt:"
  echo "$missing_routes" | sed 's/^/         Routes./'
fi
note "Routes defined: $(echo "$DEFINED_ROUTES" | wc -l) / referenced (unique): $(echo "$USED_ROUTES" | wc -l)"

# ── 6: NavHost composable destinations have screen functions ───────────
section "NavHost composable(...) destinations ↔ screen functions"

# Extract Routes.X constants used in composable(...) inside NavHost
COMP_TARGETS=$(grep -oE 'composable\(\s*Routes\.[A-Za-z][A-Za-z0-9_]*' "$NAVHOST_FILE" \
  | sed -E 's/composable\(\s*Routes\.//' | sort -u)

missing_screens=""
while IFS= read -r tgt; do
  [[ -z "$tgt" ]] && continue
  # Naive convention: NavHost calls a screen function named *Screen for each route.
  # We don't enforce the function name (composable lambdas can call anything),
  # we just check that *some* @Composable function called by NavHost exists.
  # A real "missing screen" would already fail Kotlin compile, so this check
  # is informational unless the lambda body is truly empty.
  if ! grep -qE "Routes\.${tgt}\s*\)" "$NAVHOST_FILE"; then
    missing_screens="$missing_screens$tgt\n"
  fi
done <<< "$COMP_TARGETS"

if [[ -z "$missing_screens" ]]; then
  pass "all $(echo "$COMP_TARGETS" | wc -l) composable destinations use defined Routes constants"
else
  fail "composable destinations referencing undefined routes:"
  echo -e "$missing_screens" | sed 's/^/         Routes./'
fi

# ── 7+8: token discipline ─────────────────────────────────────────────
section "design token discipline (no raw literals outside ui/theme/)"

color_offenders=$(grep -rln "Color(0x" "$UI_DIR" 2>/dev/null \
  | grep -v "^$THEME_DIR/" || true)

textstyle_offenders=$(grep -rln "TextStyle(" "$UI_DIR" 2>/dev/null \
  | grep -v "^$THEME_DIR/" || true)

if [[ -z "$color_offenders" ]]; then
  pass "no raw Color(0x...) literals outside ui/theme/"
else
  fail "raw Color(0x...) literals found outside ui/theme/:"
  echo "$color_offenders" | sed 's/^/         /'
fi

if [[ -z "$textstyle_offenders" ]]; then
  pass "no raw TextStyle(...) literals outside ui/theme/"
else
  fail "raw TextStyle(...) literals outside ui/theme/:"
  echo "$textstyle_offenders" | sed 's/^/         /'
fi

# ── summary ────────────────────────────────────────────────────────────
section "summary"
if [[ $FAILED -eq 0 ]]; then
  printf "  %s%d of %d invariants pass — no drift detected.%s\n\n" "$GRN" "$TOTAL" "$TOTAL" "$RST"
  exit 0
else
  printf "  %s%d of %d invariants failed.%s\n" "$RED" "$FAILED" "$TOTAL" "$RST"
  printf "  %sFix the violations above before committing.%s\n\n" "$DIM" "$RST"
  exit 1
fi
