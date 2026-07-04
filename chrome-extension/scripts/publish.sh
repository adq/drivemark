#!/usr/bin/env bash
set -euo pipefail

# Package the DriveMark Chrome extension for Chrome Web Store upload.
#
# Usage:
#   npm run package -- [--bump patch|minor|major]   (from chrome-extension/)
#   chrome-extension/scripts/publish.sh [--bump patch|minor|major]
#
# The script:
#   1. Optionally bumps the version in manifest.json
#   2. Creates a clean ZIP with the key field stripped
#   3. Outputs the ZIP path and next steps

EXT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="$EXT_DIR/manifest.json"

# Files/dirs to include in the ZIP (relative to chrome-extension/)
# config.js is intentionally omitted — it's staged from config.prod.js below.
INCLUDE=(
  manifest.json
  background.js
  popup.html
  popup.js
  popup.css
  auth.js
  browser.js
  save-form.js
  spreadsheet-picker.js
  lib/
  vendor/
  icons/
)

die() { echo "error: $*" >&2; exit 1; }

# --- Parse args -----------------------------------------------------------

BUMP=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --bump)
      [[ -n "${2:-}" ]] || die "--bump requires patch, minor, or major"
      BUMP="$2"; shift 2 ;;
    -h|--help)
      sed -n '3,8s/^# //p' "$0"; exit 0 ;;
    *)
      die "unknown argument: $1" ;;
  esac
done

[[ -f "$MANIFEST" ]] || die "manifest.json not found at $MANIFEST"

# --- Helpers ---------------------------------------------------------------

# Read a top-level string field from manifest.json without jq
json_field() {
  grep -o "\"$1\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" "$MANIFEST" \
    | head -1 \
    | sed 's/.*:[[:space:]]*"\([^"]*\)".*/\1/'
}

# --- Version bump ----------------------------------------------------------

if [[ -n "$BUMP" ]]; then
  [[ "$BUMP" =~ ^(patch|minor|major)$ ]] || die "--bump must be patch, minor, or major"

  OLD_VER="$(json_field version)"
  [[ -n "$OLD_VER" ]] || die "could not read version from manifest.json"

  IFS='.' read -r MAJ MIN PAT <<< "$OLD_VER"
  case "$BUMP" in
    major) MAJ=$((MAJ + 1)); MIN=0; PAT=0 ;;
    minor) MIN=$((MIN + 1)); PAT=0 ;;
    patch) PAT=$((PAT + 1)) ;;
  esac
  NEW_VER="$MAJ.$MIN.$PAT"

  sed -i "s/\"version\"[[:space:]]*:[[:space:]]*\"$OLD_VER\"/\"version\": \"$NEW_VER\"/" "$MANIFEST"
  echo "Bumped version: $OLD_VER -> $NEW_VER"
fi

VERSION="$(json_field version)"

# --- Build vendored libs ---------------------------------------------------

# vendor/ is a gitignored build artifact — regenerate it so the ZIP always
# ships the pinned Preact/htm. Requires deps installed (npm install).
echo "Building vendored libs..."
node "$EXT_DIR/scripts/build-vendor.mjs" \
  || die "vendor build failed — run 'npm install' in chrome-extension/ first"

# --- Validate required files -----------------------------------------------

MISSING=()
for entry in "${INCLUDE[@]}"; do
  [[ -e "$EXT_DIR/$entry" ]] || MISSING+=("$entry")
done

if [[ ${#MISSING[@]} -gt 0 ]]; then
  die "missing required files: ${MISSING[*]}"
fi

# --- Build ZIP -------------------------------------------------------------

# config.prod.js is gitignored — the package always ships it as config.js, so your
# local dev config.js is not used when packaging.
[[ -f "$EXT_DIR/config.prod.js" ]] || die "config.prod.js missing — run: cp config.prod.example.js config.prod.js (then set the production CLIENT_ID)"

STAGING="$(mktemp -d)"
trap 'rm -rf "$STAGING"' EXIT

for entry in "${INCLUDE[@]}"; do
  if [[ -d "$EXT_DIR/$entry" ]]; then
    cp -r "$EXT_DIR/$entry" "$STAGING/$entry"
  else
    cp "$EXT_DIR/$entry" "$STAGING/$entry"
  fi
done

# Use production config in the package
cp "$EXT_DIR/config.prod.js" "$STAGING/config.js"

# Strip the "key" field from the staged manifest (Web Store assigns its own)
command -v python3 &>/dev/null || die "python3 is required to process manifest.json"
python3 -c "
import json, pathlib, sys
p = pathlib.Path(sys.argv[1])
m = json.loads(p.read_text())
m.pop('key', None)
p.write_text(json.dumps(m, indent=2) + '\n')
" "$STAGING/manifest.json"

OUT_ZIP="$EXT_DIR/drivemark-v${VERSION}.zip"
(cd "$STAGING" && zip -rq "$OUT_ZIP" .)

echo ""
echo "Packaged: $OUT_ZIP"
echo "Version:  $VERSION"
echo ""
echo "Next steps:"
echo "  1. Open https://chrome.google.com/webstore/devconsole"
echo "  2. Upload the ZIP (New Item or Package -> Upload new package)"
echo "  3. After first upload, note the assigned extension ID and update"
echo "     the OAuth redirect URI in Google Cloud Console — see docs/publication.md"
