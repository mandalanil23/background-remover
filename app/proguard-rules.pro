# =====================================================================
# Background Remover — PNG Maker : R8 / ProGuard configuration
# =====================================================================

# ---- Keep line numbers for readable crash reports, hide source names ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Strip verbose logging from the release binary ----
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ---- Kotlin / Coroutines ----
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-keep class kotlin.Metadata { *; }

# ---- Jetpack Compose ----
# The Compose runtime is R8-friendly out of the box; these rules only guard
# against over-eager stripping of composable lambdas kept via reflection.
-dontwarn androidx.compose.**

# ---- AndroidX / Lifecycle ViewModels (instantiated reflectively) ----
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(...); }

# ---- ML Kit (Google Play services vision) ----
# Model classes and native bridges are looked up by name at runtime.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep interface com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ML Kit declares optional dependencies through manifest metadata; keep the
# annotation-driven registrars used by the Play services module installer.
-keep class com.google.firebase.components.** { *; }
-keepnames class * implements com.google.firebase.components.ComponentRegistrar
-dontwarn com.google.firebase.**

# ---- Coil ----
-dontwarn okio.**
-dontwarn okhttp3.**

# ---- Our own model/state classes used across process death ----
-keep class com.bgremover.pngmaker.data.model.** { *; }

# ---- Parcelables / Serializables ----
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---- Enum values() / valueOf() used by DataStore-backed settings ----
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
