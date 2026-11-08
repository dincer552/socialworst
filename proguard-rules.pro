# Keep SSH algorithm/provider reflection and terminal native method names stable.
-keep class com.jcraft.jsch.** { *; }
-keep class com.termux.terminal.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
