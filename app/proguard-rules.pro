# Readable crash traces.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Angus Mail / Jakarta Mail load providers and protocols by class name from META-INF files.
-keep class org.eclipse.angus.mail.** { *; }
-keep class org.eclipse.angus.activation.** { *; }
-keep class jakarta.mail.** { *; }
-keep class jakarta.activation.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }

# Optional classes that do not exist on Android.
-dontwarn java.awt.**
-dontwarn javax.naming.**
-dontwarn javax.security.sasl.**
-dontwarn javax.security.auth.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.annotation.**
-dontwarn org.jspecify.**
-dontwarn com.google.re2j.**
-dontwarn org.slf4j.**
-dontwarn org.osgi.**
-dontwarn sun.security.**
-dontwarn org.eclipse.angus.**
-dontwarn jakarta.**
