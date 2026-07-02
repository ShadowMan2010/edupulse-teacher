# EduPulse Scanner

## Security: Firebase Admin SDK Key

The admin SDK key has been moved OUTSIDE the project tree for security.

**Current location:** `~/.config/edupulse/firebase-adminsdk-key.json`

The scanner reads the key in this order:
1. `FIREBASE_ADMIN_KEY_PATH` environment variable
2. `~/.config/edupulse/firebase-adminsdk-key.json`
3. `scanner/firebase-adminsdk-key.json` (legacy, no longer shipped)

**Do NOT place a copy of the key inside the project directory.** If a copy exists
in `scanner/` it will NOT be deployed to Firebase Hosting (ignored via firebase.json),
but it still poses a local security risk.

File permissions on the key should be `600` (owner read/write only).
