# PatchMaster ProGuard Rules

# Keep the application and main activity
-keep class com.patchmaster.PatchMasterApp { *; }
-keep class com.patchmaster.MainActivity { *; }

# Keep engine classes
-keep class com.patchmaster.engine.** { *; }
-keep class com.patchmaster.model.** { *; }

# Keep agent classes
-keep class com.patchmaster.agent.** { *; }

# Keep data classes for serialization
-keep class com.patchmaster.model.ApkInfo { *; }
-keep class com.patchmaster.model.ModScript { *; }
-keep class com.patchmaster.model.ModAction { *; }
-keep class com.patchmaster.model.ModTemplate { *; }

# Keep Compose
-keep class androidx.compose.** { *; }

# Kotlin serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# General Android
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }

# Don't obfuscate
-dontobfuscate
