# Room generates implementations reflectively referenced by name.
-keep class androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }

# Room looks up the generated `*_Impl` for each database and DAO by name, so the
# names have to survive. The bodies do not — R8 is free to shrink inside them.
-keep,allowobfuscation,allowshrinking class * extends androidx.room.RoomDatabase
-keepnames class **_Impl { <init>(...); }

# Entities and DAOs are read by Room's generated code, not by reflection, so
# they need no keep rules of their own. The one exception is the enum a column
# is stored as: Room's converters call `valueOf` on the name.
-keepclassmembers enum com.citymemory.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# `CityMapCodec` reads a packed asset whose shape ids are wire values. Nothing
# reflective, but keeping the enum's ordinal-to-id mapping honest is cheap.
-keep class com.citymemory.domain.model.ShapeKind { *; }

# Coroutines ships its own rules; this covers the debug agent probe that
# `proguard-android-optimize.txt` otherwise leaves a dangling reference to.
-dontwarn kotlinx.coroutines.debug.**

# Compose and AndroidX supply consumer rules for everything else they need.
