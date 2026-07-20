#!/usr/bin/env bash
# Downloads the Firebase service account key for FCM push notifications.
#
# Prerequisites:
#   1. firebase-tools installed (already have it)
#   2. Run: firebase login (if not already logged in)
#
# This script opens the Firebase Console so you can generate the key manually.
set -euo -pipefail

PROJECT="edupulse-attendance-qr"
OUTPUT="/home/dhruba/edupulse/firebase-service-account.json"

echo "1. Open this URL in your browser:"
echo "   https://console.firebase.google.com/project/$PROJECT/settings/serviceaccounts/adminsdk"
echo ""
echo "2. Click 'Generate new private key'"
echo "3. Move the downloaded JSON to:"
echo "   $OUTPUT"
echo ""
echo "Then restart the server for FCM push to work."
