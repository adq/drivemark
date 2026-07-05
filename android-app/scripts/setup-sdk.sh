#!/usr/bin/env bash
set -euo pipefail

# Installs the Android SDK components required to build the app and accepts
# their licenses. Idempotent — safe to re-run. See docs/android-development.md.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Resolve a JDK for sdkmanager: prefer JAVA_HOME, else fall back to a system JDK,
# preferring the recommended JDK 21 and only then an older JDK 17.
if [ -z "${JAVA_HOME:-}" ] || [ ! -d "${JAVA_HOME:-}" ]; then
    for candidate in /usr/lib/jvm/java-21-openjdk /usr/lib/jvm/java-17-openjdk; do
        if [ -d "$candidate" ]; then
            JAVA_HOME="$candidate"
            break
        fi
    done
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -d "${JAVA_HOME:-}" ]; then
    echo "ERROR: No JDK found. Set JAVA_HOME to a JDK 21 install." >&2
    exit 1
fi
export JAVA_HOME
echo "Using JAVA_HOME=$JAVA_HOME"

# JDK 21 is the recommended runtime for Android development (Android Studio's
# bundled JetBrains Runtime). Warn — but don't block — if it's something else.
JAVA_MAJOR="$("$JAVA_HOME/bin/java" -version 2>&1 | head -n1 | grep -oE '"[0-9]+' | grep -oE '[0-9]+' | head -n1)"
if [ "${JAVA_MAJOR:-}" != "21" ]; then
    echo "WARNING: JDK ${JAVA_MAJOR:-unknown} detected; JDK 21 is recommended for Android development." >&2
    echo "         Install JDK 21 (or use Android Studio's bundled JBR) and point JAVA_HOME at it." >&2
fi

# Resolve the SDK root: honor ANDROID_HOME/ANDROID_SDK_ROOT, else default.
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
    echo "ERROR: sdkmanager not found at $SDKMANAGER" >&2
    echo "Set ANDROID_HOME to an SDK with cmdline-tools installed." >&2
    exit 1
fi
echo "Using Android SDK at $SDK_ROOT"

# Derive the API level from compileSdk so this tracks the build config.
API="$(grep -oE 'compileSdk[[:space:]]*=[[:space:]]*[0-9]+' "$PROJECT_DIR/app/build.gradle.kts" \
    | grep -oE '[0-9]+' | head -n1)"
API="${API:-36}"
echo "Target API level: $API"

echo "Accepting SDK licenses..."
# `yes` receives SIGPIPE (exit 141) once sdkmanager stops reading; under
# `pipefail` that would abort the script, so tolerate it explicitly.
yes | "$SDKMANAGER" --licenses >/dev/null || true

echo "Installing SDK components..."
"$SDKMANAGER" "platform-tools" "platforms;android-$API" "build-tools;$API.0.0"

echo ""
echo "Done. You can now run: ./gradlew assembleDebug"
