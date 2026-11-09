# Shared release keep rules for :mobile and :wear.
# Library modules also ship consumer-rules.pro; these rules are the app-level
# backstop for R8 (Room, SQLCipher, kotlinx.serialization, Wearable Data Layer,
# Health Connect).

# Strip debug and verbose logs in release builds (SEC-09)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# --- SQLCipher (native JNI + Java wrapper) ---
-keep class net.zetetic.** { *; }
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.**

# --- Room ---
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# --- kotlinx.serialization (rules recommended by kotlinx.serialization 1.7) ---
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod,Signature
-dontnote kotlinx.serialization.**

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.lockerlift.**$$serializer { *; }
-keepclassmembers class com.lockerlift.** {
    *** Companion;
}
-keepclasseswithmembers class com.lockerlift.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers enum com.lockerlift.** { *; }

# --- Wearable Data Layer ---
# AGP keeps manifest components; these rules also retain the GMS data-layer
# types the listener services call into.
-keep class com.google.android.gms.wearable.** { *; }
-keep class com.google.android.gms.common.api.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.lockerlift.mobile.service.MobileDataLayerListenerService { *; }
-keep class com.lockerlift.wear.communication.WearDataLayerListenerService { *; }
-keep class com.lockerlift.wear.tracking.WorkoutForegroundService { *; }

# --- Health Connect ---
-keep class androidx.health.connect.client.** { *; }
-dontwarn androidx.health.connect.**
-dontwarn androidx.health.platform.client.**
