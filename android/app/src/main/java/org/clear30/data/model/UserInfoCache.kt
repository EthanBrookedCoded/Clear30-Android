package org.clear30.data.model

import org.clear30.data.LocalStore

/**
 * Generic object caching — ported from UserInfo's `getCachedObject` /
 * `setCacheObject` (UserInfo.swift). Swift used JSONEncoder/Decoder; here we use
 * the shared kotlinx [LocalStore.json]. Declared as inline-reified extensions
 * because Kotlin members can't be reified.
 */
inline fun <reified T> UserInfo.getCachedObject(key: String): T? {
    val raw = cache[key] ?: return null
    return runCatching { LocalStore.json.decodeFromString<T>(raw) }.getOrNull()
}

inline fun <reified T> UserInfo.setCacheObject(key: String, value: T) {
    cache[key] = LocalStore.json.encodeToString(value)
}
