# NewPipe Extractor / Rhino
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

# Keep extractor-facing model metadata.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
