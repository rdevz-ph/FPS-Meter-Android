# Aggressive Code & Resource Shrinking Rules for FPS Meter

# Repackage all non-kept classes into root package to shrink DEX string pool
-repackageclasses ''
-allowaccessmodification

# Preserve line numbers and source file for accurate crash reporting in CrashActivity
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve standard annotations and generic signatures for Compose and Coroutines
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Shizuku API & Provider (Binder interfaces, AIDL, and reflection)
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-keep class dev.rikka.shizuku.** { *; }
-keep interface dev.rikka.shizuku.** { *; }
-dontwarn dev.rikka.shizuku.**

# Android Components & Background Services
-keep class com.rdevzph.fpsmeter.service.** { *; }
-keep class com.rdevzph.fpsmeter.overlay.** { *; }
-keep class com.rdevzph.fpsmeter.accessibility.** { *; }
-keep class com.rdevzph.fpsmeter.crash.** { *; }

# Models & Settings
-keep class com.rdevzph.fpsmeter.model.** { *; }
-keepclassmembers class com.rdevzph.fpsmeter.viewmodel.OverlaySettings** { *; }

# AndroidX Lifecycle & ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Compose
-dontwarn androidx.compose.**