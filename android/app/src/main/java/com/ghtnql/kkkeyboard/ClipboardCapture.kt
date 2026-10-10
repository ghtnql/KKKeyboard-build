package com.ghtnql.kkkeyboard

import android.content.ClipboardManager
import android.content.Context
import java.security.MessageDigest

/** Captures plain, non-sensitive text only while the IME input view is active. */
class ClipboardCapture(context: Context, private val store: ClipboardHistoryStore, private val onChanged: () -> Unit = {}) {
    private val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val preferences = context.getSharedPreferences("clipboard_capture", Context.MODE_PRIVATE)
    private var active = false
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { if (active) { capture(); onChanged() } }
    fun start() {
        if (active || manager == null) return
        active = true
        runCatching { manager?.addPrimaryClipChangedListener(listener) }
        capture()
    }
    fun stop() {
        active = false
        runCatching { manager?.removePrimaryClipChangedListener(listener) }
    }
    private fun capture() {
        if (!active) return
        runCatching {
            val clip = manager?.primaryClip ?: return
            val description = clip.description
            if (description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return
            if (clip.itemCount == 0) return
            val raw = clip.getItemAt(0).text ?: return
            if (raw.isEmpty() || raw.length > ClipboardHistoryStore.MAX_TEXT_LENGTH) return
            val text = raw.toString()
            val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val identity = "${description.timestamp}:$hash"
            if (preferences.getString("last_identity", null) == identity) return
            if (store.save(text)) preferences.edit().putString("last_identity", identity).apply()
        }
    }
}
