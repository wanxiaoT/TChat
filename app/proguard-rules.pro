# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep line numbers useful in release crash reports while still allowing R8 shrinking.
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*

# Room uses generated implementations and schema metadata. Keep database-facing model
# names stable enough for migrations and query verification diagnostics.
-keep class com.tchat.data.database.** { *; }
-keep class * extends androidx.room.RoomDatabase

# JSON payloads and app export/import models are reflected by Gson/org.json paths.
-keep class com.tchat.data.model.** { *; }
-keep class com.tchat.wanxiaot.settings.** { *; }
-keep class com.tchat.wanxiaot.util.ExportDataModelsKt { *; }
-keep class com.tchat.wanxiaot.util.**Json** { *; }

# WebView JavaScript bridge and native ImGui bridge entry points.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# Markwon/JLatexMath and ML Kit use plugin/service discovery and reflective entry points.
-keep class io.noties.markwon.** { *; }
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn ru.noties.jlatexmath.**
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
