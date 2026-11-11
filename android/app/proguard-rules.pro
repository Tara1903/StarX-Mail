# StarX Mail Production ProGuard / R8 Rules

# Jetpack Compose
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }

# Hilt & Dagger
-keep class * extends dagger.hilt.internal.GeneratedComponentManager { *; }
-keep class * extends dagger.hilt.internal.ComponentManager { *; }
-keepnames class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class app.starx.mail.data.local.entity.** { *; }
-keep class app.starx.mail.data.local.dao.** { *; }

# Retrofit & Gson Models
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}
-keep class app.starx.mail.data.remote.dto.** { *; }
-keep class app.starx.mail.core.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn sun.misc.**
-dontwarn okio.**
-dontwarn retrofit2.**

# WorkManager
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.** { *; }
