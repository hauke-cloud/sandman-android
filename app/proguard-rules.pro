# kotlinx.serialization keeps the generated serializers on the classes it
# annotates; R8 cannot see they are used, so they are kept explicitly.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
  *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
  kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class cloud.hauke.sandman.**$$serializer { *; }
-keepclassmembers class cloud.hauke.sandman.** {
  *** Companion;
}
-keepclasseswithmembers class cloud.hauke.sandman.** {
  kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp ships optional hooks for platforms that are not Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
