package com.ghtnql.kkkeyboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClipboardHistoryTest {
    private lateinit var context: Context
    private lateinit var store: ClipboardHistoryStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = ClipboardHistoryStore(context)
        store.clear()
    }

    @Test
    fun saveAndListMostRecentFirst() {
        assertTrue(store.save("a"))
        assertTrue(store.save("b"))
        assertEquals(listOf("b", "a"), store.list().map { it.text })
    }

    @Test
    fun saveRejectsEmptyAndOversized() {
        assertFalse(store.save(""))
        assertFalse(store.save("x".repeat(ClipboardHistoryStore.MAX_TEXT_LENGTH + 1)))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun saveAcceptsMaxLength() {
        val max = "y".repeat(ClipboardHistoryStore.MAX_TEXT_LENGTH)
        assertTrue(store.save(max))
        assertEquals(listOf(max), store.list().map { it.text })
    }

    @Test
    fun saveDedupesExactMatchAndPreservesPin() {
        assertTrue(store.save("dup"))
        assertTrue(store.save("other"))
        assertTrue(store.togglePin("dup"))
        assertTrue(store.save("dup"))
        val list = store.list()
        assertEquals(listOf("dup", "other"), list.map { it.text })
        assertTrue(list.first { it.text == "dup" }.pinned)
        assertEquals(2, list.size)
    }

    @Test
    fun pinnedListedFirst() {
        store.save("one")
        store.save("two")
        store.save("three")
        assertTrue(store.togglePin("one"))
        assertEquals(listOf("one", "three", "two"), store.list().map { it.text })
    }

    @Test
    fun unpinMovesEntryBackToUnpinnedSection() {
        store.save("one")
        store.save("two")
        assertTrue(store.togglePin("one"))
        assertTrue(store.togglePin("one"))
        val list = store.list()
        assertEquals(listOf("one", "two"), list.map { it.text })
        assertTrue(list.none { it.pinned })
    }

    @Test
    fun togglePinMissingReturnsFalse() {
        assertFalse(store.togglePin("missing"))
    }

    @Test
    fun useMovesExistingOnly() {
        store.save("a")
        store.save("b")
        assertTrue(store.use("a"))
        assertEquals(listOf("a", "b"), store.list().map { it.text })
        assertFalse(store.use("missing"))
        assertEquals(listOf("a", "b"), store.list().map { it.text })
    }

    @Test
    fun unpinnedCapEvictsOldest() {
        for (i in 0 until ClipboardHistoryStore.MAX_UNPINNED + 5) {
            assertTrue(store.save("u$i"))
        }
        val list = store.list()
        assertEquals(ClipboardHistoryStore.MAX_UNPINNED, list.size)
        assertEquals("u34", list.first().text)
        assertTrue(list.none { it.text == "u0" })
    }

    @Test
    fun pinsNotEvictedByUnpinnedOverflow() {
        for (i in 0 until ClipboardHistoryStore.MAX_UNPINNED) {
            store.save("u$i")
        }
        assertTrue(store.togglePin("u0"))
        for (i in 30 until 60) {
            store.save("n$i")
        }
        val list = store.list()
        assertTrue(list.first { it.text == "u0" }.pinned)
        assertEquals(ClipboardHistoryStore.MAX_UNPINNED + 1, list.size)
    }

    @Test
    fun pinAtCapacityReturnsFalse() {
        for (i in 0 until ClipboardHistoryStore.MAX_PINNED) {
            store.save("p$i")
            assertTrue(store.togglePin("p$i"))
        }
        store.save("extra")
        assertFalse(store.togglePin("extra"))
        assertFalse(store.list().first { it.text == "extra" }.pinned)
    }

    @Test
    fun deleteAndClear() {
        store.save("a")
        store.save("b")
        assertTrue(store.delete("a"))
        assertFalse(store.delete("a"))
        assertEquals(listOf("b"), store.list().map { it.text })
        store.clear()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun reopenPreservesOrderAndData() {
        store.save("a")
        store.save("b")
        assertTrue(store.togglePin("a"))
        val reopened = ClipboardHistoryStore(context).list()
        assertEquals(store.list(), reopened)
        assertEquals(
            listOf(ClipboardEntry("a", true), ClipboardEntry("b", false)),
            reopened,
        )
    }

    @Test
    fun unpinAtCapacityRetainsSelectedAndEvictsOldest() {
        store.save("pinned")
        store.togglePin("pinned")
        repeat(30) { store.save("recent$it") }
        store.togglePin("pinned")
        assertEquals(30, store.list().size)
        assertEquals("pinned", store.list().first().text)
        assertFalse(store.list().any { it.text == "recent0" })
        assertEquals(store.list(), ClipboardHistoryStore(context).list())
    }

    @Test
    fun corruptJsonReadsEmpty() {
        context.getSharedPreferences(
            ClipboardHistoryStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        ).edit().putString(ClipboardHistoryStore.KEY_ENTRIES, "{not-json").commit()
        assertTrue(ClipboardHistoryStore(context).list().isEmpty())
    }
}
