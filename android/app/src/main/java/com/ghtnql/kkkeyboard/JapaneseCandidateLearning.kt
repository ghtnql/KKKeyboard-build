package com.ghtnql.kkkeyboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * On-device preference learning for Japanese conversion candidates.
 *
 * Only bounded reading/candidate pairs and counters are stored in app-private preferences.
 * Nothing is logged or sent over the network.
 */
class JapaneseCandidateLearning internal constructor(
    private val preferences: SharedPreferences,
) {
    data class Score(val count: Int, val lastUsed: Long)

    fun rank(reading: String, candidates: List<String>): List<String> {
        if (candidates.size < 2 || !isValidReading(reading)) return candidates
        val bucket = loadRoot()
            .optJSONObject(KEY_READINGS)
            ?.optJSONObject(reading)
            ?.optJSONObject(KEY_CANDIDATES)
            ?: return candidates

        val originalOrder = candidates.withIndex().associate { it.value to it.index }
        return candidates.sortedWith(
            compareByDescending<String> { score(bucket, it).count }
                .thenByDescending { score(bucket, it).lastUsed }
                .thenBy { originalOrder[it] ?: Int.MAX_VALUE },
        )
    }

    fun recordSelection(reading: String, candidate: String) {
        if (!isValidReading(reading) || candidate.isBlank() || candidate.length > MAX_CANDIDATE_LENGTH) return

        val root = loadRoot()
        val readings = root.optJSONObject(KEY_READINGS) ?: JSONObject().also {
            root.put(KEY_READINGS, it)
        }
        val nextSequence = root.optLong(KEY_SEQUENCE, 0L).let { if (it == Long.MAX_VALUE) 1L else it + 1L }
        root.put(KEY_SEQUENCE, nextSequence)

        val readingEntry = readings.optJSONObject(reading) ?: JSONObject().also {
            readings.put(reading, it)
        }
        readingEntry.put(KEY_UPDATED, nextSequence)
        val learnedCandidates = readingEntry.optJSONObject(KEY_CANDIDATES) ?: JSONObject().also {
            readingEntry.put(KEY_CANDIDATES, it)
        }
        val candidateEntry = learnedCandidates.optJSONObject(candidate) ?: JSONObject().also {
            learnedCandidates.put(candidate, it)
        }
        candidateEntry.put(KEY_COUNT, (candidateEntry.optInt(KEY_COUNT, 0) + 1).coerceAtMost(MAX_COUNT))
        candidateEntry.put(KEY_LAST_USED, nextSequence)

        pruneReadings(readings)
        preferences.edit().putString(PREFERENCE_DATA, root.toString()).apply()
    }

    private fun score(bucket: JSONObject, candidate: String): Score {
        val value = bucket.optJSONObject(candidate) ?: return Score(0, 0L)
        return Score(value.optInt(KEY_COUNT, 0), value.optLong(KEY_LAST_USED, 0L))
    }

    private fun loadRoot(): JSONObject {
        val raw = preferences.getString(PREFERENCE_DATA, null) ?: return freshRoot()
        return runCatching { JSONObject(raw) }
            .getOrNull()
            ?.takeIf { it.optInt(KEY_VERSION, 0) == VERSION }
            ?: freshRoot()
    }

    private fun freshRoot() = JSONObject()
        .put(KEY_VERSION, VERSION)
        .put(KEY_SEQUENCE, 0L)
        .put(KEY_READINGS, JSONObject())

    private fun pruneReadings(readings: JSONObject) {
        while (readings.length() > MAX_READINGS) {
            var oldestKey: String? = null
            var oldestSequence = Long.MAX_VALUE
            val keys = readings.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val updated = readings.optJSONObject(key)?.optLong(KEY_UPDATED, 0L) ?: 0L
                if (updated < oldestSequence) {
                    oldestSequence = updated
                    oldestKey = key
                }
            }
            if (oldestKey == null) return
            readings.remove(oldestKey)
        }
    }

    private fun isValidReading(reading: String): Boolean =
        reading.isNotEmpty() &&
            reading.length <= JapaneseTransliterator.maxInputLength &&
            reading.first() != ' ' &&
            reading.last() != ' ' &&
            reading.all { it == ' ' || it in '\uAC00'..'\uD7A3' || it in '\u3131'..'\u314E' }

    companion object {
        private const val VERSION = 1
        private const val MAX_READINGS = 256
        private const val MAX_CANDIDATE_LENGTH = 64
        private const val MAX_COUNT = 1_000_000
        private const val PREFERENCES = "japanese_candidate_learning"
        private const val PREFERENCE_DATA = "state"
        private const val KEY_VERSION = "version"
        private const val KEY_SEQUENCE = "sequence"
        private const val KEY_READINGS = "readings"
        private const val KEY_UPDATED = "updated"
        private const val KEY_CANDIDATES = "candidates"
        private const val KEY_COUNT = "count"
        private const val KEY_LAST_USED = "lastUsed"

        fun from(context: Context): JapaneseCandidateLearning =
            JapaneseCandidateLearning(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE))
    }
}
