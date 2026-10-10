package com.ghtnql.kkkeyboard

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private class FakeGateway : GameEndAdGateway {
    var prepareCount = 0
    var disposeCount = 0
    var acceptShow = true
    var finishDuringShow = false
    var callbackOnDispose = true
    var throwOnDispose = false
    var pendingShown: (() -> Unit)? = null
    var pendingFinished: (() -> Unit)? = null
    var showCalls = 0

    override fun prepare() {
        prepareCount++
    }

    override fun show(onShown: () -> Unit, onFinished: () -> Unit): Boolean {
        showCalls++
        if (!acceptShow) return false
        pendingShown = onShown
        pendingFinished = onFinished
        if (finishDuringShow) {
            pendingShown = null
            pendingFinished = null
            onFinished()
        }
        return true
    }

    override fun dispose() {
        disposeCount++
        val cb = pendingFinished
        pendingFinished = null
        pendingShown = null
        if (callbackOnDispose) cb?.invoke()
        if (throwOnDispose) throw IllegalStateException("disposal failed")
    }

    fun deliverShownThenDismiss() {
        pendingShown?.invoke()
        val cb = pendingFinished
        pendingFinished = null
        pendingShown = null
        cb?.invoke()
    }

    fun deliverFailure() {
        val cb = pendingFinished
        pendingFinished = null
        pendingShown = null
        cb?.invoke()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameEndAdControllerTest {

    private lateinit var prefs: SharedPreferences
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_game_end_${System.nanoTime()}", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        now = 1_000_000L
    }

    private fun controller(gateway: FakeGateway): GameEndAdController =
        GameEndAdController(prefs, gateway) { now }

    @Test
    fun `requires 3 rounds and 3 min interval`() {
        val gateway = FakeGateway().apply { acceptShow = true }
        val c = controller(gateway)
        var finished = 0
        // First two rounds: count only, no show.
        assertFalse(c.onCompletedRound { finished++ })
        assertFalse(c.onCompletedRound { finished++ })
        assertEquals(0, finished)
        assertEquals(2, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
        // Third round but interval not elapsed (init-now persisted at construction).
        now += 60_000L
        assertFalse(c.onCompletedRound { finished++ })
        assertEquals(0, finished)
        // Count saturates at 3; still no callback.
        assertEquals(3, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
    }

    @Test
    fun `not ready skip retains count then next eligible works`() {
        val gateway = FakeGateway().apply { acceptShow = false }
        val c = controller(gateway)
        now += 200_000L
        var finished = 0
        assertFalse(c.onCompletedRound { finished++ })
        assertFalse(c.onCompletedRound { finished++ })
        // Third round eligible by count+time but gateway not ready.
        assertFalse(c.onCompletedRound { finished++ })
        assertEquals(0, finished)
        assertEquals(3, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
        // Next eligible round works once gateway ready.
        gateway.acceptShow = true
        assertTrue(c.onCompletedRound { finished++ })
        assertEquals(0, finished) // pending: finished only after dismissal
        gateway.deliverShownThenDismiss()
        assertEquals(1, finished)
        assertEquals(0, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
    }

    @Test
    fun `recreation restores persisted state`() {
        val gateway = FakeGateway().apply { acceptShow = false }
        var c = controller(gateway)
        now += 200_000L
        var finished = 0
        c.onCompletedRound { finished++ }
        c.onCompletedRound { finished++ }
        assertEquals(2, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
        // Recreate controller over same prefs.
        val gateway2 = FakeGateway().apply { acceptShow = true }
        c = GameEndAdController(prefs, gateway2) { now }
        assertTrue(c.onCompletedRound { finished++ })
        gateway2.deliverShownThenDismiss()
        assertEquals(1, finished)
    }

    @Test
    fun `shown resets persisted count and timestamp`() {
        val gateway = FakeGateway()
        val c = controller(gateway)
        now += 200_000L
        repeat(2) { c.onCompletedRound {} }
        var finished = 0
        assertTrue(c.onCompletedRound { finished++ })
        val before = now
        gateway.deliverShownThenDismiss()
        assertEquals(1, finished)
        assertEquals(0, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
        assertEquals(before, prefs.getLong(GameEndAdController.KEY_LAST_FULLSCREEN_AT, -1))
    }

    @Test
    fun `failure finishes once and never resets count`() {
        val gateway = FakeGateway()
        val c = controller(gateway)
        now += 200_000L
        repeat(2) { c.onCompletedRound {} }
        var finished = 0
        assertTrue(c.onCompletedRound { finished++ })
        gateway.deliverFailure()
        gateway.deliverFailure()
        assertEquals(1, finished)
        assertEquals(3, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
    }

    @Test
    fun `reward fullscreen resets count`() {
        val gateway = FakeGateway()
        val c = controller(gateway)
        c.onCompletedRound {}
        assertEquals(1, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
        now += 10_000L
        c.noteFullscreenShown()
        assertEquals(0, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
        assertEquals(now, prefs.getLong(GameEndAdController.KEY_LAST_FULLSCREEN_AT, -1))
    }

    @Test
    fun `repeated completion while pending is ignored`() {
        val gateway = FakeGateway()
        val c = controller(gateway)
        now += 200_000L
        repeat(2) { c.onCompletedRound {} }
        var finished = 0
        assertTrue(c.onCompletedRound { finished++ })
        val showsBefore = gateway.showCalls
        // Repeated completions while presentation pending: ignored, not counted.
        assertFalse(c.onCompletedRound { finished++ })
        assertFalse(c.onCompletedRound { finished++ })
        assertEquals(showsBefore, gateway.showCalls)
        assertEquals(0, finished)
        gateway.deliverShownThenDismiss()
        assertEquals(1, finished)
    }

    @Test
    fun `dispose disposes gateway once and finishes pending once`() {
        val gateway = FakeGateway()
        val c = controller(gateway)
        now += 200_000L
        repeat(2) { c.onCompletedRound {} }
        var finished = 0
        assertTrue(c.onCompletedRound { finished++ })
        c.dispose()
        assertEquals(1, finished)
        assertEquals(1, gateway.disposeCount)
        c.dispose()
        assertEquals(1, gateway.disposeCount)
        assertEquals(1, finished)
        // After dispose, no further attempts.
        assertFalse(c.onCompletedRound { finished++ })
        assertEquals(1, finished)
    }

    @Test
    fun `synchronous show failure releases pending and permits retry`() {
        val gateway = FakeGateway().apply { finishDuringShow = true }
        val c = controller(gateway)
        now += 200_000L
        repeat(2) { c.onCompletedRound {} }
        var finished = 0
        assertTrue(c.onCompletedRound { finished++ })
        assertEquals(1, finished)
        assertEquals(3, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
        gateway.finishDuringShow = false
        assertTrue(c.onCompletedRound { finished++ })
        gateway.deliverShownThenDismiss()
        assertEquals(2, finished)
    }

    @Test
    fun `dispose finishes when gateway omits callback and ignores late callbacks`() {
        val gateway = FakeGateway().apply { callbackOnDispose = false }
        val c = controller(gateway)
        now += 200_000L
        repeat(2) { c.onCompletedRound {} }
        var finished = 0
        assertTrue(c.onCompletedRound { finished++ })
        val lateShown = gateway.pendingShown!!
        val lateFinished = gateway.pendingFinished!!
        c.dispose()
        lateShown()
        lateFinished()
        assertEquals(1, finished)
        assertEquals(3, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
    }

    @Test
    fun `dispose finishes pending even when gateway throws`() {
        val gateway = FakeGateway().apply {
            callbackOnDispose = false
            throwOnDispose = true
        }
        val c = controller(gateway)
        now += 200_000L
        repeat(2) { c.onCompletedRound {} }
        var finished = 0
        assertTrue(c.onCompletedRound { finished++ })
        c.dispose()
        assertEquals(1, finished)
        assertEquals(1, gateway.disposeCount)
    }

    @Test
    fun `old callbacks cannot affect a newer presentation`() {
        val gateway = FakeGateway()
        val c = controller(gateway)
        now += 200_000L
        repeat(2) { c.onCompletedRound {} }
        var finished = 0
        assertTrue(c.onCompletedRound { finished++ })
        val oldShown = gateway.pendingShown!!
        val oldFinished = gateway.pendingFinished!!
        gateway.deliverFailure()
        assertEquals(1, finished)
        assertTrue(c.onCompletedRound { finished++ })
        oldShown()
        oldFinished()
        assertEquals(1, finished)
        assertEquals(3, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
        gateway.deliverShownThenDismiss()
        assertEquals(2, finished)
        assertEquals(0, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
    }

    @Test
    fun `repeated shown callback does not reset later rounds`() {
        val gateway = FakeGateway()
        val c = controller(gateway)
        now += 200_000L
        repeat(2) { c.onCompletedRound {} }
        assertTrue(c.onCompletedRound {})
        val shown = gateway.pendingShown!!
        shown()
        gateway.deliverShownThenDismiss()
        c.onCompletedRound {}
        now += 1000L
        shown()
        assertEquals(1, prefs.getInt(GameEndAdController.KEY_COMPLETED_ROUNDS, -1))
        assertEquals(now - 1000L, prefs.getLong(GameEndAdController.KEY_LAST_FULLSCREEN_AT, -1))
    }
}
