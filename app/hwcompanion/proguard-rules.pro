# ML Kit's digital ink recogniser starts itself through a content provider and Firebase-style
# component registrars found by name, and its native engine calls back into Java classes by
# name: renamed or stripped by R8, the app crashes as soon as it starts. Keep them whole (the
# rest of Google's libraries is still shrunk).
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_digital_ink.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keepclasseswithmembers class * { native <methods>; }
-dontwarn com.google.**
