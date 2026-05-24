# FileBridge R8 / ProGuard rules.

# Uncomment to preserve line numbers for debugging release stack traces.
#-keepattributes SourceFile,LineNumberTable
#-renamesourcefileattribute SourceFile

# --- Apache FTPServer (reflection-heavy: command factory, ftplets, MINA) ---
-keep class org.apache.ftpserver.** { *; }
-keep class org.apache.mina.** { *; }
-dontwarn org.apache.ftpserver.**
-dontwarn org.apache.mina.**
-dontwarn org.slf4j.**

# --- BouncyCastle (provider loaded reflectively via Security.insertProviderAt) ---
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# --- Hilt / Dagger generated code ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**

# --- Kotlin coroutines ---
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# --- AndroidX Glance widget ---
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**

# --- ZXing QR code library ---
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --- DataStore ---
-keep class androidx.datastore.** { *; }

# --- Kotlin serialization (if added in future) ---
-keepattributes *Annotation*, InnerClasses
-keepattributes Signature, Exceptions
