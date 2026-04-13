# R8 / ProGuard rules for Premium TV Player.
# Defaults from `proguard-android-optimize.txt` are inherited via build.gradle.kts.

# Keep Compose runtime entry points (also covered by Compose Compiler plugin).
-keep class androidx.compose.runtime.** { *; }

# Keep generated Hilt + ViewModel components.
-keep class dagger.hilt.** { *; }
-keep class * extends androidx.lifecycle.ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Kotlinx serialization — keep companion object serializers.
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp / Retrofit — keep generic signatures so reflection-based
# converters still work after R8.
-keepattributes Signature, *Annotation*, EnclosingMethod
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
