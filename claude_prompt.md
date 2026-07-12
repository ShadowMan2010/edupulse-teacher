# Rebuild EduPulse Teacher Android App — Modern 3D Glass Effect UI

## Your Task

Rebuild the **EduPulse Teacher** Android app from scratch using the exact same functionality but with a **modern 3D glassmorphism UI** design language. The current app uses a flat cyberpunk theme with pure black background (#000000), #111111 cards with cyan (#00E5FF) borders, and 12dp rounded corners. You will upgrade this to a premium 3D glass effect UI while keeping every feature identical.

---

## 1. APP OVERVIEW

EduPulse Teacher is a single-activity Android app for teachers to take QR-code-based attendance. The flow:

1. **Splash Screen** (2 seconds) → checks auth state
2. **Login Screen** → Firebase email/password or Google Sign-In
3. **Connect Screen** → enter server IP:port, test connection via `/api/health`
4. **Main Screen** → select Class/Section/Subject, scan student QR codes, push attendance
5. **Biometric Lock** → optional fingerprint unlock on future launches
6. **OTA Updates** → checks GitHub releases, downloads APK, installs via FileProvider
7. **Loading/Success/Update Overlays** → modal dialogs over the main content

---

## 2. FUNCTIONAL REQUIREMENTS (Port these EXACTLY)

### Authentication
- Firebase email/password via REST API at `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=AIzaSyDPYjylSBhng6CRL77P0MXTUcuq7jBFnnA`
- Google Sign-In via `play-services-auth:20.7.0`, exchange ID token with Firebase at `https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=...`
- Persist login state in SharedPreferences (`logged_in`, `email`, `id_token`, `api_ip`, `api_port`)

### Server Connection
- User enters IP and port (default 3000)
- Test with `GET {apiBaseUrl}/api/health`
- Save to SharedPreferences

### Main Screen
- Load subjects from `GET {apiBaseUrl}/api/subjects` (JSON: `{"success": true, "data": {"Class 10": ["Math","Science"], ...}}`)
- Class/Subject/Selection spinners
- Prefetch students via `GET {apiBaseUrl}/api/students?class={cls}` when class selected
- Student data cached in `Map<String, Map<String, String>> studentsCache` keyed by student ID

### QR Scanning
- Uses `com.journeyapps:zxing-android-embedded:4.3.0` (IntentIntegrator)
- QR data is JSON with `student_id` field
- Check cache first, then `GET {apiBaseUrl}/api/students/{studentId}`
- Student must match selected class
- Photo fallback: `GET https://edupulse-attendance-qr-default-rtdb.asia-southeast1.firebasedatabase.app/students/{studentId}.json`
- Track scanned IDs in `List<String> scannedStudentIds` to prevent duplicates
- Play `scan_beep.wav` on each scan

### Attendance Push
- POST each student to `{apiBaseUrl}/api/attendance/scan`
- Body: `{"student_id", "class", "section", "subject"}`
- Recursive push with per-student status
- Show success overlay with confetti animation when done

### OTA Update
- Background thread: `GET https://api.github.com/repos/ShadowMan2010/edupulse-teacher/releases/latest`
- Compare `tag_name` version code against current `versionCode`
- If newer: show update overlay with changelog, download APK, install via FileProvider
- FileProvider paths: `cache-path` (`updates/`) + `external-cache-path` (`updates/`)

### Biometric Lock
- `androidx.biometric:biometric:1.1.0`
- `BiometricPrompt` with `setNegativeButtonText("Cancel")`
- Only on devices with biometric hardware (`BiometricManager.canAuthenticate()`)
- Prompts user to enable after successful login

### Sound Effects
- `scan_beep.wav` → played on QR scan
- `scan_success.wav` → played on successful push

### Data Structures
```java
List<String> scannedStudentIds = new ArrayList<>();
List<Map<String, String>> scannedStudents = new ArrayList<>();  // keys: id, name, class, section, photo, status
List<String> classList = new ArrayList<>();
Map<String, List<String>> subjectsMap = new HashMap<>();
Map<String, Map<String, String>> studentsCache = new HashMap<>();
List<String> currentSubjects = new ArrayList<>();
static final List<String> SECTIONS = Arrays.asList("A", "B", "C", "D", "E");
```

### SharedPreferences Keys
```
"logged_in" (boolean), "email" (String), "api_ip" (String), "api_port" (String),
"id_token" (String), "ota_url" (String), "pending_apk" (String), "biometric_enabled" (boolean)
```

### RecyclerView Item
Each scanned student shows: photo (40dp circle/rounded), name (14sp), class·section detail (10sp monospace), status badge (pill, color-coded), remove button (red X). Inflated from a layout with:
- `R.id.studentPhoto` (ImageView)
- `R.id.studentName` (TextView)
- `R.id.studentDetail` (TextView)
- `R.id.statusBadge` (TextView)
- `R.id.removeButton` (TextView)

Status badge colors:
- "late" → text #f59e0b, bg #2d1f00
- "early" → text #00E5FF, bg #003333
- "present" → text #00FF88, bg #0a2e1a
- default → text #9E9E9E, bg #1F1F1F

---

## 3. TECHNICAL REQUIREMENTS

### Build Configuration
```groovy
// Project-level
classpath 'com.android.tools.build:gradle:8.2.0'

// App-level
android {
    namespace 'com.edupulse.teacher'
    compileSdk 34
    defaultConfig {
        applicationId 'com.edupulse.teacher'
        minSdk 24
        targetSdk 34
        versionCode 5
        versionName '5.0.0'
    }
    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'com.android.volley:volley:1.2.1'
    implementation 'com.journeyapps:zxing-android-embedded:4.3.0'
    implementation 'com.google.zxing:core:3.5.2'
    implementation 'com.google.android.gms:play-services-auth:20.7.0'
    implementation 'androidx.biometric:biometric:1.1.0'
}
```

### Permissions (in AndroidManifest.xml)
```xml
INTERNET, ACCESS_NETWORK_STATE, CAMERA, VIBRATE,
REQUEST_INSTALL_PACKAGES, WRITE_EXTERNAL_STORAGE (maxSdkVersion=28),
USE_BIOMETRIC
```
Features: `android.hardware.camera` (required), `android.hardware.camera.autofocus` (not required).

Also include:
- FileProvider with authorities `${applicationId}.fileprovider`, paths from `@xml/file_paths`
- Network security config with cleartext permitted
- Screen orientation locked to portrait
- AdjustResize soft input mode

### Architecture
- Single Activity (extends AppCompatActivity)
- All screens managed via View visibility (GONE/VISIBLE) within a single layout
- All HTTP via Volley RequestQueue
- All persistence via SharedPreferences ("edupulse_teacher")
- Layout: single FrameLayout root containing all screen containers stacked as siblings

### 6 Custom Views to Implement
You MUST create these as View subclasses (draw them programmatically with Canvas/Paint + ValueAnimator):

1. **MatrixRainView** — Matrix-style digital rain (katakana + alphanumeric chars falling in columns) + CRT scanline overlay (white lines every 3dp at 4% alpha). Characters: `アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ{}[]<>/\\|!@#$%^&*()`

2. **DigitalLoader** — Loading animation with: pulsing cyan ring (scale 0.9→1.1), rotating arc (0→360), inner progress arc, center cycling character, orbiting mini-chars. Plus CRT scanlines.

3. **GlitchTextView** — Text that periodically glitches: random slice offset + RGB shift cyan/magenta ghost copies + character corruption at 92-100%, 30-35%, and 70-73% of a 2500ms cycle. Default text "EDUPULSE".

4. **CyberBgView** — Animated background with: gradient cycling through 4 color pairs (Cyan→Purple→Magenta→Blue→Cyan), 40 floating particles, 8 rotating hexagons, grid lines every 40px.

5. **ScanPulseView** — Pulsing ring around scan button: expanding ring from 0→180px with alpha 1→0, 3s cycle, 1.5s initial delay.

6. **ConfettiView** — 60 colored confetti pieces burst from center with gravity (400px/s²), 2s lifetime, rotation, fade out.

---

## 4. UI / LAYOUT STRUCTURE

All screens are direct children of a root FrameLayout. Use `android:keepScreenOn="true"` on the root.

### Splash Screen ID: `splashContainer` (FrameLayout)
- Full screen, 2 second delay before routing
- Contains: MatrixRainView (alpha 0.25) + centered column: logo (120dp), GlitchTextView "EDUPULSE", TextView "Teacher", DigitalLoader (48dp)

### Login Screen ID: `loginContainer` (FrameLayout)
- Contains: MatrixRainView (alpha 0.2) + ScrollView
- Content: logo (80dp), GlitchTextView "EDUPULSE", TextView "Teacher Sign In", card with email/password fields (TextInputLayout OutlinedBox), SIGN IN button (full-width, 52dp height), Google SignInButton, login error text

### Connect Screen ID: `connectContainer` (ScrollView)
- Content: logo (96dp), "Connect to Server" title, IP input (monospace), Port input (default "3000", monospace), CONNECT button, connection status text

### Main Screen ID: `mainContainer` (LinearLayout, vertical)
- **Top bar**: logo + "EduPulse" + user email + Exit button (red)
- **Stat bar**: "SCANNED STUDENTS" label + count (large monospace) + class/subject info
- **"CLASS & SUBJECT" section** with spinner row: CLASS spinner | SEC spinner + SUBJECT spinner
- **Scan button area**: FrameLayout(200dp) wrapping ScanPulseView + oval scan button (200dp, "SCAN\nQR")
- **Last scanned card**: hidden until scan
- **SCAN FINISHED button**: outline style, hidden until scan

### Loading Overlay ID: `loadingOverlay` (FrameLayout)
- Semi-transparent background (#CC000000), clickable
- Center card: DigitalLoader + "Connecting..." + subtext

### Summary Sheet ID: `scanSummarySheet` (LinearLayout, bottom-aligned, 300dp height)
- "SCAN SUMMARY" header + count
- RecyclerView for student list
- Row: ADD MORE + PUSH TO SERVER buttons
- Visibility: GONE by default, shown on first scan or when "ADD MORE" pressed

### Success Overlay ID: `successOverlay` (FrameLayout)
- ConfettiView (full screen, renders confetti particles)
- Center card: checkmark in gradient circle + "Attendance Pushed!" + subtext + DONE button

### Biometric Lock ID: `biometricOverlay` (FrameLayout)
- 90% black background
- Center card: lock icon + "FINGERPRINT LOCK" + status text + USE PASSWORD button

### Update Overlay ID: `updateOverlay` (FrameLayout)
- Center card: download icon + "Update Available" + version + changelog + progress bar + UPDATE NOW + LATER buttons

---

## 5. NEW 3D GLASS EFFECT DESIGN LANGUAGE

This is the most important section. The UI must look like **premium 3D dark glass** — think iOS 18/macOS Sonoma glass, not flat glassmorphism.

### Color Palette
```
Background:           #000000 (pure black, no exceptions)
Glass card fill:      rgba(255, 255, 255, 0.03) to rgba(255, 255, 255, 0.06)
Glass card border:    rgba(255, 255, 255, 0.08), 1dp
Glass card corner:    16dp (rounded)
Glass highlight:      rgba(255, 255, 255, 0.05) top edge shine
Glass shadow:         rgba(0, 0, 0, 0.6), 20dp blur, 10dp Y offset

Accent:               #00E5FF (neon cyan)
Accent glow:          #00E5FF at 15-20% alpha (radial, 40dp radius)
Accent dim:           #0097A7

Secondary accent:     #BB00FF (neon purple)
Success:              #00FF88
Error:                #FF3B3B

Text primary:         #FFFFFF (or rgba(255,255,255,0.92))
Text secondary:       rgba(255, 255, 255, 0.55)
Label text:           rgba(255, 255, 255, 0.4), monospace, 10sp, letterSpacing 0.15

Divider:              rgba(255, 255, 255, 0.06)
Surface (elevated):   rgba(255, 255, 255, 0.04)
```

### Glass Cards (replaces bg_card.xml)
Each card should look like a **piece of dark frosted glass floating in 3D space**:
- Semi-transparent fill (not solid #111111)
- Subtle backdrop blur effect (if API 31+ use `setRenderEffect`; otherwise simulate with gradient)
- Thin semi-transparent white border (not colored cyan border)
- 16dp corner radius (not 12dp — larger radius looks more premium)
- A **subtle top-edge highlight** — a very thin (0.5dp) white-to-transparent gradient line at the top of the card simulating light hitting the glass edge
- Realistic elevation shadow: dark, wide, offset downward
- Optional: a subtle diagonal gradient "sheen" across the card that creates a glass reflection effect (the "shine" line)
- Internal padding: 16dp

### Glass Stat Card (replaces bg_stat_card.xml)
Same glass card but with a **thinner accent bar** at top (1.5dp, accent color with glow, 16dp radius top corners)

### Input Fields (TextInputLayout OutlinedBox)
- Background: rgba(255, 255, 255, 0.04) — very subtle glass effect
- Border: rgba(255, 255, 255, 0.08), 1dp
- Focused border: #00E5FF with slight glow
- Text: white
- Hint: rgba(255, 255, 255, 0.4)
- Corner radius: 12dp
- Font: system monospace

### Primary Buttons
- Full-width, 52dp height
- Background: #00E5FF (solid accent) — NO gradients
- Text: #000000, ALL CAPS, monospace, ExtraBold, 14sp, letterSpacing 0.12
- Corner radius: 14dp
- **3D effect**: subtle inner shadow at top, slight outer shadow at bottom, 1px bright top edge
- Disabled: rgba(255, 255, 255, 0.06) fill, rgba(255, 255, 255, 0.3) text

### Outlined Buttons
- 50dp height
- Border: 1dp, rgba(0, 229, 255, 0.5)
- Text: #00E5FF, monospace, 13sp, letterSpacing 0.08
- Corner radius: 12dp

### Scan Button (oval)
- 200dp × 200dp oval
- Background: #00E5FF solid
- Text: #000000, "SCAN\nQR", 20sp, monospace bold in center
- Surrounded by a pulsing glass ripple ring (ScanPulseView)
- The ripple ring should have a glass-like translucent quality

### Text Styles
```
Section headers:  monospace, 10sp, letterSpacing 0.15, rgba(255,255,255,0.4) — uppercase
Body text:        sans-serif, 13sp, #FFFFFF (92% alpha)
Secondary text:   sans-serif, 12sp, rgba(255,255,255,0.55)
Labels:           monospace, 10sp, rgba(255,255,255,0.4)
Stat values:      monospace, 36sp, bold, #00E5FF
Button text:      monospace, 14sp, extra bold, letterSpacing 0.12, ALL CAPS
```

### Spinners
Same glass style: rgba(255,255,255,0.04) background, rgba(255,255,255,0.08) border, 12dp radius, white text. Dropdown background: glass card with blur.

### 3D Depth Effects
Cards should have **perceptible layering**:
- Base surface: depth layer 0
- Card: depth layer 1 (elevation shadow, slight z-translation)
- Modals/overlays: depth layer 2 (stronger shadow, larger z)
- Maximum depth: update/biometric overlays at layer 3

Each layer should have progressively larger shadow radius and offset.

### Glass Reflection Animation
When the login screen appears or the main screen loads, cards should animate in with a **glass sheen sweep** — a diagonal gradient highlight that moves across the card surface once (like light reflecting off glass as it moves into position). Duration: 600ms, delay: staggered by card index.

### ScanLine Overlay
CRT scan lines: horizontal white lines, 4dp spacing, 1px width, 4% alpha. Applied as a final overlay on all full-screen views (not on cards/modals).

### Background
The CyberBgView animated gradient background plays underneath the glass cards, making the glass translucency visible. The MatrixRainView is used on splash/login screens as an additional layer between the background and the glass cards.

### Glass Card Implementation Approach

Since Android XML drawables cannot render semi-transparent blur or glass highlights, implement glass cards as:

**Option A (if API 31+):**
Use `customView.setRenderEffect(RenderEffect.createBlurEffect(...))` on a semi-transparent background view, then overlay content on top. This provides real-time frosted glass.

**Option B (simulation for all API levels):**
Create each glass card using a **custom `GlassCardView`** that extends any layout (FrameLayout, LinearLayout) and draws:
1. Semi-transparent fill (`Paint` with `Color.argb(8, 255, 255, 255)`)
2. Thin white border (`Color.argb(20, 255, 255, 255)`, 1dp)
3. Top highlight line (`Color.argb(12, 255, 255, 255)`, 0.5dp, gradient fade at ends)
4. Optional diagonal sheen (a linear gradient across the card, alpha 3-5%, rotated 45deg)
5. Drop shadow (dark, wide, offset down)

**Option C (hybrid, recommended):**
Use XML drawables with careful layering to simulate glass:
```xml
<!-- Glass card drawable simulation -->
<layer-list>
    <!-- Shadow layer -->
    <item android:left="4dp" android:top="8dp" android:right="4dp" android:bottom="2dp">
        <shape>
            <solid android:color="#40000000" />
            <corners android:radius="16dp" />
        </shape>
    </item>
    <!-- Glass fill + border -->
    <item>
        <shape>
            <solid android:color="#0DFFFFFF" />  <!-- ~5% white -->
            <stroke android:width="1dp" android:color="#14FFFFFF" />  <!-- ~8% white -->
            <corners android:radius="16dp" />
        </shape>
    </item>
    <!-- Top highlight -->
    <item android:top="0dp" android:bottom="calc(100%-0.5dp)" android:left="4dp" android:right="4dp">
        <shape>
            <gradient
                android:startColor="#14FFFFFF"
                android:endColor="#00FFFFFF"
                android:type="linear"
                android:angle="0" />
            <corners android:topLeftRadius="16dp" android:topRightRadius="16dp" />
        </shape>
    </item>
</layer-list>
```

Then add the diagonal sheen animation programmatically on window focus.

---

## 6. SCREEN TRANSITIONS & ANIMATIONS

### Screen Transitions
- Show/hide screens with alpha fade (250ms) + slight scale (0.97→1.0)
- Handler.postDelayed for splash delay (2000ms)

### Glass Card Entrance
- Cards translate up 20dp + fade in (400ms, DecelerateInterpolator)
- Staggered: 80ms delay between each card
- Plus glass sheen sweep animation on the first card

### Loading Overlay
- Fade in/out over 200ms
- DigitalLoader spinning continuously

### Success Overlay
- Success icon pop-in (scale 0→1.2→1.0, 600ms)
- Confetti burst (60 particles, 2s)

### Biometric
- Fade in overlay (300ms)
- On success: scale exit animation

### Update Overlay
- Slide up from bottom (300ms)
- Progress bar animated

### Scan Button Pulse
- Infinite scale pulse: 1.0→1.05→1.0, 1500ms, DecelerateInterpolator
- ScanPulseView ring expanding behind button

---

## 7. COMPLETE FILE STRUCTURE

```
android/
├── build.gradle (project-level)
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/edupulse/teacher/
│       │   ├── MainActivity.java
│       │   ├── MatrixRainView.java
│       │   ├── DigitalLoader.java
│       │   ├── GlitchTextView.java
│       │   ├── CyberBgView.java
│       │   ├── ScanPulseView.java
│       │   ├── ConfettiView.java
│       │   └── GlassCardView.java (NEW — custom glass card container)
│       ├── res/
│       │   ├── drawable/
│       │   │   ├── logo.png (generate or use simple vector)
│       │   │   ├── bg_card_glass.xml (NEW — glass card shape)
│       │   │   ├── bg_stat_card_glass.xml (NEW — glass stat card)
│       │   │   ├── bg_field_glass.xml (NEW — glass input field)
│       │   │   ├── bg_success_circle.xml
│       │   │   ├── bg_badge_pill.xml
│       │   │   ├── btn_gradient.xml (#00E5FF solid button)
│       │   │   └── btn_gradient_scan.xml (#00E5FF solid oval)
│       │   ├── layout/
│       │   │   ├── activity_main.xml
│       │   │   ├── item_student_scan.xml
│       │   │   ├── spinner_item.xml
│       │   │   └── spinner_dropdown.xml
│       │   ├── values/
│       │   │   ├── strings.xml
│       │   │   └── themes.xml
│       │   ├── raw/
│       │   │   ├── scan_beep.wav
│       │   │   └── scan_success.wav
│       │   └── xml/
│       │       ├── file_paths.xml
│       │       └── network_security_config.xml
│       └── res/font/ (OPTIONAL — can use system fonts instead)
├── version.json
└── AGENTS.md
```

---

## 8. IMPLEMENTATION NOTES

### GlassCardView
Create a custom container view (extends FrameLayout) that:
- In `onDraw`, renders the glass card visual: semi-transparent fill, border, top highlight, shadow
- Has a `startSheenAnimation()` method that draws a diagonal gradient highlight sweeping across the card (left-to-right, 600ms)
- Uses an `ObjectAnimator` for the sheen position
- Children added to it will sit on top of the glass background
- Supports elevation-based shadow intensity (setElevation maps to shadow params)

### Layout Changes
Replace ALL `bg_card` backgrounds in activity_main.xml with the new glass card drawable or GlassCardView wrapper. Change card corner radii from 12dp to 16dp. Adjust padding to 16dp.

### Theme Updates
```xml
<style name="Theme.EduPulse" parent="Theme.MaterialComponents.DayNight.NoActionBar">
    <item name="android:colorBackground">#000000</item>
    <item name="colorSurface">#0DFFFFFF</item>  <!-- glass surface -->
    <!-- etc -->
</style>
```

### Keep These Exactly the Same
- All Java logic in MainActivity (auth, API calls, QR scanning, OTA, biometric)
- All custom views (MatrixRainView, DigitalLoader, GlitchTextView, CyberBgView, ScanPulseView, ConfettiView)
- All sound effects (scan_beep.wav, scan_success.wav)
- All API endpoints and network logic
- SharedPreferences keys and data structures
- ProGuard rules
- AndroidManifest.xml permissions and components
- build.gradle dependencies and versions

### Change These to Glass Style
- All card backgrounds → glass effect (semi-transparent with blur simulation, white-ish borders, top highlights, shadows)
- Button backgrounds → keep flat #00E5FF but add subtle 3D depth effects (inner shadow, bottom shadow, top edge light)
- Input field backgrounds → glass style
- Stat cards → glass style with cyan accent bar
- Corner radii: 12dp → 16dp (cards), 10dp → 12dp (fields), 12dp → 14dp (buttons)
- Card internal padding: 16dp
- Screen horizontal margins: 20dp
- Text colors: maintain the same hierarchy (#FFFFFF primary, rgba(255,255,255,0.55) secondary, rgba(255,255,255,0.4) labels)
- Add subtle entrance animations to cards (translate + fade + sheen)
- Spinner backgrounds → glass style
- Modal overlays (loading, success, biometric, update) → glass cards with frosted effect

---

## 9. DELIVERABLES

Produce a complete, buildable Android project with:

1. All Gradle build files
2. Full AndroidManifest.xml
3. All Java source files (MainActivity + 6 custom views + optional GlassCardView)
4. All layout XML files
5. All drawable XML files (updated to glass style)
6. All resource files (strings.xml, themes.xml, file_paths.xml, network_security_config.xml)
7. ProGuard rules
8. version.json
9. A brief README explaining the glass design system

The app must:
- Compile and run on Android 7.0+ (API 24)
- Target SDK 34
- Build a release APK via `./gradlew assembleRelease`
- Work with the existing backend at the configured server IP
- Match the glass design language described above

Start by creating the complete project structure, then implement MainActivity with all the logic, then the custom views, then the layouts/drawables with the glass design system.
