# Gor4akovNET — R8 keep-правила (оптимизация размера APK без поломки JNI/Compose)

# --- Go / gomobile runtime + gboxcore (обязательно для JNI) ---
-keep class go.** { *; }
-keepclassmembers class go.** { *; }
-keep class gboxcore.** { *; }
-keepclassmembers class gboxcore.** { *; }
-dontwarn go.**
-dontwarn gboxcore.**

# --- Zxing (QR-генератор для Wi-Fi QR) ---
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --- Kotlin / Coroutines ---
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, SourceFile, LineNumberTable, EnclosingMethod
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# --- Compose ---
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**
-dontwarn androidx.**

# --- BuildConfig и enum-ы приложения ---
-keep class ru.garickbox.gor4akovnet.BuildConfig { *; }
-keepclassmembers enum * { *; }

# --- Отключаем шумные warnings ---
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn **.R$*