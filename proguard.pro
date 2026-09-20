# Dev Sandbox - ProGuard Obfuscation Configuration
# Author: TNQ MEDIA

-dontwarn
-dontnote

# Optimization passes
-optimizationpasses 3
-overloadaggressively
-repackageclasses ''
-allowaccessmodification

# Strip debug info (line numbers, source files, local variable tables)
-renamesourcefileattribute ''

# Keep essential runtime attributes
-keepattributes Exceptions,InnerClasses,Signature,*Annotation*,EnclosingMethod

# Keep application main entry point
-keep public class com.dixon.sandbox.DevSandboxApp {
    public static void main(java.lang.String[]);
}

# Keep Swing components
-keep public class * extends javax.swing.JFrame
-keep public class * extends javax.swing.JPanel
-keep public class * extends javax.swing.JDialog

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
