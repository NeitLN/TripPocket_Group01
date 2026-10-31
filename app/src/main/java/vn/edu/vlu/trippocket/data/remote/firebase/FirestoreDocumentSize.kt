package vn.edu.vlu.trippocket.data.remote.firebase

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Storage size of a Firestore document, following
 * https://firebase.google.com/docs/firestore/storage-size (proposal 6.2, A5):
 * - string: UTF-8 bytes + 1; boolean and null: 1; integer and double: 8
 * - array: sum of its elements; map / nested object: sum of (key string + value)
 * - document name: each path segment as a string + 16
 * - document: name + every top-level (field name + value) + 32
 *
 * Objects are read like Firestore's mapper reads a POJO: through public getters, where
 * `getName()` becomes "name" and `isDone()` becomes "done". So the result is the size of
 * what would actually be uploaded, not of a JSON dump.
 */
object FirestoreDocumentSize {

    /** Firestore's hard limit for one document. */
    const val MAX_BYTES = 1_048_576

    /** Size of the backup document users/{uid}/trips/{tripId}. */
    fun ofTripBackup(snapshot: TripSnapshot): Int =
        ofDocument(listOf("users", snapshot.ownerUid, "trips", snapshot.tripId), snapshot)

    fun ofDocument(path: List<String>, value: Any): Int {
        val fields = fieldsOf(value)
        return path.sumOf(::ofString) + 16 + fields.entries.sumOf { (name, v) -> ofString(name) + ofValue(v) } + 32
    }

    fun ofValue(value: Any?): Int = when (value) {
        null -> 1
        is String -> ofString(value)
        is Boolean -> 1
        is Number -> 8
        is Enum<*> -> ofString(value.name)
        is Collection<*> -> value.sumOf(::ofValue)
        is Array<*> -> value.sumOf(::ofValue)
        is Map<*, *> -> value.entries.sumOf { (k, v) -> ofString(k.toString()) + ofValue(v) }
        else -> fieldsOf(value).entries.sumOf { (name, v) -> ofString(name) + ofValue(v) }
    }

    fun ofString(value: String): Int = value.toByteArray(Charsets.UTF_8).size + 1

    /** Bean properties as Firestore names them. */
    private fun fieldsOf(value: Any): Map<String, Any?> = value.javaClass.methods
        .filter { it.isGetter() }
        .associate { propertyName(it) to it.invoke(value) }

    private fun Method.isGetter(): Boolean {
        if (parameterCount != 0 || Modifier.isStatic(modifiers) || declaringClass == Any::class.java) return false
        return (name.startsWith("get") && name.length > 3 && returnType != Void.TYPE) ||
            (name.startsWith("is") && name.length > 2 && (returnType == Boolean::class.javaPrimitiveType))
    }

    private fun propertyName(method: Method): String {
        val bare = if (method.name.startsWith("is")) method.name.drop(2) else method.name.drop(3)
        return bare.replaceFirstChar { it.lowercaseChar() }
    }
}
