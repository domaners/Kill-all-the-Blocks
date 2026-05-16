# Standard Android and Firebase keep rules to prevent false positives in heuristic scanners.

# Keep standard Activity and Application classes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep Firebase and Google Play Services classes to avoid broken integration
# which can sometimes look like suspicious missing code.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep our own data classes that are used for Firebase serialization
-keep class com.example.killalltheblocks.ScoreEntry { *; }
-keep class com.example.killalltheblocks.ScoreStore { *; }
-keep class com.example.killalltheblocks.GameSettingsStore { *; }
