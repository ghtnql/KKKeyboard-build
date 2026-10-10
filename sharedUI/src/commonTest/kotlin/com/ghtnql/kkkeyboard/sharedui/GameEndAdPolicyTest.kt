package com.ghtnql.kkkeyboard.sharedui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameEndAdPolicyTest {

    private val t0 = 1_000_000_000_000L

    @Test
    fun rounds1And2FalseEvenWithSufficientTime() {
        val p = GameEndAdPolicy(lastFullscreenAtMillis = t0)
        p.onCompletedRound()
        assertFalse(p.canShow(t0 + 180_000L + 60_000L))
        p.onCompletedRound()
        assertFalse(p.canShow(t0 + 180_000L + 60_000L))
    }

    @Test
    fun threeRoundsTrueOnlyAfter3Min() {
        val p = GameEndAdPolicy(lastFullscreenAtMillis = t0)
        repeat(3) { p.onCompletedRound() }
        assertFalse(p.canShow(t0 + 179_999L))
        assertTrue(p.canShow(t0 + 180_000L))
    }

    @Test
    fun firstThreeShortRoundsFalse() {
        // First use: last = init-now, three completions in quick succession.
        val p = GameEndAdPolicy(lastFullscreenAtMillis = t0)
        p.onCompletedRound()
        p.onCompletedRound()
        p.onCompletedRound()
        assertFalse(p.canShow(t0 + 60_000L))
        assertFalse(p.canShow(t0 + 120_000L))
    }

    @Test
    fun skipOrFailureDoesNotReset() {
        val p = GameEndAdPolicy(lastFullscreenAtMillis = t0)
        repeat(3) { p.onCompletedRound() }
        // No API for skip/failure: a pure canShow query must not mutate.
        assertFalse(p.canShow(t0 + 10_000L))
        assertEquals(3, p.completedRoundsSinceAd)
        assertEquals(t0, p.lastFullscreenAtMillis)
        assertTrue(p.canShow(t0 + 180_000L))
    }

    @Test
    fun shownResetsCountAndTimestamp() {
        val p = GameEndAdPolicy(lastFullscreenAtMillis = t0)
        repeat(3) { p.onCompletedRound() }
        assertTrue(p.canShow(t0 + 180_000L))
        p.onFullscreenShown(t0 + 180_000L)
        assertEquals(0, p.completedRoundsSinceAd)
        assertEquals(t0 + 180_000L, p.lastFullscreenAtMillis)
        assertFalse(p.canShow(t0 + 180_000L))
    }

    @Test
    fun rewardFullscreenAlsoResets() {
        val p = GameEndAdPolicy(lastFullscreenAtMillis = t0)
        repeat(3) { p.onCompletedRound() }
        // Reward fullscreen uses the same callback.
        p.onFullscreenShown(t0 + 200_000L)
        assertEquals(0, p.completedRoundsSinceAd)
        assertEquals(t0 + 200_000L, p.lastFullscreenAtMillis)
        assertFalse(p.canShow(t0 + 200_000L))
    }

    @Test
    fun corruptCountClamps() {
        assertEquals(0, GameEndAdPolicy(completedRoundsSinceAd = -5, lastFullscreenAtMillis = t0).completedRoundsSinceAd)
        assertEquals(3, GameEndAdPolicy(completedRoundsSinceAd = 99, lastFullscreenAtMillis = t0).completedRoundsSinceAd)
        // Saturating increment never exceeds 3.
        val p = GameEndAdPolicy(completedRoundsSinceAd = 3, lastFullscreenAtMillis = t0)
        p.onCompletedRound()
        p.onCompletedRound()
        assertEquals(3, p.completedRoundsSinceAd)
    }

    @Test
    fun clockBackwardsFalse() {
        val p = GameEndAdPolicy(lastFullscreenAtMillis = t0)
        repeat(3) { p.onCompletedRound() }
        assertFalse(p.canShow(t0 - 1L))
    }

    @Test
    fun restartWithSavedStateSame() {
        val p = GameEndAdPolicy(lastFullscreenAtMillis = t0)
        p.onCompletedRound()
        p.onCompletedRound()
        // Persist + restore.
        val restored = GameEndAdPolicy(
            completedRoundsSinceAd = p.completedRoundsSinceAd,
            lastFullscreenAtMillis = p.lastFullscreenAtMillis,
        )
        assertEquals(p.completedRoundsSinceAd, restored.completedRoundsSinceAd)
        assertEquals(p.lastFullscreenAtMillis, restored.lastFullscreenAtMillis)
        assertFalse(restored.canShow(t0 + 500_000L))
        restored.onCompletedRound()
        assertTrue(restored.canShow(t0 + 500_000L))
    }

    @Test
    fun boundaryExactly180k() {
        val p = GameEndAdPolicy(lastFullscreenAtMillis = t0)
        repeat(3) { p.onCompletedRound() }
        assertFalse(p.canShow(t0 + 179_999L))
        assertTrue(p.canShow(t0 + 180_000L))
        assertTrue(p.canShow(t0 + 180_001L))
    }

    @Test
    fun overflowRobust() {
        // now = MAX_VALUE with last = 0: must be true, no wrap.
        val p = GameEndAdPolicy(completedRoundsSinceAd = 3, lastFullscreenAtMillis = 0L)
        assertTrue(p.canShow(Long.MAX_VALUE))
        // last near MAX_VALUE, small elapsed: false, no wrap.
        val q = GameEndAdPolicy(completedRoundsSinceAd = 3, lastFullscreenAtMillis = Long.MAX_VALUE - 1_000L)
        assertFalse(q.canShow(Long.MAX_VALUE))
        // now below last near the top: false, no wrap.
        assertFalse(q.canShow(Long.MAX_VALUE - 1_001L))
    }
}
