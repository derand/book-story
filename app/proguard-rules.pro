# Proguard-rules
-dontwarn android.content.res.**
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn org.kxml2.io.KXml**
-dontwarn org.slf4j.Logger
-dontwarn org.slf4j.LoggerFactory
-dontwarn org.xmlpull.v1.XmlPullParserFactory

# Keeping names of classes that produce undeterministic results
-keepnames class **
-keepnames class org.xmlpull.** { *; }
-keepclassmembernames class org.xmlpull.** { *; }
-keepnames class kotlin.reflect.jvm.internal.impl.builtins.PrimitiveType { values(); }
-keepnames class * implements android.os.Parcelable { ** CREATOR; }