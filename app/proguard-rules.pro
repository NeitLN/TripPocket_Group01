# TripPocket R8 rules (M5).
#
# Only classes the app reads through reflection are kept here. Retrofit, Room, WorkManager,
# Firebase, Glide and OkHttp ship their own consumer rules, so they are not repeated.

# Gson fills these DTOs by field name: both the class and its fields must survive R8.
# They are the Retrofit payloads of MockAPI (Trip Ideas), Wikipedia, Open-Meteo and
# open.er-api.com.
-keep class vn.edu.vlu.trippocket.data.remote.mockapi.** { *; }
-keep class vn.edu.vlu.trippocket.data.remote.wikipedia.** { *; }
-keep class vn.edu.vlu.trippocket.data.remote.openmeteo.** { *; }
-keep class vn.edu.vlu.trippocket.data.remote.exchangerate.** { *; }

# WeatherRepository stores the cached days as JSON in Room through Gson. It is a private
# nested class, so R8 would happily rename (or drop) the fields it never sees read in code.
-keep class vn.edu.vlu.trippocket.data.repository.WeatherRepository$CachedDay { *; }

# Retrofit and kotlinx.coroutines read annotations and generic signatures at runtime.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class kotlin.Metadata { *; }

# Firestore itself never reflects on TripSnapshot: TripSnapshotCodec writes and reads plain
# Map<String, Any?>. But FirestoreDocumentSize (the 1 MB guard in BackupRepository) sizes a
# snapshot by calling its public get*/is* methods through reflection. If R8 renames them,
# the guard sees no fields and lets an oversized backup through, so keep the getter names.
-keepclassmembers class vn.edu.vlu.trippocket.data.remote.firebase.*Snapshot {
    public *** get*();
    public boolean is*();
}
