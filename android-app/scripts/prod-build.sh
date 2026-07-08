#!/usr/bin/env bash
set -euo pipefail

# Build the production (release) DriveMark Android artifacts.
#
# Usage:
#   scripts/prod-build.sh [--bump patch|minor|major]
#
# The script:
#   1. Preflights the production prerequisites (config.prod.properties + keystore.properties)
#   2. Optionally bumps versionName/versionCode in app/build.gradle.kts
#   3. Builds the signed release AAB (Play Store) and APK (sideload)
#   4. Prints the output paths and next steps
#
# Release builds automatically read config.prod.properties (prod OAuth client ID)
# and sign with keystore.properties — see app/build.gradle.kts.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
GRADLE_FILE="$APP_DIR/app/build.gradle.kts"

die() { echo "error: $*" >&2; exit 1; }

# --- Parse args -----------------------------------------------------------

BUMP=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --bump)
      [[ -n "${2:-}" ]] || die "--bump requires patch, minor, or major"
      BUMP="$2"; shift 2 ;;
    -h|--help)
      sed -n '4,13s/^# //p' "$0"; exit 0 ;;
    *)
      die "unknown argument: $1" ;;
  esac
done

# --- Preflight -------------------------------------------------------------

# config.prod.properties is gitignored — without it the release build ships
# MISSING_PROD_WEB_CLIENT_ID as the OAuth client ID and sign-in breaks.
[[ -f "$APP_DIR/config.prod.properties" ]] \
  || die "config.prod.properties missing — run: cp config.prod.example.properties config.prod.properties (then set the production webClientId)"

# keystore.properties is gitignored — without it the release build is UNSIGNED
# and cannot be uploaded to the Play Store.
[[ -f "$APP_DIR/keystore.properties" ]] \
  || die "keystore.properties missing — release builds would be unsigned. See docs/android-development.md#release-keystore"

# Warn (don't block) if the prod config still holds the example placeholder.
if grep -q "YOUR_WEB_CLIENT_ID" "$APP_DIR/config.prod.properties"; then
  echo "WARNING: config.prod.properties still contains the placeholder YOUR_WEB_CLIENT_ID." >&2
  echo "         Set a real production Web OAuth client ID before shipping." >&2
fi

# --- Version bump ----------------------------------------------------------

[[ -f "$GRADLE_FILE" ]] || die "app/build.gradle.kts not found at $GRADLE_FILE"

if [[ -n "$BUMP" ]]; then
  [[ "$BUMP" =~ ^(patch|minor|major)$ ]] || die "--bump must be patch, minor, or major"

  OLD_VER="$(grep -oE 'versionName[[:space:]]*=[[:space:]]*"[^"]*"' "$GRADLE_FILE" \
    | head -1 | sed 's/.*"\([^"]*\)".*/\1/')"
  [[ -n "$OLD_VER" ]] || die "could not read versionName from app/build.gradle.kts"

  OLD_CODE="$(grep -oE 'versionCode[[:space:]]*=[[:space:]]*[0-9]+' "$GRADLE_FILE" \
    | head -1 | grep -oE '[0-9]+')"
  [[ -n "$OLD_CODE" ]] || die "could not read versionCode from app/build.gradle.kts"

  IFS='.' read -r MAJ MIN PAT <<< "$OLD_VER"
  case "$BUMP" in
    major) MAJ=$((MAJ + 1)); MIN=0; PAT=0 ;;
    minor) MIN=$((MIN + 1)); PAT=0 ;;
    patch) PAT=$((PAT + 1)) ;;
  esac
  NEW_VER="$MAJ.$MIN.$PAT"
  NEW_CODE=$((OLD_CODE + 1))

  sed -i "s/versionName[[:space:]]*=[[:space:]]*\"$OLD_VER\"/versionName = \"$NEW_VER\"/" "$GRADLE_FILE"
  sed -i "s/versionCode[[:space:]]*=[[:space:]]*$OLD_CODE\b/versionCode = $NEW_CODE/" "$GRADLE_FILE"
  echo "Bumped version: $OLD_VER -> $NEW_VER (versionCode $OLD_CODE -> $NEW_CODE)"
fi

# --- Build -----------------------------------------------------------------

cd "$APP_DIR"
echo "Building signed release AAB + APK..."
./gradlew bundleRelease assembleRelease

echo ""
echo "Build complete:"
echo "  AAB: app/build/outputs/bundle/release/app-release.aab"
echo "  APK: app/build/outputs/apk/release/app-release.apk"
echo ""
echo "Next steps:"
echo "  1. Upload the AAB to the Play Console (or sideload the APK for testing)"
echo "  2. Verify the APK signature:"
echo "       apksigner verify --verbose app/build/outputs/apk/release/app-release.apk"
echo "     See docs/android-development.md for the full release flow."
