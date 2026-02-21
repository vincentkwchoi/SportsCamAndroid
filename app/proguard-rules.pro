# Add project specific ProGuard rules here.

# Keep LiteRT / TensorFlow Lite classes to prevent them from being stripped or renamed
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.lite.** { *; }

# MediaPipe rules
-keep class com.google.mediapipe.** { *; }

# Keep CameraX internal classes
-keep class androidx.camera.** { *; }

# FIX: Missing classes in LiteRT Support library
# The support library references legacy TF Lite classes that aren't in LiteRT 2.x
-dontwarn org.tensorflow.lite.Delegate
-dontwarn org.tensorflow.lite.TfLiteDelegate
-dontwarn org.tensorflow.lite.Interpreter
-dontwarn org.tensorflow.lite.gpu.GpuDelegate
-dontwarn org.tensorflow.lite.support.**

# Suppress warnings from other common libraries if necessary
-dontwarn sun.misc.Unsafe
-dontwarn javax.annotation.**
