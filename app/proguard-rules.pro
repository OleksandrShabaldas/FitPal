# Most of AndroidX/Google's own libraries (Room, Hilt, WorkManager, CameraX, Health Connect)
# ship their OWN consumer-rules.pro bundled in the AAR, which AGP merges in automatically — the
# rules below are only for things that don't, or as cheap extra insurance on the reflection-heavy
# ones the app actually depends on for its core features.

# TensorFlow Lite (used by MediaPipe's on-device inference)
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# MediaPipe — the on-device Gemma model engine. This is load-bearing (the app's offline AI
# fallback), so it gets an explicit keep rather than trusting inference alone that its bundled
# consumer rules cover everything the genai/vision task graph loads reflectively.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
# Custom @EntryPoint interfaces (FitPalApplication, ReminderReceiver, the Wear workers) are
# resolved via EntryPointAccessors.fromApplication(context, X::class.java) at runtime — keep the
# interface + its methods so R8 can't rename/strip what Hilt's generated component implements.
-keep @dagger.hilt.EntryPoint interface *
