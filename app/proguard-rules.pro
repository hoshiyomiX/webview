# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve debug utilities di debug builds
-keepclassmembers class com.deviant.batterymonitor.SELinuxDebugger {
    public *;
}

-keepclassmembers class com.deviant.batterymonitor.SysfsAccessLogger {
    public *;
}

# Preserve logging methods
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep line numbers untuk crash reports
-keepattributes SourceFile,LineNumberTable

# Rename source file attribute untuk obfuscation
-renamesourcefileattribute SourceFile

# WebView related
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}

-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, java.lang.String);
}

# JavaScript Interface (jika ada)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve custom exceptions
-keep public class * extends java.lang.Exception
