package com.ghtnql.kkkeyboard

import android.app.Application
import android.content.Context
import com.ghtnql.kkkeyboard.sharedui.GameMusicTrack
import com.ghtnql.kkkeyboard.sharedui.GameSoundEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameAudioControllerTest {

    private lateinit var app: Application

    private class FakeEngine : GameAudioEngine {
        val events = mutableListOf<String>()
        var startMusicCount = 0
        var lastTrack: GameMusicTrack? = null
        var playCount = 0
        var releaseCount = 0

        override fun startMusic(track: GameMusicTrack) {
            startMusicCount++
            lastTrack = track
            events.add("startMusic:$track")
        }

        override fun stopMusic() {
            events.add("stopMusic")
        }

        override fun play(effect: GameSoundEffect) {
            playCount++
            events.add("play:$effect")
        }

        override fun stopEffects() {
            events.add("stopEffects")
        }

        override fun release() {
            releaseCount++
            events.add("release")
        }
    }

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("game_audio", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("rain_audio", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun independentPreferencesPersistAcrossRecreatedController() {
        val fake = FakeEngine()
        val first = GameAudioController(app, fake)
        first.setMusicEnabled(false)
        first.setEffectsEnabled(true)

        val second = GameAudioController(app, fake)
        val settings = second.readSettings()
        assertFalse(settings.musicEnabled)
        assertTrue(settings.effectsEnabled)
    }

    @Test
    fun legacyMutedLeavesUntouchedEffectOffWhenMusicOn() {
        app.getSharedPreferences("rain_audio", Context.MODE_PRIVATE)
            .edit().putBoolean("muted", true).commit()
        val fake = FakeEngine()
        val controller = GameAudioController(app, fake)
        controller.setMusicEnabled(true)

        val settings = controller.readSettings()
        assertTrue(settings.musicEnabled)
        assertFalse(settings.effectsEnabled)

        controller.start(GameMusicTrack.PRACTICE)
        assertEquals(1, fake.startMusicCount)
        controller.play(GameSoundEffect.HIT)
        assertEquals(0, fake.playCount)
    }

    @Test
    fun effectsOnlyHitWorks() {
        val fake = FakeEngine()
        val controller = GameAudioController(app, fake)
        controller.setMusicEnabled(false)
        controller.setEffectsEnabled(true)

        controller.start(GameMusicTrack.RAIN)
        assertEquals(0, fake.startMusicCount)
        controller.play(GameSoundEffect.HIT)
        assertEquals(1, fake.playCount)
        assertEquals("play:${GameSoundEffect.HIT}", fake.events.single { it.startsWith("play:") })
    }

    @Test
    fun fxMuteSuppressesSfxAndStopsOngoingWhileMusicRemains() {
        val fake = FakeEngine()
        val controller = GameAudioController(app, fake)
        controller.start(GameMusicTrack.CAFE)
        controller.play(GameSoundEffect.HIT)
        assertEquals(1, fake.playCount)

        fake.events.clear()
        controller.setEffectsEnabled(false)

        assertTrue(fake.events.contains("stopEffects"))
        assertFalse(fake.events.any { it.startsWith("play:") })
        controller.play(GameSoundEffect.HIT)
        assertEquals(1, fake.playCount)
        assertTrue(controller.readSettings().musicEnabled)
        assertEquals(1, fake.startMusicCount)
    }

    @Test
    fun duplicateSameTrackStartOnlyOnce() {
        val fake = FakeEngine()
        val controller = GameAudioController(app, fake)
        controller.start(GameMusicTrack.PRACTICE)
        controller.start(GameMusicTrack.PRACTICE)
        assertEquals(1, fake.startMusicCount)
        assertEquals(GameMusicTrack.PRACTICE, fake.lastTrack)
    }

    @Test
    fun pauseCancelsBothBlocksEffectsThenResumeRestartsTrack() {
        val fake = FakeEngine()
        val controller = GameAudioController(app, fake)
        controller.start(GameMusicTrack.RAIN)
        controller.play(GameSoundEffect.HIT)
        fake.events.clear()

        controller.onPause()
        assertTrue(fake.events.contains("stopMusic"))
        assertTrue(fake.events.contains("stopEffects"))

        fake.events.clear()
        controller.play(GameSoundEffect.HIT)
        assertEquals(1, fake.playCount)

        controller.onResume()
        assertEquals(2, fake.startMusicCount)
        assertEquals(GameMusicTrack.RAIN, fake.lastTrack)
    }

    @Test
    fun stopPreventsLaterResume() {
        val fake = FakeEngine()
        val controller = GameAudioController(app, fake)
        controller.start(GameMusicTrack.CAFE)
        controller.stop()
        fake.events.clear()
        controller.onResume()
        assertEquals(1, fake.startMusicCount)
        assertTrue(fake.events.none { it.startsWith("startMusic") })
    }

    @Test
    fun finishStopsBothPlaysFinishInOrderOnceAndCannotResumeMusic() {
        val fake = FakeEngine()
        val controller = GameAudioController(app, fake)
        controller.start(GameMusicTrack.PRACTICE)
        controller.play(GameSoundEffect.START)
        fake.events.clear()

        controller.finish()
        assertEquals(
            listOf("stopMusic", "stopEffects", "play:${GameSoundEffect.FINISH}"),
            fake.events
        )

        fake.events.clear()
        controller.finish()
        assertTrue(fake.events.none { it == "play:${GameSoundEffect.FINISH}" })
        fake.events.clear()
        controller.onResume()
        assertTrue(fake.events.none { it.startsWith("startMusic") })
    }

    @Test
    fun releaseOnceAndIgnoresFuturePlayStart() {
        val fake = FakeEngine()
        val controller = GameAudioController(app, fake)
        controller.start(GameMusicTrack.PRACTICE)
        val startsBefore = fake.startMusicCount
        val playsBefore = fake.playCount

        controller.release()
        controller.release()
        assertEquals(1, fake.releaseCount)

        fake.events.clear()
        controller.play(GameSoundEffect.HIT)
        controller.start(GameMusicTrack.RAIN)
        assertEquals(startsBefore, fake.startMusicCount)
        assertEquals(playsBefore, fake.playCount)
        assertTrue(fake.events.none { it.startsWith("startMusic") || it.startsWith("play:") })
    }
}
