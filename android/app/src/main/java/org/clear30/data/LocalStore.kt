package org.clear30.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.clear30.data.model.AssessmentQuestionType

/**
 * Local persistence layer — the Android replacement for SwiftData's
 * `ModelContext`.
 *
 * On iOS, the singleton models (UserInfo, Program, OnboardingSetup,
 * JournalEntries, ExperimentController) are each fetched as the single row of
 * their type and mutated in place, with `context.save()` flushing changes. We
 * mirror that by serialising each model to JSON and storing it under a stable
 * key in a Preferences DataStore. `save(...)` is the `context.save()` analog.
 *
 * Collection element models (JournalEntry, ProgramMessage, ProgramBreak, ...)
 * are nested inside their parent singleton's JSON, matching how the UI accesses
 * them (e.g. `journalEntries.entries`, `program.breaks`).
 */
object LocalStore {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "clear30_store")

    val json = Json {
        ignoreUnknownKeys = true   // tolerate schema drift (was SwiftData migration logic)
        encodeDefaults = true
        explicitNulls = false
        serializersModule = SerializersModule {
            // Backend may add new AssessmentQuestionType discriminators in A/B tests;
            // route any unknown discriminator to [AssessmentQuestionType.Unknown]
            // instead of throwing SerializationException and breaking the whole flow.
            polymorphic(AssessmentQuestionType::class) {
                defaultDeserializer { AssessmentQuestionType.Unknown.serializer() }
            }
        }
    }

    private lateinit var appContext: Context

    fun init(context: Context) { appContext = context.applicationContext }

    /** Load the single stored instance of [T], or null if none persisted yet. */
    suspend fun <T> load(key: String, serializer: KSerializer<T>): T? {
        val prefs = appContext.dataStore.data.first()
        val raw = prefs[stringPreferencesKey(key)] ?: return null
        return runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
    }

    /** Persist [value] under [key] — the `context.save()` equivalent. */
    suspend fun <T> save(key: String, serializer: KSerializer<T>, value: T) {
        val raw = json.encodeToString(serializer, value)
        appContext.dataStore.edit { it[stringPreferencesKey(key)] = raw }
    }

    /** Delete a single key (used on sign-out / model reset). */
    suspend fun clear(key: String) {
        appContext.dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    /** Wipe everything (sign-out deletes all SwiftData models). */
    suspend fun clearAll() {
        appContext.dataStore.edit { it.clear() }
    }
}
