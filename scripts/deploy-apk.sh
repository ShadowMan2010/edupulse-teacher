#!/usr/bin/env bash
# Build and deploy the APK to Firebase hosting
# The APK will be available at https://edupulse-attendance-qr.web.app/edupulse-teacher.apk

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

bash "$SCRIPT_DIR/build-android.sh" --release

echo ""
echo "Copying APK to project root for Firebase deploy..."
cp "$PROJECT_DIR/android/app/build/outputs/apk/release/app-release.apk" "$PROJECT_DIR/edupulse-teacher.apk"

echo "Deploying to Firebase..."
cd "$PROJECT_DIR"
npx firebase deploy --only hosting

echo ""
echo "✓ APK deployed!"
echo "  URL: https://edupulse-attendance-qr.web.app/edupulse-teacher.apk"
echo "  Update version.json after deploying a new APK"
