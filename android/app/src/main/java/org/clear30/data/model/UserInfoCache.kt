package org.clear30.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import org.clear30.data.LocalStore

/**
 * Generic object caching — ported from UserInfo's `getCachedObject` /
 * `setCacheObject` (UserInfo.swift). Swift used JSONEncoder/Decoder; here we use
 * the shared kotlinx [LocalStore.json]. Declared as inline-reified extensions
 * because Kotlin members can't be reified.
 */
inline fun <reified T> UserInfo.getCachedObject(key: String): T? {
    val raw = cache[key] ?: return null
    // Pass the serializer explicitly so we always hit the (serializer, string)
    // overload — the reified-without-arg form picks up `Json.decodeFromString`
    // inconsistently depending on which kotlinx imports are visible.
    return runCatching { LocalStore.json.decodeFromString(serializer<T>(), raw) }.getOrNull()
}

inline fun <reified T> UserInfo.setCacheObject(key: String, value: T) {
    cache[key] = LocalStore.json.encodeToString(serializer<T>(), value)
}
