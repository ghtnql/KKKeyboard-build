package com.ghtnql.kkkeyboard

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserPhraseStoreTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        UserPhraseStore.clear(context)
        // Legacy CRUD fixtures start with an intentionally empty, already seeded store.
        context.getSharedPreferences("user_phrases", Context.MODE_PRIVATE)
            .edit().putBoolean("defaultPhrasesSeeded.v1", true).commit()
    }

    @After fun tearDown() {
        UserPhraseStore.clear(context)
    }

    @Test fun addUpdateAndDeletePreserveStableIdAndExactContent() {
        val added = UserPhraseStore.add(context, "  Greeting  ", "Hello,\nworld!")
        assertNotNull(added)
        assertEquals("Greeting", added?.title)
        assertEquals("Hello,\nworld!", added?.content)
        assertTrue(UserPhraseStore.update(context, added!!.id, "Updated", "  keep spaces  "))

        val updated = UserPhraseStore.read(context).single()
        assertEquals(added.id, updated.id)
        assertEquals("Updated", updated.title)
        assertEquals("  keep spaces  ", updated.content)
        assertTrue(UserPhraseStore.delete(context, added.id))
        assertTrue(UserPhraseStore.read(context).isEmpty())
        assertFalse(UserPhraseStore.delete(context, added.id))
    }

    @Test fun validationRejectsBlankAndOversizedValues() {
        assertEquals(PhraseValidationError.EMPTY_TITLE, UserPhraseStore.validationError(" ", "content"))
        assertEquals(PhraseValidationError.EMPTY_CONTENT, UserPhraseStore.validationError("title", "\n"))
        assertEquals(
            PhraseValidationError.TITLE_TOO_LONG,
            UserPhraseStore.validationError("x".repeat(UserPhraseStore.MAX_TITLE_LENGTH + 1), "content"),
        )
        assertEquals(
            PhraseValidationError.CONTENT_TOO_LONG,
            UserPhraseStore.validationError("title", "x".repeat(UserPhraseStore.MAX_CONTENT_LENGTH + 1)),
        )
        assertNull(UserPhraseStore.add(context, "", "content"))
        assertTrue(UserPhraseStore.read(context).isEmpty())
    }

    @Test fun idsAreUniqueAndPhraseCountIsBounded() {
        val first = UserPhraseStore.add(context, "0", "content")!!
        val second = UserPhraseStore.add(context, "1", "content")!!
        assertNotEquals(first.id, second.id)
        for (index in 2 until UserPhraseStore.MAX_PHRASES) {
            assertNotNull(UserPhraseStore.add(context, index.toString(), "content"))
        }
        assertNull(UserPhraseStore.add(context, "overflow", "content"))
        assertEquals(UserPhraseStore.MAX_PHRASES, UserPhraseStore.read(context).size)
    }
    @Test fun defaultsSeedOnceAndDeletedEntriesDoNotReturn() {
        UserPhraseStore.clear(context)
        val first = UserPhraseStore.read(context)
        assertEquals(10, first.size)
        assertEquals("こんにちは", first.first().content)
        assertEquals("また明日。", first.last().content)
        assertEquals(10, first.map { it.id }.distinct().size)
        assertTrue(UserPhraseStore.delete(context, first.first().id))
        assertEquals(9, UserPhraseStore.read(context).size)
        assertEquals(9, UserPhraseStore.read(context).size)
    }

    @Test fun defaultsAppendToExistingCustomPhrasesIncludingAnEmptySavedArray() {
        val custom = UserPhraseStore.add(context, "내 문구", "自分の文")!!
        context.getSharedPreferences("user_phrases", Context.MODE_PRIVATE)
            .edit().remove("defaultPhrasesSeeded.v1").commit()
        val merged = UserPhraseStore.read(context)
        assertEquals(11, merged.size)
        assertEquals(custom, merged.first())
        assertEquals(11, UserPhraseStore.read(context).size)

        UserPhraseStore.clear(context)
        context.getSharedPreferences("user_phrases", Context.MODE_PRIVATE)
            .edit().putString("phrases", "[]").commit()
        assertEquals(10, UserPhraseStore.read(context).size)
    }

    @Test fun invalidExistingStorageIsNotOverwrittenBySeeding() {
        UserPhraseStore.clear(context)
        val preferences = context.getSharedPreferences("user_phrases", Context.MODE_PRIVATE)
        preferences.edit().putString("phrases", "{not-json").commit()
        assertTrue(UserPhraseStore.read(context).isEmpty())
        assertEquals("{not-json", preferences.getString("phrases", null))
        assertFalse(preferences.getBoolean("defaultPhrasesSeeded.v1", false))
    }

}
