package com.ghtnql.kkkeyboard

import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.ComposeView
import org.robolectric.RobolectricTestRunner
import com.ghtnql.kkkeyboard.sharedui.AppPlatform
import com.ghtnql.kkkeyboard.sharedui.GameAudioSettings
import com.ghtnql.kkkeyboard.sharedui.KKKeyboardApp
import com.ghtnql.kkkeyboard.sharedui.KeyboardStatus
import com.ghtnql.kkkeyboard.sharedui.LearningProgress
import com.ghtnql.kkkeyboard.sharedui.PracticeMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.ghtnql.kkkeyboard.sharedui.GameMusicTrack
import com.ghtnql.kkkeyboard.sharedui.GameSoundEffect
import androidx.compose.ui.test.*
import org.robolectric.util.ReflectionHelpers
import androidx.compose.ui.test.junit4.createAndroidComposeRule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-rKR-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeGameAudioTest {

    @get:Rule
    val ui = createAndroidComposeRule<ComposeMainActivity>()

    private val events = mutableListOf<String>()
    private var settings = GameAudioSettings()

    @Before
    fun setUp() {
        val provider = Class.forName("org.jetbrains.compose.resources.AndroidContextProvider")
            .getDeclaredConstructor().newInstance() as android.content.ContentProvider
        provider.attachInfo(
            ui.activity,
            ProviderInfo().apply {
                authority = "${ui.activity.packageName}.resources.AndroidContextProvider"
            }
        )
    }

    private fun installProbe() {
        val delegate = ReflectionHelpers.getField<AppPlatform>(ui.activity, "platform")
        settings = GameAudioSettings()
        val fixture = delegate.learningItems.first().copy(
            sourceLanguage = "ko",
            sourceText = "테스트",
            acceptedAnswers = listOf("테스트"),
            gameTypes = listOf("typing", "sentence", "convert", "rain", "cafe"),
            enabledModes = PracticeMode.entries.map { it.id },
            japaneseText = "テスト",
            japaneseHangulPronunciation = "테스토",
            koreanText = "테스트"
        )
        val platform = object : AppPlatform by delegate {
            override val keyboardStatus: KeyboardStatus = KeyboardStatus.ACTIVE
            override val learningItems = listOf(fixture)
            override val learningProgress = LearningProgress()
            override fun saveLearningProgress(progress: LearningProgress) = Unit
            override fun prepareGameEndAd() = Unit
            override fun requestGameEndAd(onFinished: () -> Unit): Boolean {
                events.add("ad")
                return false
            }
            override fun readGameAudioSettings(): GameAudioSettings = settings
            override fun setGameMusicEnabled(enabled: Boolean) {
                settings = settings.copy(musicEnabled = enabled)
            }
            override fun setGameEffectsEnabled(enabled: Boolean) {
                settings = settings.copy(effectsEnabled = enabled)
            }
            override fun startGameAudio(track: GameMusicTrack) {
                events.add("track:$track")
            }
            override fun playGameSound(effect: GameSoundEffect) {
                events.add("effect:$effect")
            }
            override fun stopGameAudio() {
                events.add("stop")
            }
            override fun finishGameAudio() {
                events.add("finish")
            }
        }
        ui.runOnUiThread {
            ui.activity.setContentView(
                ComposeView(ui.activity).apply {
                    setContent { KKKeyboardApp(platform) }
                }
            )
        }
        ui.waitForIdle()

    }

    private fun start(name: String) {
        ui.onNodeWithText(if (name == "기본 연습") "단어 연습" else name).performScrollTo().performClick()
        ui.mainClock.advanceTimeBy(100)
        ui.waitForIdle()
        ui.onNodeWithText("시작").performClick()
        ui.mainClock.advanceTimeBy(160)
        ui.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    private fun snapshot(name: String, dialog: Boolean = false) {
        ui.waitForIdle()
        ui.runOnUiThread {
            val view = if (dialog) {
                val global = ReflectionHelpers.callStaticMethod<Any>(Class.forName("android.view.WindowManagerGlobal"), "getInstance")
                ReflectionHelpers.getField<List<View>>(global, "mViews").last()
            } else ui.activity.window.decorView
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val out = File("build/reports/game-audio/$name.png")
            out.parentFile?.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test
    fun hangulLearningUsesPracticeMusicAndUiEffects() {
        installProbe()
        events.clear()

        ui.onNodeWithText("한글학습").performScrollTo().performClick()
        ui.waitForIdle()
        assertEquals("track:PRACTICE", events.last { it.startsWith("track:") })
        assertTrue(events.contains("effect:START"))

        ui.onAllNodesWithTag("hiragana-guide-close").fetchSemanticsNodes().firstOrNull()?.let {
            ui.onNodeWithTag("hiragana-guide-close").performClick()
            ui.waitForIdle()
        }
        val hitsBefore = events.count { it == "effect:HIT" }
        ui.onNodeWithText("글자 만들기").performScrollTo().performClick()
        ui.waitForIdle()
        assertTrue(events.count { it == "effect:HIT" } > hitsBefore)

        val stopsBefore = events.count { it == "stop" }
        ui.onNodeWithText("닫기").performClick()
        ui.waitForIdle()
        assertTrue(events.count { it == "stop" } > stopsBefore)
    }

    @Test
    fun basicAudio() {
        installProbe()
        start("기본 연습")
        assertTrue(events.contains("track:PRACTICE"))
        assertTrue(events.contains("effect:START"))
        snapshot("practice-playing")

        // Blank submission before typing should not add HIT/ERROR.
        val hitsBefore = events.count { it == "effect:HIT" }
        val errorsBefore = events.count { it == "effect:ERROR" }
        ui.onNode(hasSetTextAction()).performImeAction()
        ui.waitForIdle()
        assertEquals(hitsBefore, events.count { it == "effect:HIT" })
        assertEquals(errorsBefore, events.count { it == "effect:ERROR" })

        ui.onNode(hasSetTextAction()).performTextInput("테스트")
        ui.onNode(hasSetTextAction()).performImeAction()
        ui.mainClock.advanceTimeBy(100)
        ui.waitForIdle()

        assertTrue(events.contains("effect:HIT"))
        val finishIndex = events.indexOf("finish")
        val adIndex = events.indexOf("ad")
        assertTrue(finishIndex >= 0)
        assertTrue(finishIndex < adIndex)
        assertEquals(1, events.count { it == "finish" })
        ui.onNodeWithText("다시 하기").assertExists()
        snapshot("practice-result")
    }

    @Test
    fun soundDialog() {
        installProbe()
        start("기본 연습")
        ui.onNodeWithContentDescription("게임 소리 설정").performClick()
        ui.waitForIdle()

        ui.onNodeWithContentDescription("게임 배경음악").performClick()
        ui.waitForIdle()
        assertEquals(false, settings.musicEnabled)
        assertEquals(true, settings.effectsEnabled)

        ui.onNodeWithContentDescription("게임 효과음").performClick()
        ui.waitForIdle()
        ui.onNodeWithContentDescription("게임 배경음악").performClick()
        ui.waitForIdle()
        assertEquals(true, settings.musicEnabled)
        assertEquals(false, settings.effectsEnabled)

        snapshot("sound-dialog", dialog = true)
        ui.onNodeWithText("닫기").performClick()
        ui.waitForIdle()
        snapshot("game-with-sound")

        ui.onNode(hasSetTextAction()).assertIsDisplayed()
        ui.onNodeWithContentDescription("뒤로").assertIsDisplayed()
        ui.onNodeWithContentDescription("게임 소리 설정").performClick()
        ui.onNodeWithContentDescription("게임 배경음악").assertIsOn()
        ui.onNodeWithContentDescription("게임 효과음").assertIsOff()
        ui.onNodeWithText("닫기").performClick()
        ui.onNodeWithContentDescription("뒤로").performClick()
        ui.onNodeWithText("시작").assertExists()
    }

    @Test fun rainAndCafeTracksAndManualExitAndBackStop() {
        installProbe()
        listOf(Triple("한글비", "RAIN", "게임 끝내기"), Triple("한글카페", "CAFE", "카페 나가기")).forEach { (name, track, exit) ->
            start(name)
            assertEquals("track:$track", events.last { it.startsWith("track:") })
            snapshot("${track.lowercase()}-playing")
            val stops = events.count { it == "stop" }
            ui.onNodeWithText(exit).performClick()
            ui.mainClock.advanceTimeBy(100)
            ui.onNodeWithText("다시 하기").assertExists()
            assertTrue(events.count { it == "stop" } > stops)
            ui.onNodeWithContentDescription("뒤로").performClick()
            ui.onNodeWithContentDescription("뒤로").performClick()
            start(name)
            val beforeBack = events.count { it == "stop" }
            ui.onNodeWithContentDescription("뒤로").assertIsDisplayed().performClick()
            assertTrue(events.count { it == "stop" } > beforeBack)
            ui.onNodeWithText("시작").assertExists()
            ui.onNodeWithContentDescription("뒤로").performClick()
        }
        assertEquals(0, events.count { it == "finish" || it == "ad" })
    }

    @Test fun mainSettingsPersistIntoGameplayDialog() {
        installProbe()
        ui.onNodeWithContentDescription("설정").performClick()
        ui.onNodeWithContentDescription("게임 배경음악").performScrollTo().assertIsOn().performClick()
        assertEquals(GameAudioSettings(false, true), settings)
        ui.onNodeWithContentDescription("게임 효과음").performScrollTo().assertIsOn().performClick()
        assertEquals(GameAudioSettings(false, false), settings)
        snapshot("main-settings-sound-off")
        ui.onNodeWithContentDescription("뒤로").assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("설정").performClick()
        ui.onNodeWithContentDescription("게임 배경음악").performScrollTo().assertIsOff()
        ui.onNodeWithContentDescription("게임 효과음").performScrollTo().assertIsOff().performClick()
        assertEquals(GameAudioSettings(false, true), settings)
        snapshot("main-settings-effects-only")
        ui.onNodeWithContentDescription("뒤로").assertIsDisplayed().performClick()
        start("한글카페")
        ui.onNodeWithContentDescription("게임 소리 설정").performClick()
        ui.onNodeWithContentDescription("게임 배경음악").assertIsOff()
        ui.onNodeWithContentDescription("게임 효과음").assertIsOn()
        snapshot("cafe-persisted-dialog", dialog = true)
    }
}
