# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.fatlosstrack.data.local.db.** { *; }

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**
-keep class io.ktor.** { *; }

# Kotlinx Serialization
-keepattributes InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.fatlosstrack.**$$serializer { *; }

# Google Play services OSS Licenses
-keep class com.google.android.gms.oss.licenses.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }

# Health Connect
-keep class androidx.health.connect.** { *; }
