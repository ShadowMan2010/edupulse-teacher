# EduPulse Teacher - Development Guide

## Build Commands

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew assembleRelease      # Build release APK (minified)
./gradlew installDebug         # Build and install on connected device
```

## Project Structure

```
android/
├── app/src/main/java/com/edupulse/teacher/
│   ├── MainActivity.java       # Single activity with all screens
│   ├── GlassCardView.java      # Custom glass card container
│   ├── MatrixRainView.java     # Matrix rain animation
│   ├── DigitalLoader.java      # Loading spinner animation
│   ├── GlitchTextView.java     # Glitching text effect
│   ├── CyberBgView.java        # Animated cyber background
│   ├── ScanPulseView.java      # Pulsing ring around scan button
│   └── ConfettiView.java       # Confetti burst effect
├── app/src/main/res/
│   ├── drawable/               # Glass cards, buttons, shapes
│   ├── layout/                 # activity_main + item layouts
│   ├── raw/                    # Sound effects
│   ├── values/                 # Strings, themes, attrs
│   └── xml/                    # FileProvider paths, network config
```

## Design System

- **Background**: #000000
- **Glass cards**: rgba(255,255,255,0.03-0.06) fill, rgba(255,255,255,0.08) 1dp border, 16dp radius
- **Accent**: #00E5FF (cyan)
- **Secondary**: #BB00FF (purple)
- **Success**: #00FF88
- **Error**: #FF3B3B
- **Text primary**: rgba(255,255,255,0.92)
- **Text secondary**: rgba(255,255,255,0.55)
- **Labels**: monospace, 10sp, rgba(255,255,255,0.4)
- **Buttons**: 52dp height, 14dp radius, monospace caps

## Key Dependencies

- Volley 1.2.1 (networking)
- zxing-android-embedded 4.3.0 (QR scanning)
- play-services-auth 20.7.0 (Google Sign-In)
- biometric 1.1.0 (fingerprint auth)
- Material 1.11.0
