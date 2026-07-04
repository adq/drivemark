#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK="$SCRIPT_DIR/../android-app/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK" ]; then
    echo "Debug APK not found. Run dev-build.sh first."
    exit 1
fi

echo "Available devices:"
adb devices -l
echo ""

DEVICE="${1:-}"
if [ -n "$DEVICE" ]; then
    ADB="adb -s $DEVICE"
else
    ADB="adb"
fi

echo "Installing debug APK..."
$ADB install -r "$APK"

echo "Launching app..."
$ADB shell am start -n com.drivemark.app/.MainActivity
