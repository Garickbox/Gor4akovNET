# Gor4akovNET — R8 keep-правила
# Задача: сжать APK без потери функционала.

# --- Go-ядро (gomobile bind → AAR, JNI + reflection) ---
-keep class gboxcore.** { *; }
-keepclassmembers class gboxcore.** { *; }
-dontwarn gboxcore.**

# --- Zxing (QR-сканер, может грузить декодеры рефлексией) ---
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --- Kotlin metadata / reflection ---
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, SourceFile, LineNumberTable
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }

# --- Coroutines (Compose + lifecycle) ---
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# --- Compose: сам плагин держит @Composable, но оставим безопасность ---
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# --- AndroidX core / lifecycle ---
-keep class androidx.core.** { *; }
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.**

# --- BuildConfig (envQuoted поля) ---
-keep class ru.garickbox.gor4akovnet.BuildConfig { *; }

# --- Наши классы: если где-то есть optJSONObject/parcelable ---
-keep class ru.garickbox.gor4akovnet.** { *; }
-keepclassmembers class ru.garickbox.gor4akovnet.** {
    public <init>(...);
    public *;
}

# --- Reflection-безопасность общая ---
-keepattributes EnclosingMethod
-keepclassmembers enum * { *; }

# --- Отключаем шумные warnings ---
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn **.R$*