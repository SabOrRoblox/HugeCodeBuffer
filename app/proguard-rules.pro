# Keep JNI
-keep class com.hugecode.buffer.CryptoManager {
    native <methods>;
}

# Keep X25519
-keep class sun.security.ec.** { *; }
-dontwarn sun.security.ec.**

# Keep KeyAgreement
-keep class javax.crypto.KeyAgreement { *; }

# === БАЗОВАЯ ОПТИМИЗАЦИЯ ===
-optimizationpasses 10
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively
-repackageclasses ''
-dontpreverify
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-useuniqueclassmembernames

# === УДАЛЕНИЕ ВСЕГО ЛИШНЕГО ===
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

-assumenosideeffects class java.lang.String {
    public static java.lang.String valueOf(...);
}

# === СОХРАНЕНИЕ WEB SOCKET ===
-keep class org.java_websocket.** { *; }
-keep class org.java_websocket.client.** { *; }
-keep class org.java_websocket.handshake.** { *; }
-keep class org.java_websocket.drafts.** { *; }
-keep class org.java_websocket.framing.** { *; }
-keep class org.java_websocket.enums.** { *; }
-keep class org.java_websocket.exceptions.** { *; }
-keep class org.java_websocket.extensions.** { *; }
-keep class org.java_websocket.protocols.** { *; }
-keep class org.java_websocket.server.** { *; }
-keep class org.java_websocket.util.** { *; }

# === JSON ===
-keep class org.json.** { *; }

# === KOTLIN COROUTINES ===
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# === ANDROID SERVICE ===
-keep class com.hugecode.buffer.DeviceService { *; }
-keep class com.hugecode.buffer.MainActivity { *; }
-keep class com.hugecode.buffer.BootReceiver { *; }
-keep class com.hugecode.buffer.RestartReceiver { *; }

# === KEEP AIDL ===
-keep class * extends android.os.IInterface { *; }
-keep class * extends android.os.Binder { *; }

# === KEEP ANNOTATIONS ===
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile
-keepattributes LineNumberTable
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# === KEEP NATIVE ===
-keepclasseswithmembernames class * {
    native <methods>;
}

# === KEEP ENUM ===
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# === KEEP SERIALIZATION ===
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# === УДАЛЕНИЕ DEBUG ИНФОРМАЦИИ ===
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# === АГРЕССИВНАЯ ОБФУСКАЦИЯ ===
-obfuscationdictionary proguard-dictionary.txt
-classobfuscationdictionary proguard-dictionary.txt
-packageobfuscationdictionary proguard-dictionary.txt

# === УДАЛЕНИЕ НЕИСПОЛЬЗУЕМОГО ===
-whyareyoukeeping class * extends android.app.Service

# === OPTIMIZATION ===
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
