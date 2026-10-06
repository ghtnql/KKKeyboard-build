package com.ghtnql.kkkeyboard

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Insets
import android.os.Looper
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.ScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-rKR-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityFlowTest {
    @Test fun shortSentencePracticeHighlightsTypingAndAdvancesAfterMistake() {
        assumeTrue(BuildConfig.EXPOSE_GAMES)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            button(activity.window.decorView, "연습 시작").performClick()
            button(activity.window.decorView, "짧은 문장 입력").performClick()

            assertEquals("창작 문장", activity.findViewById<TextView>(R.id.practice_style).text)
            val firstPrompt = activity.findViewById<TextView>(R.id.practice_prompt)
            val firstSentence = firstPrompt.text.toString()
            val answer = activity.findViewById<EditText>(R.id.practice_answer)
            assertFalse(answer.isSingleLine)
            answer.setText(firstSentence.take(2))
            val styled = firstPrompt.text as Spanned
            assertTrue(styled.getSpans(0, styled.length, ForegroundColorSpan::class.java).isNotEmpty())
            val scroll = answer.parent.parent as ScrollView
            scroll.dispatchApplyWindowInsets(
                WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 24, 0, 32))
                    .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 320))
                    .build(),
            )
            val decor = activity.window.decorView
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, 360, 800)
            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val out = File("build/reports/app/sentence-360-ime.png")
            out.parentFile?.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertEquals(320, scroll.paddingBottom)

            answer.setText(firstSentence.dropLast(1) + "X")
            button(activity.window.decorView, "확인").performClick()
            assertEquals("오타를 기록하고 다음 문장으로 넘어갑니다", activity.findViewById<TextView>(R.id.practice_feedback).text)
            assertFalse(firstSentence == activity.findViewById<TextView>(R.id.practice_prompt).text.toString())

            var answeredAfterTypo = 0
            while (activity.findViewById<TextView>(R.id.result_accuracy) == null) {
                assertTrue("practice session did not finish", answeredAfterTypo < 500)
                val prompt = activity.findViewById<TextView>(R.id.practice_prompt).text.toString()
                activity.findViewById<EditText>(R.id.practice_answer).setText(prompt)
                button(activity.window.decorView, "확인").performClick()
                answeredAfterTypo++
            }

            assertFalse(activity.findViewById<TextView>(R.id.result_accuracy).text == "정확도 100%")
            assertEquals(1, LearningProgressStore.read(activity).totalSessions)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun completesKoreanPracticeAndStoresResult() {
        assumeTrue(BuildConfig.EXPOSE_GAMES)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            assertNull(activity.findViewById<EditText>(R.id.keyboard_test_field))
            button(activity.window.decorView, "연습 시작").performClick()
            button(activity.window.decorView, "한국어 보고 한글 입력").performClick()

            var answered = 0
            while (activity.findViewById<TextView>(R.id.result_accuracy) == null) {
                assertTrue("practice session did not finish", answered < 500)
                val prompt = activity.findViewById<TextView>(R.id.practice_prompt).text.toString()
                activity.findViewById<EditText>(R.id.practice_answer).setText(prompt)
                button(activity.window.decorView, "확인").performClick()
                answered++
            }

            assertEquals("정확도 100%", activity.findViewById<TextView>(R.id.result_accuracy).text)
            assertEquals(1, LearningProgressStore.read(activity).totalSessions)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun rainScreenHasArenaInputAndHud() {
        assumeTrue(BuildConfig.EXPOSE_GAMES)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            button(activity.window.decorView, "Rain 타자게임").performClick()
            assertNotNull(button(activity.window.decorView, "한국어 보고 한글 입력"))
            button(activity.window.decorView, "일본어 보고 한글 발음 입력").performClick()
            assertNotNull(activity.findViewById<View>(R.id.rain_arena))
            assertNotNull(activity.findViewById<EditText>(R.id.rain_answer))
            assertNotNull(activity.findViewById<TextView>(R.id.rain_hud))
            assertNotNull(activity.findViewById<View>(R.id.rain_submit))
            assertNotNull(activity.findViewById<View>(R.id.rain_mute))
            assertNotNull(activity.findViewById<View>(R.id.rain_pause))

            val decor = activity.window.decorView
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, 360, 800)
            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val out = File("build/reports/app/rain-360.png")
            out.parentFile?.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(bitmap.getPixel(180, 320) != bitmap.getPixel(180, 700))

            val arena = activity.findViewById<RainArenaView>(R.id.rain_arena)
            arena.updateTargets(
                listOf(
                    RainTarget(
                        instanceId = 99L,
                        item = LearningItem(
                            id = "preview",
                            category = "greeting",
                            difficulty = 1,
                            sourceLanguage = "ja",
                            sourceText = "ありがとう",
                            targetLanguage = "ja",
                            acceptedAnswers = listOf("아리가토"),
                            meaningHint = "고마워",
                            enabledModes = setOf(PracticeMode.JAPANESE_TO_HANGUL.persistedValue),
                            gameTypes = setOf("rain"),
                        ),
                        x = 0.5f,
                        y = 0.34f,
                    ),
                ),
                typed = "아",
            )
            val gameRoot = activity.findViewById<EditText>(R.id.rain_answer).parent.parent as View
            gameRoot.dispatchApplyWindowInsets(
                WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 24, 0, 32))
                    .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 320))
                    .build(),
            )
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, 360, 800)
            val imeBitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(imeBitmap))
            val imeOut = File("build/reports/app/rain-360-ime.png")
            imeOut.outputStream().use { imeBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val answerRow = activity.findViewById<EditText>(R.id.rain_answer).parent as View
            assertEquals(320, gameRoot.paddingBottom)
            assertEquals(480, answerRow.bottom)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun cafeScreenKeepsNotebookAboveImeAndPreservesAcceptedWriting() {
        assumeTrue(BuildConfig.EXPOSE_GAMES)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            button(activity.window.decorView, "한글 카페").performClick()
            button(activity.window.decorView, "한국어 보고 한글 입력").performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(750))

            val scene = activity.findViewById<CafeSceneView>(R.id.cafe_scene)
            val order = activity.findViewById<TextView>(R.id.cafe_order)
            val score = activity.findViewById<TextView>(R.id.cafe_score)
            val answer = activity.findViewById<EditText>(R.id.cafe_answer)
            val submit = activity.findViewById<View>(R.id.cafe_submit)
            assertNotNull(scene)
            assertEquals("커피", order.text.toString())
            assertEquals("점수 0", score.text.toString())
            assertNotNull(submit)
            answer.setText("커")

            val gameRoot = answer.parent.parent as View
            gameRoot.dispatchApplyWindowInsets(
                WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 24, 0, 32))
                    .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 320))
                    .build(),
            )
            val decor = activity.window.decorView
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, 360, 800)
            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val out = File("build/reports/app/cafe-360-ime.png")
            out.parentFile?.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // The customer's full region must stay pixel-identical as the IME changes height.
            val customerPixels = IntArray(140 * 160)
            bitmap.getPixels(customerPixels, 0, 140, 30, 130, 140, 160)

            val answerRow = answer.parent as View
            assertEquals(320, gameRoot.paddingBottom)
            assertEquals(480, answerRow.bottom)
            for (imeHeight in listOf(0, 400)) {
                gameRoot.dispatchApplyWindowInsets(
                    WindowInsets.Builder()
                        .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 24, 0, 32))
                        .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, imeHeight))
                        .build(),
                )
                decor.measure(
                    View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                )
                decor.layout(0, 0, 360, 800)
                decor.draw(Canvas(bitmap))
                val resizedCustomerPixels = IntArray(customerPixels.size)
                bitmap.getPixels(resizedCustomerPixels, 0, 140, 30, 130, 140, 160)
                assertTrue("Customer moved when IME height became $imeHeight", customerPixels.contentEquals(resizedCustomerPixels))
                val variant = File("build/reports/app/cafe-360-ime-$imeHeight.png")
                variant.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertEquals(800 - maxOf(32, imeHeight), answerRow.bottom)
            }
            answer.setText("커피")
            decor.draw(Canvas(bitmap))
            val sceneLocation = IntArray(2)
            scene.getLocationOnScreen(sceneLocation)
            val writingTop = sceneLocation[1] + scene.height - 108 + 32
            val acceptedWriting = IntArray(100 * 44)
            bitmap.getPixels(acceptedWriting, 0, 100, 220, writingTop, 100, 44)
            submit.performClick()
            assertEquals("접수 완료!", order.text.toString())
            assertEquals("", answer.text.toString())
            assertFalse(submit.isEnabled)
            assertTrue(answer.hasFocus())
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, 360, 800)
            decor.draw(Canvas(bitmap))
            val clearedInputWriting = IntArray(acceptedWriting.size)
            bitmap.getPixels(clearedInputWriting, 0, 100, 220, writingTop, 100, 44)
            assertTrue("Accepted writing disappeared when the input cleared", acceptedWriting.contentEquals(clearedInputWriting))
            File("build/reports/app/cafe-notebook-accepted.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun rendersHomeAtPhoneWidth() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            val decor = activity.window.decorView
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, 360, 800)
            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val out = File("build/reports/app/home-360.png")
            out.parentFile?.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(bitmap.width == 360 && bitmap.height == 800)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun settingsScrollsToTheLastContentAndRendersCleanly() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            activity.findViewById<Button>(R.id.open_app_settings).performClick()
            val testField = activity.findViewById<EditText>(R.id.keyboard_test_field)
            assertNotNull(testField)
            assertFalse(testField.hasFocus())
            val decor = activity.window.decorView
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            )
            decor.layout(0, 0, 360, 800)
            decor.viewTreeObserver.dispatchOnPreDraw()
            val scroll = activity.findViewById<ScrollView>(R.id.settings_scroll)
            val topBitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(topBitmap))
            val topOut = File("build/reports/app/settings-360-top.png")
            topOut.parentFile?.mkdirs()
            topOut.outputStream().use { topBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            scroll.fullScroll(View.FOCUS_DOWN)
            assertTrue(scroll.scrollY > 0)
            val child = scroll.getChildAt(0)
            assertTrue(child.bottom <= scroll.scrollY + scroll.height + scroll.paddingBottom)

            val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(bitmap))
            val out = File("build/reports/app/settings-360-bottom.png")
            out.parentFile?.mkdirs()
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun settingsCanAddEditAndDeleteCustomPhrase() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        UserPhraseStore.clear(activity)
        UserPhraseStore.read(activity).forEach { UserPhraseStore.delete(activity, it.id) }
        try {
            activity.findViewById<Button>(R.id.open_app_settings).performClick()
            assertNotNull(activity.findViewById<View>(R.id.phrase_list))
            activity.findViewById<Button>(R.id.phrase_add).performClick()

            var dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.findViewById<EditText>(R.id.phrase_title_input).setText("인사")
            dialog.findViewById<EditText>(R.id.phrase_content_input).setText("안녕하세요!")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals("안녕하세요!", UserPhraseStore.read(activity).single().content)

            button(activity.window.decorView, "수정").performClick()
            dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.findViewById<EditText>(R.id.phrase_title_input).setText("업무 인사")
            dialog.findViewById<EditText>(R.id.phrase_content_input).setText("안녕하세요. 감사합니다.")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals("업무 인사", UserPhraseStore.read(activity).single().title)

            button(activity.window.decorView, "삭제").performClick()
            ShadowAlertDialog.getLatestAlertDialog()
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .performClick()
            assertTrue(UserPhraseStore.read(activity).isEmpty())
        } finally {
            UserPhraseStore.clear(activity)
            controller.pause().stop().destroy()
        }
    }

    private fun button(root: View, text: String): Button = buttons(root).first { it.text.toString() == text }

    private fun buttons(view: View): List<Button> {
        if (view is Button) return listOf(view)
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
    }
}
