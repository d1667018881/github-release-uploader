# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Gson
-keep class com.github.releaseuploader.data.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
