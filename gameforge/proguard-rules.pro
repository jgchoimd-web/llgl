# Release builds are not minified (see build.gradle.kts); kept so the proguardFiles reference resolves.
# MediaPipe's native bridge looks classes up by name, so keep them if minification is ever enabled.
-keep class com.google.mediapipe.** { *; }
