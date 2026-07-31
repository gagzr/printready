# Default ProGuard rules for release builds
-keep class com.printready.app.** { *; }
-keepclassmembers class * implements android.os.Parcelable { *; }
