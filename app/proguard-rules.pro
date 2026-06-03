# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Future-minify guard rules. These are inert while isMinifyEnabled = false in
# build.gradle.kts, but stay in place so flipping minify on does not regress
# kotlinx-serialization reflection or MapLibre's native/reflective surface.
# ---------------------------------------------------------------------------

# kotlinx-serialization: standard keep rules. R8 strips the synthetic
# serializer companions and @Serializable metadata without these.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the @Serializer/@Serializable-generated serializer companions.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}

# Keep the serializer() accessor and named-companion serializers.
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}

# MapLibre: relies on native (JNI) and reflective access; keep its classes and
# silence warnings for missing optional references.
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**