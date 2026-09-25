# Firestore deserializes models via reflection using field names — keep them intact.
-keepclassmembers class com.teo.core.model.** {
  <fields>;
  <init>();
}
-keep class com.teo.core.model.** { *; }

# Room entities are also reflected into by the generated DAO implementations.
-keep class com.teo.child.data.local.** { *; }

# WebRTC (listen-session audio) makes heavy use of JNI — keep the whole package intact.
-keep class org.webrtc.** { *; }
