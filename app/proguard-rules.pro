# Keep JNI entry points — they are called from C++ by name.
-keep class com.haramain.monitor.audio.NativeAudioEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
