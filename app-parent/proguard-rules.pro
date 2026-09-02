# Firestore deserializes models via reflection using field names — keep them intact.
-keepclassmembers class com.teo.core.model.** {
  <fields>;
  <init>();
}
-keep class com.teo.core.model.** { *; }
