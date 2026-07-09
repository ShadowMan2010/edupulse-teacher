# Keep all custom view classes (used in XML layouts)
-keep class com.edupulse.teacher.** { *; }

# Keep ZXing (QR scanning library)
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

# Keep Gson/JSON serialization
-keep class org.json.** { *; }

# Keep Volley
-keep class com.android.volley.** { *; }

# Keep Google Sign-In
-keep class com.google.android.gms.** { *; }
