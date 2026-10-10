package com.ghtnql.kkkeyboard

import com.ghtnql.kkkeyboard.sharedcore.JapanesePronunciationMatcher
import org.json.JSONObject

/** Shared, bundled vocabulary. Indexes are built once, never on each key press. */
class JapaneseDictionary private constructor(val entries: List<Entry>) {
    data class Entry(val id: String, val priority: Int, val aliases: List<String>, val surfaces: List<String>)
    data class Match(val length: Int, val surfaces: List<String>)

    private val exact = mutableMapOf<String, MutableList<Entry>>()
    private val prefixes = mutableMapOf<String, MutableList<Entry>>()
    private val tolerantExact = mutableMapOf<String, MutableList<Entry>>()
    private val tolerantPrefixes = mutableMapOf<String, MutableList<Entry>>()
    private val pronunciationMatcher = JapanesePronunciationMatcher()
    private var maxAliasLength = 0

    init {
        entries.sortedWith(compareBy<Entry> { it.priority }.thenBy { it.id }).forEach { entry ->
            entry.aliases.forEach { alias ->
                exact.getOrPut(alias) { mutableListOf() }.add(entry)
                maxAliasLength = maxOf(maxAliasLength, alias.length)
                addPrefixes(prefixes, phoneticKeys(alias), entry)

                val tolerantAlias = pronunciationMatcher.canonicalize(alias)
                val bucket = tolerantExact.getOrPut(tolerantAlias) { mutableListOf() }
                if (entry !in bucket) bucket.add(entry)
                addPrefixes(tolerantPrefixes, phoneticKeys(tolerantAlias), entry)
                // Preserve intermediate composition of an optional long vowel (뉴 + ㅇ -> 늉).
                for (boundary in alias.indices) {
                    val base = phoneticKeys(pronunciationMatcher.canonicalize(alias.take(boundary)))
                    val next = phoneticKeys(pronunciationMatcher.canonicalize(alias[boundary].toString()))
                    for (length in 1 until next.length) {
                        val key = base + next.take(length)
                        if (key.length >= MIN_PREFIX_KEYS) {
                            val partialBucket = tolerantPrefixes.getOrPut(key) { mutableListOf() }
                            if (entry !in partialBucket) partialBucket.add(entry)
                        }
                    }
                }
            }
        }
    }

    private fun literalSurfaces(input: String): List<String> =
        exact[input]?.flatMap { it.surfaces }?.distinct().orEmpty()

    private fun tolerantSurfaces(input: String): List<String> =
        tolerantExact[pronunciationMatcher.canonicalize(input)]
            ?.flatMap { it.surfaces }?.distinct().orEmpty()

    fun exact(input: String): List<String> =
        literalSurfaces(input).ifEmpty { tolerantSurfaces(input) }

    fun completions(input: String): List<String> {
        val direct = prefixes[phoneticKeys(input)]?.flatMap { it.surfaces }.orEmpty()
        val keys = phoneticKeys(pronunciationMatcher.canonicalize(input))
        // During composition the next onset may still be attached as a coda.
        // Offer both ㄱ/ㅋ and ㄷ/ㅌ prefixes without changing exact coda matching.
        val folded = when (keys.lastOrNull()) { 'ㅋ' -> 'ㄱ'; 'ㅌ' -> 'ㄷ'; else -> null }
        val prefixVariants = listOfNotNull(keys, folded?.let { keys.dropLast(1) + it }).distinct()
        val tolerant = prefixVariants.flatMap { key -> tolerantPrefixes[key]?.flatMap { it.surfaces }.orEmpty() }
        // Literal spellings stay first; equivalent pronunciation candidates follow.
        return (direct + tolerant).distinct().take(MAX_CANDIDATES)
    }

    private fun addPrefixes(index: MutableMap<String, MutableList<Entry>>, keys: String, entry: Entry) {
        for (length in MIN_PREFIX_KEYS until keys.length) {
            val bucket = index.getOrPut(keys.substring(0, length)) { mutableListOf() }
            if (entry !in bucket) bucket.add(entry)
        }
    }

    fun longestMatch(input: String, offset: Int): Match? {
        val remaining = input.length - offset
        // Preserve literal dictionary segmentation before accepting any pronunciation variant.
        for (length in minOf(maxAliasLength, remaining) downTo 2) {
            val surfaces = literalSurfaces(input.substring(offset, offset + length))
            if (surfaces.isNotEmpty()) return Match(length, surfaces)
        }
        for (length in minOf(JapaneseTransliterator.maxInputLength, remaining) downTo 2) {
            val surfaces = tolerantSurfaces(input.substring(offset, offset + length))
            if (surfaces.isNotEmpty()) return Match(length, surfaces)
        }
        return null
    }

    companion object {
        const val MAX_CANDIDATES = 16
        private const val MIN_PREFIX_KEYS = 4

        fun bundled(): JapaneseDictionary {
            val stream = requireNotNull(JapaneseDictionary::class.java.getResourceAsStream("/ja_common.json"))
            return stream.bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
        }

        fun parse(json: String): JapaneseDictionary {
            val root = JSONObject(json)
            require(root.getInt("version") == 1)
            val rows = root.getJSONArray("entries")
            val entries = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val aliases = row.getJSONArray("aliases")
                val surfaces = row.getJSONArray("surfaces")
                Entry(
                    row.getString("id"), row.getInt("priority"),
                    (0 until aliases.length()).map { aliases.getString(it) },
                    (0 until surfaces.length()).map { surfaces.getString(it) },
                ).also { entry ->
                    require(entry.id.isNotBlank() && entry.priority >= 0)
                    require(entry.aliases.isNotEmpty() && entry.surfaces.isNotEmpty())
                    require(entry.aliases.all { it.length in 1..JapaneseTransliterator.maxInputLength && it.all { ch -> ch in '\uAC00'..'\uD7A3' } })
                    require(entry.surfaces.all { it.isNotBlank() })
                }
            }
            require(entries.map { it.id }.distinct().size == entries.size)
            return JapaneseDictionary(entries)
        }

        // Decompose also the final consonant: 오항 is the key-prefix of 오하요.
        fun phoneticKeys(input: String): String = buildString {
            input.forEach { ch ->
                if (ch in '\uAC00'..'\uD7A3') {
                    val value = ch.code - 0xAC00
                    append(INITIALS[value / 588])
                    append(MEDIALS[(value % 588) / 28])
                    append(FINALS[value % 28])
                } else append(ch)
            }
        }

        private const val INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        private val MEDIALS = arrayOf("ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ", "ㅖ", "ㅗ", "ㅗㅏ", "ㅗㅐ", "ㅗㅣ", "ㅛ", "ㅜ", "ㅜㅓ", "ㅜㅔ", "ㅜㅣ", "ㅠ", "ㅡ", "ㅡㅣ", "ㅣ")
        private val FINALS = arrayOf("", "ㄱ", "ㄲ", "ㄱㅅ", "ㄴ", "ㄴㅈ", "ㄴㅎ", "ㄷ", "ㄹ", "ㄹㄱ", "ㄹㅁ", "ㄹㅂ", "ㄹㅅ", "ㄹㅌ", "ㄹㅍ", "ㄹㅎ", "ㅁ", "ㅂ", "ㅂㅅ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")
    }
}
