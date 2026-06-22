# 1. Preserve Apache FTP Server core and its dynamic UserManager bindings
-keep class org.apache.ftpserver.** { *; }
-keep interface org.apache.ftpserver.** { *; }

# 2. Preserve the SLF4J No-Op Silent Logger so the FTP server doesn't crash trying to print to console
-keep class org.slf4j.** { *; }

# 3. Preserve AndroidX ViewBinding signatures
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static * bind(android.view.View);
}

# 4. CRITICAL: Keep custom ViewGroups inflated via XML (Protects NetworkFragment from ClassNotFound crashes)
-keep class com.goodwy.ftpmanager.fragments.** {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# 5. Keep standard Android Parcelable / Serializable data models intact
-keepclassmembers class * implements android.os.Parcelable {
    static android.os.Parcelable$Creator CREATOR;
}
