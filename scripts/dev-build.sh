#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../android-app"

echo "Building debug APK..."
./gradlew assembleDebug

echo ""
echo "Build complete: app/build/outputs/apk/debug/app-debug.apk"
