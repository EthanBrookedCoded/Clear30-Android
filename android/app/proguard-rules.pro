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
