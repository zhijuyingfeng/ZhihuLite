# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep source/line info so release stack traces are readable.
-keepattributes SourceFile,LineNumberTable

# kotlinx.serialization: keep generated serializers and their descriptors.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class org.nigao.zhihuLite.**$$serializer { *; }
-keepclasseswithmembers class org.nigao.zhihuLite.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# multiplatform-settings no-arg relies on an androidx.startup Initializer that is
# referenced from the merged manifest metadata; keep all initializers.
-keep class * extends androidx.startup.Initializer { *; }

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
