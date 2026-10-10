package com.ghtnql.kkkeyboard

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnterActionResolverTest {
    @Test
    fun resolvesCommonEditorActions() {
        listOf(
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_PREVIOUS,
        ).forEach { action ->
            assertEquals(action, EnterActionResolver.actionId(action))
        }
    }

    @Test
    fun returnsNullForUnspecifiedOrNone() {
        assertNull(EnterActionResolver.actionId(EditorInfo.IME_ACTION_UNSPECIFIED))
        assertNull(EnterActionResolver.actionId(EditorInfo.IME_ACTION_NONE))
    }

    @Test
    fun noEnterActionFlagForcesPlainEnter() {
        val options = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        assertNull(EnterActionResolver.actionId(options))
    }

    @Test
    fun customActionIdTakesPrecedenceWhenLabelIsPresent() {
        assertEquals(
            42,
            EnterActionResolver.actionId(
                EditorInfo.IME_ACTION_UNSPECIFIED,
                customActionLabel = "Lookup",
                customActionId = 42,
            ),
        )
    }

    @Test
    fun customActionRequiresLabel() {
        assertNull(
            EnterActionResolver.actionId(
                EditorInfo.IME_ACTION_UNSPECIFIED,
                customActionId = 42,
            ),
        )
    }

    @Test
    fun noEnterActionFlagSuppressesCustomAction() {
        assertNull(
            EnterActionResolver.actionId(
                EditorInfo.IME_FLAG_NO_ENTER_ACTION,
                customActionLabel = "Lookup",
                customActionId = 42,
            ),
        )
    }
}
