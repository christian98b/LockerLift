# Merged into app release minification. connect-client also ships its own rules.
-keep class androidx.health.connect.client.** { *; }
-dontwarn androidx.health.connect.**
-dontwarn androidx.health.platform.client.**
