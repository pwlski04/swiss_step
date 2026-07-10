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

# --- kotlinx.serialization ---
# Standard recipe (kotlinlang.org/docs/serialization.html#android) for our own
# @Serializable models: keeps generated $$serializer classes and serializer()
# factory methods that R8 can't otherwise see are reachable via reflection.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class io.github.pwlski04.swissstep.**$$serializer { *; }
-keepclassmembers class io.github.pwlski04.swissstep.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.pwlski04.swissstep.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- mapsforge ---
# Rendertheme XML parsing (minmap.xml) resolves tag/handler classes via reflection;
# don't let R8 rename or strip anything it might look up that way.
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**