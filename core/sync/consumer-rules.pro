# Merged into app release minification.
-keep class com.google.android.gms.wearable.** { *; }
-dontwarn com.google.android.gms.**
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses
-keep,includedescriptorclasses class com.lockerlift.**$$serializer { *; }
-keepclassmembers class com.lockerlift.** {
    *** Companion;
}
