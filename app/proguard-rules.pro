# Règles ProGuard pour Taqnin ID Batch

# Conserver les modèles Room
-keep class com.taqnid.batch.data.local.entity.** { *; }
-keep class com.taqnid.batch.data.model.** { *; }

# Conserver les DAOs Room
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# ZXing
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

# iTextG
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# CameraX
-keep class androidx.camera.** { *; }

# Biométrie
-keep class androidx.biometric.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { *; }

# ViewBinding / DataBinding
-keep class **.databinding.** { *; }
-keep class **.BR { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Logs de débogage en release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
