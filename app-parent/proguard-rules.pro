# Firestore deserializes models via reflection using field names — keep them intact.
-keepclassmembers class com.teo.core.model.** {
  <fields>;
  <init>();
}
-keep class com.teo.core.model.** { *; }

# WebRTC (listen-session audio) makes heavy use of JNI — keep the whole package intact.
-keep class org.webrtc.** { *; }
