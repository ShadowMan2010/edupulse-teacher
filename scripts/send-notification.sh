#!/usr/bin/env bash
# Send a test push notification to the teacher app via the local server
# Usage: ./scripts/send-notification.sh [title] [body]
set -euo pipefail

BASE="http://localhost:3000"
TITLE="${1:-Test Notification}"
BODY="${2:-This is a push notification from EduPulse server}"

echo "Sending notification..."
echo "  Title: $TITLE"
echo "  Body:  $BODY"
echo ""

# Try the most recently registered token first
TOKEN=$(curl -sf "$BASE/api/fcm/tokens" | python3 -c "
import sys, json
d = json.load(sys.stdin)
tokens = d.get('data', {})
if tokens:
    last = sorted(tokens.keys())[-1]
    print(tokens[last])
" 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
  echo "❌ No FCM tokens registered. Connect the teacher app first."
  echo "   Or pass a token directly: SEND_TOKEN=... $0"
  exit 1
fi

curl -s "$BASE/api/fcm/send" \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"$TOKEN\",\"title\":\"$TITLE\",\"body\":\"$BODY\"}" | python3 -m json.tool
