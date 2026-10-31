# Intent is open source: keep names readable so crash reports make sense.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod

# Entry points declared in the manifest (aapt2/AGP also generate these, kept here for clarity).
-keep class com.poshith.intent.IntentApp { <init>(); }
-keep class * extends android.app.Activity { <init>(); }
-keep class * extends android.app.Service { <init>(); }
-keep class * extends android.content.BroadcastReceiver { <init>(); }

-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**
-dontwarn java.lang.invoke.**
