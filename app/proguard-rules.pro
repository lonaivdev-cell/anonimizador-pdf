# pdfbox-android
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# MediaPipe Tasks (GenAI / LLM Inference)
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Protobuf (MediaPipe transitive)
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
