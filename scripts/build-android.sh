#!/usr/bin/env bash
# EduPulse Teacher Android App Builder
# Builds the Android APK from source
#
# Prerequisites:
#   - Android SDK (set ANDROID_HOME)
#   - Java 17+
#   - Gradle (or use the wrapper)
#
# Usage:
#   bash scripts/build-android.sh [--release|--debug]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$PROJECT_DIR/android"
BUILD_MODE="${1:---debug}"

if [ -z "$ANDROID_HOME" ]; then
    # Common Android SDK locations
    for dir in "$HOME/Android/Sdk" "$HOME/android-sdk" "/opt/android-sdk" "/usr/lib/android-sdk"; do
        if [ -d "$dir" ]; then
            export ANDROID_HOME="$dir"
            break
        fi
    done
fi

if [ -z "$ANDROID_HOME" ]; then
    echo "ERROR: ANDROID_HOME not set and Android SDK not found in common locations."
    echo ""
    echo "Quick setup:"
    echo "  1. Install Android Studio: https://developer.android.com/studio"
    echo "  2. Set ANDROID_HOME: export ANDROID_HOME=\$HOME/Android/Sdk"
    echo "  3. Accept licenses: \$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses"
    echo ""
    echo "Or use the pre-built APK from: https://edupulse-attendance-qr.web.app/edupulse-teacher.apk"
    exit 1
fi

echo "=== EduPulse Teacher APK Builder ==="
echo "ANDROID_HOME: $ANDROID_HOME"
echo "Build mode: $BUILD_MODE"

cd "$ANDROID_DIR"

if [ ! -f "gradlew" ]; then
    echo "Generating Gradle wrapper..."
    gradle wrapper --gradle-version 8.5 2>/dev/null || {
        # Create wrapper manually
        curl -sL "https://services.gradle.org/distributions/gradle-8.5-bin.zip" -o /tmp/gradle.zip
        unzip -qo /tmp/gradle.zip -d /tmp/gradle
        /tmp/gradle/gradle-8.5/bin/gradle wrapper --gradle-version 8.5
        rm -rf /tmp/gradle /tmp/gradle.zip
    }
fi

if [ "$BUILD_MODE" = "--release" ]; then
    echo "Building RELEASE APK..."
    ./gradlew assembleRelease
    APK_PATH="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"
else
    echo "Building DEBUG APK..."
    ./gradlew assembleDebug
    APK_PATH="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
fi

if [ -f "$APK_PATH" ]; then
    # Copy to project root for easy access
    cp "$APK_PATH" "$PROJECT_DIR/edupulse-teacher.apk"
    echo ""
    echo "✓ APK built successfully!"
    echo "  Location: $APK_PATH"
    echo "  Copy:     edupulse-teacher.apk"
    echo ""
    echo "Install on device:"
    echo "  adb install edupulse-teacher.apk"
else
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi
