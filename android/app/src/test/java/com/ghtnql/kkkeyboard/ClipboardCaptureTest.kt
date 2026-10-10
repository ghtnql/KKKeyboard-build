package com.ghtnql.kkkeyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.os.PersistableBundle
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipboardCaptureTest {
    private lateinit var context: Context
    private lateinit var manager: ClipboardManager
    private lateinit var store: ClipboardHistoryStore
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        store = ClipboardHistoryStore(context)
        store.clear()
        context.getSharedPreferences("clipboard_capture", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun copy(text: String, sensitive: Boolean = false) {
        manager.setPrimaryClip(ClipData.newPlainText("test", text).apply {
            description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", sensitive) }
        })
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun capturesOnlyWhileActiveAndRefreshesSensitiveChanges() {
        copy("first")
        var changes = 0
        val capture = ClipboardCapture(context, store) { changes++ }
        capture.start()
        assertEquals(listOf("first"), store.list().map { it.text })
        copy("secret", true)
        assertFalse(store.list().any { it.text == "secret" })
        assertTrue(changes > 0)
        capture.stop(); copy("hidden")
        assertFalse(store.list().any { it.text == "hidden" })
    }
    @Test fun deletedCurrentDoesNotReturnAfterNewCaptureInstance() {
        copy("delete-me")
        val capture = ClipboardCapture(context, store)
        capture.start(); store.clear(); capture.stop()
        val restarted = ClipboardCapture(context, ClipboardHistoryStore(context))
        restarted.start()
        assertTrue(store.list().isEmpty())
        copy("new")
        assertEquals(listOf("new"), store.list().map { it.text })
        restarted.stop()
    }
    @Test fun ignoresUrisOversizeAndSensitiveAtStart() {
        copy("secret", true)
        val capture = ClipboardCapture(context, store)
        capture.start()
        assertTrue(store.list().isEmpty())
        copy("x".repeat(10001))
        assertTrue(store.list().isEmpty())
        manager.setPrimaryClip(ClipData.newRawUri("uri", Uri.parse("content://example/item")))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(store.list().isEmpty())
        capture.stop()
    }
}
