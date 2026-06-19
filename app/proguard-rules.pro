# ProGuard / R8 rules for MusicPlayer
# Keep the app entry point
-keep class com.codewhale.musicplayer.** { *; }

# Keep ID3 parser (uses reflection-free pure Java, but prevent aggressive stripping)
-keep class com.codewhale.musicplayer.id3.** { *; }

# Keep model classes used in serialization
-keep class com.codewhale.musicplayer.model.** { *; }

# AndroidX / AppCompat
-keep class androidx.appcompat.** { *; }
-dontwarn androidx.appcompat.**

# Material
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
