# SafeGap ProGuard rules

# TFLite Task Vision - uses reflection for model loading
-keep class org.tensorflow.** { *; }
-keep class com.google.android.odml.** { *; }
-dontwarn org.tensorflow.lite.**

# Hilt - generated code
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# DataStore preferences
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    *;
}

# Compose runtime (AARs ship their own rules, this is a safety net)
-keep class androidx.compose.runtime.** { *; }

# Keep model classes used by detection pipeline
-keep class com.safegap.core.model.** { *; }
-keep class com.safegap.detection.DetectorConfig { *; }

# Strip debug, verbose, info, and warning logging in release builds
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
    public static int w(...);
}
