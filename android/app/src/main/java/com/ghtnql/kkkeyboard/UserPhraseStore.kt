package com.ghtnql.kkkeyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class UserPhrase(
    val id: String,
    val title: String,
    val content: String,
)

enum class PhraseValidationError {
    EMPTY_TITLE,
    EMPTY_CONTENT,
    TITLE_TOO_LONG,
    CONTENT_TOO_LONG,
}

object UserPhraseStore {
    const val MAX_PHRASES = 50
    const val MAX_TITLE_LENGTH = 40
    const val MAX_CONTENT_LENGTH = 500

    private const val PREFERENCES = "user_phrases"
    private const val KEY_PHRASES = "phrases"
    private const val KEY_DEFAULTS_SEEDED = "defaultPhrasesSeeded.v1"
    private const val DEFAULT_PHRASES_ASSET = "default_phrases.json"

    fun validationError(title: String, content: String): PhraseValidationError? = when {
        title.isBlank() -> PhraseValidationError.EMPTY_TITLE
        content.isBlank() -> PhraseValidationError.EMPTY_CONTENT
        title.trim().length > MAX_TITLE_LENGTH -> PhraseValidationError.TITLE_TOO_LONG
        content.length > MAX_CONTENT_LENGTH -> PhraseValidationError.CONTENT_TOO_LONG
        else -> null
    }

    @Synchronized
    fun read(context: Context): List<UserPhrase> {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val raw = preferences.getString(KEY_PHRASES, null)
        val stored = parse(raw)
        if (preferences.getBoolean(KEY_DEFAULTS_SEEDED, false)) return stored
        if (raw != null && runCatching { JSONArray(raw) }.isFailure) return stored
        val defaults = loadDefaults(context) ?: return stored
        val ids = stored.mapTo(mutableSetOf()) { it.id }
        val seeded = (stored + defaults.filter { ids.add(it.id) }
            .take((MAX_PHRASES - stored.size).coerceAtLeast(0)))
        preferences.edit()
            .putString(KEY_PHRASES, encode(seeded))
            .putBoolean(KEY_DEFAULTS_SEEDED, true)
            .commit()
        return seeded
    }

    private fun parse(raw: String?): List<UserPhrase> {
        if (raw == null) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val ids = mutableSetOf<String>()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val phrase = UserPhrase(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        content = item.optString("content"),
                    )
                    if (phrase.id.isBlank() || !ids.add(phrase.id)) continue
                    if (validationError(phrase.title, phrase.content) != null) continue
                    add(phrase.copy(title = phrase.title.trim()))
                    if (size == MAX_PHRASES) break
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadDefaults(context: Context): List<UserPhrase>? = runCatching {
        val raw = context.assets.open(DEFAULT_PHRASES_ASSET).bufferedReader().use { it.readText() }
        parse(raw).also { require(it.size == 10) { "Invalid bundled default phrases" } }
    }.getOrNull()

    @Synchronized
    fun add(context: Context, title: String, content: String): UserPhrase? {
        if (validationError(title, content) != null) return null
        val phrases = read(context).toMutableList()
        if (phrases.size >= MAX_PHRASES) return null
        val phrase = UserPhrase(UUID.randomUUID().toString(), title.trim(), content)
        phrases += phrase
        write(context, phrases)
        return phrase
    }

    @Synchronized
    fun update(context: Context, id: String, title: String, content: String): Boolean {
        if (validationError(title, content) != null) return false
        val phrases = read(context).toMutableList()
        val index = phrases.indexOfFirst { it.id == id }
        if (index < 0) return false
        phrases[index] = UserPhrase(id, title.trim(), content)
        write(context, phrases)
        return true
    }

    @Synchronized
    fun delete(context: Context, id: String): Boolean {
        val phrases = read(context).toMutableList()
        val removed = phrases.removeAll { it.id == id }
        if (removed) write(context, phrases)
        return removed
    }

    @Synchronized
    internal fun clear(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun write(context: Context, phrases: List<UserPhrase>) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PHRASES, encode(phrases))
            .apply()
    }

    private fun encode(phrases: List<UserPhrase>): String {
        val array = JSONArray()
        phrases.forEach { phrase ->
            array.put(JSONObject().apply {
                put("id", phrase.id)
                put("title", phrase.title)
                put("content", phrase.content)
            })
        }
        return array.toString()
    }
}
