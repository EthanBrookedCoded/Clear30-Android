# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep,includedescriptorclasses class org.clear30.**$$serializer { *; }
-keepclassmembers class org.clear30.** {
    *** Companion;
}
-keepclasseswithmembers class org.clear30.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The Helium SDK's ktor HttpClient references ktor plugin classes that aren't on
# the compile classpath, which R8 reports as errors. Helium is inert unless
# HELIUM_API_KEY is set (its client is never built), so these paths don't run.
# NOTE: when Helium is turned on, add the ktor content-negotiation + logging
# artifacts so its HttpClient actually works at runtime.
-dontwarn io.ktor.**
-dontwarn com.tryhelium.**
