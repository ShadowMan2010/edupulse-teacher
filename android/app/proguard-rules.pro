-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

-keep class com.edupulse.teacher.** { *; }

-keep class * extends androidx.appcompat.app.AppCompatActivity { *; }

-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

-keep class com.google.android.gms.** { *; }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.barcodescanner.**
-dontwarn com.google.android.gms.**

-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
