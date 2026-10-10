package com.ghtnql.kkkeyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ClipboardEntry(
    val text: String,
    val pinned: Boolean = false,
)

class ClipboardHistoryStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun list(): List<ClipboardEntry> {
        val entries = readEntries()
        return entries.filter { it.pinned } + entries.filter { !it.pinned }
    }

    @Synchronized
    fun save(text: String): Boolean {
        if (text.isEmpty() || text.length > MAX_TEXT_LENGTH) return false
        val entries = readEntries().toMutableList()
        val existingIndex = entries.indexOfFirst { it.text == text }
        val pinned = if (existingIndex >= 0) {
            val pinnedValue = entries[existingIndex].pinned
            entries.removeAt(existingIndex)
            pinnedValue
        } else {
            false
        }
        entries.add(0, ClipboardEntry(text, pinned))
        enforceUnpinnedCap(entries)
        writeEntries(entries)
        return true
    }

    @Synchronized
    fun use(text: String): Boolean {
        val entries = readEntries().toMutableList()
        val index = entries.indexOfFirst { it.text == text }
        if (index < 0) return false
        val entry = entries.removeAt(index)
        entries.add(0, entry)
        writeEntries(entries)
        return true
    }

    @Synchronized
    fun togglePin(text: String): Boolean {
        val entries = readEntries().toMutableList()
        val index = entries.indexOfFirst { it.text == text }
        if (index < 0) return false
        val entry = entries[index]
        if (!entry.pinned && entries.count { it.pinned } >= MAX_PINNED) return false
        entries.removeAt(index)
        entries.add(0, entry.copy(pinned = !entry.pinned))
        enforceUnpinnedCap(entries)
        writeEntries(entries)
        return true
    }

    @Synchronized
    fun delete(text: String): Boolean {
        val entries = readEntries().toMutableList()
        val removed = entries.removeAll { it.text == text }
        if (removed) writeEntries(entries)
        return removed
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun readEntries(): List<ClipboardEntry> {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val seen = mutableSetOf<String>()
            buildList<ClipboardEntry> {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val text = item.optString(KEY_TEXT)
                    if (text.isEmpty() || text.length > MAX_TEXT_LENGTH) continue
                    if (!seen.add(text)) continue
                    val pinned = item.optBoolean(KEY_PINNED, false)
                    if (pinned && count { it.pinned } >= MAX_PINNED) continue
                    if (!pinned && count { !it.pinned } >= MAX_UNPINNED) continue
                    add(ClipboardEntry(text, pinned))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun enforceUnpinnedCap(entries: MutableList<ClipboardEntry>) {
        var unpinned = entries.count { !it.pinned }
        if (unpinned <= MAX_UNPINNED) return
        val iterator = entries.listIterator(entries.size)
        while (iterator.hasPrevious() && unpinned > MAX_UNPINNED) {
            if (!iterator.previous().pinned) {
                iterator.remove()
                unpinned--
            }
        }
    }

    private fun writeEntries(entries: List<ClipboardEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put(KEY_TEXT, entry.text)
                    put(KEY_PINNED, entry.pinned)
                },
            )
        }
        preferences.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    companion object {
        const val MAX_UNPINNED = 30
        const val MAX_PINNED = 30
        const val MAX_TEXT_LENGTH = 10000
        internal const val PREFS_NAME = "clipboard_history"
        internal const val KEY_ENTRIES = "entries"
        internal const val KEY_TEXT = "text"
        internal const val KEY_PINNED = "pinned"
    }
}
