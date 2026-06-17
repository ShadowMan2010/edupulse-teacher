# EduPulse — QR Attendance System

A cyberpunk-themed QR code attendance system with a web frontend, local API server, and a full-screen kiosk scanner.

## Overview

| Component | Stack |
|---|---|
| **Web Dashboard** | Firebase v8 JS SDK, Realtime Database, Hosting |
| **Admin Panel** | multi-page HTML + CSS (cyberpunk neon theme) |
| **Local API** | Node.js + Express + sql.js |
| **Scanner Kiosk** | PyQt5, OpenCV, pyzbar, custom 3D card animations |
| **Boot / OS** | Plymouth theme + kiosk setup scripts for live USB / Pi |

## Features

- 📷 **QR Scanner** — real-time QR decoding via webcam or DroidCam IP camera
- 🃏 **3D Card Flip** — animated student ID card with particle burst on scan
- 🎨 **Cyberpunk UI** — glassmorphism, scan lines, neon glow, floating particles
- 📊 **Reports** — Excel/PDF export, daily attendance summaries
- 🔄 **Duplicate Prevention** — configurable cooldown window
- 🎵 **Audio Feedback** — success/error sound effects on scan
- 💻 **Kiosk OS** — boot-to-scanner Plymouth theme + LightDM auto-login scripts

## Getting Started

### Scanner (Desktop Kiosk)

```bash
cd scanner
pip install -r requirements.txt
python3 scanner.py
```

Set camera source via environment:

```bash
export EDUPULSE_CAMERA="http://192.168.1.100:4747/video"
python3 scanner.py
```

### Local API Server

```bash
node server.js
# Serves on http://localhost:3000
```

### Web Frontend

Serve `index.html` locally or deploy to Firebase Hosting:

```bash
firebase deploy --only hosting
```

## Building a Kiosk OS

**Convert an existing Ubuntu/Debian to a kiosk:**

```bash
sudo bash os/setup-kiosk.sh
```

**Build a bootable live USB ISO:**

```bash
sudo bash os/build-iso.sh
# Then: sudo dd if=output/edupulse-kiosk-*.iso of=/dev/sdX bs=4M status=progress
```

**Build a Raspberry Pi image:**

```bash
sudo bash os/build-pi.sh
# Then write output/edupulse-pi-*.img.xz to SD card
```

## Project Structure

```
edupulse/
├── index.html               # Firebase web app entry
├── server.js                # Local Express API + sql.js
├── firebase.json            # Firebase hosting config
├── js/firebase-config.js    # Firebase init + RTDB helpers
├── css/theme.css            # Global cyberpunk theme
├── pages/
│   ├── login.html
│   └── admin/               # Dashboard, attendance, students, reports, settings
│       └── principal/       # Principal dashboard
├── scanner/
│   ├── scanner.py           # PyQt5 kiosk scanner with 3D card
│   ├── sounds/              # success.wav, error.wav
│   └── requirements.txt
├── functions/index.js       # Firebase Cloud Function (undeployed)
├── os/
│   ├── setup-kiosk.sh       # Kiosk conversion script
│   ├── build-iso.sh         # Live USB ISO builder
│   ├── build-pi.sh          # Raspberry Pi image builder
│   └── plymouth/            # Custom boot theme (Orbitron, cyberpunk)
└── firebase.json/.firebaserc
```

## Configuration

Key environment variables:

| Variable | Default | Description |
|---|---|---|
| `EDUPULSE_CAMERA` | `0` | Camera source (device index or IP URL) |
| `EDUPULSE_ROTATION` | `0` | Display rotation (0/90/180/270) |
| `CLOUD_API` (in `scanner.py`) | `False` | Use Firebase Cloud Functions vs local API |

## License

MIT
