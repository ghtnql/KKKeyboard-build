package com.ghtnql.kkkeyboard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Insets
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-rKR-w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CafeAnimationFlowTest {
    @Test fun rushDifficultyDrainsPatienceAndHidesOrderAfterMemoryWindow() {
        assumeTrue(BuildConfig.EXPOSE_GAMES)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            buttons(activity.window.decorView).first { it.text == "한글 카페" }.performClick()
            buttons(activity.window.decorView).first { it.text == "러시아워" }.performClick()
            buttons(activity.window.decorView).first { it.text == "한국어 보고 한글 입력" }.performClick()
            advance(750)

            val patience = activity.findViewById<ProgressBar>(R.id.cafe_patience)
            val difficulty = activity.findViewById<TextView>(R.id.cafe_difficulty)
            val order = activity.findViewById<TextView>(R.id.cafe_order)
            assertEquals("러시아워", difficulty.text.toString())
            assertTrue(patience.progress in 1 until patience.max)
            assertEquals("•••", order.text.toString())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun customersEnterReactLeaveAndCompleteMixedRound() {
        assumeTrue(BuildConfig.EXPOSE_GAMES)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            startCafe(activity)
            val scene = activity.findViewById<CafeSceneView>(R.id.cafe_scene)
            val answer = activity.findViewById<EditText>(R.id.cafe_answer)
            val submit = activity.findViewById<View>(R.id.cafe_submit)
            val order = activity.findViewById<TextView>(R.id.cafe_order)
            val decor = activity.window.decorView
            val gameRoot = answer.parent.parent as View
            gameRoot.dispatchApplyWindowInsets(WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 24, 0, 32))
                .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 320))
                .build())

            val sprites = BitmapFactory.decodeResource(activity.resources, R.drawable.cafe_customers)
            assertEquals("Atlas corner must be transparent", 0, Color.alpha(sprites.getPixel(0, 0)))
            assertTrue("Character must not be translucent", Color.alpha(sprites.getPixel(sprites.width / 6, sprites.height / 4)) > 240)

            val folder = File("build/reports/app").apply { mkdirs() }
            val frames = File(folder, "cafe-animation-frames").apply { mkdirs() }
            frames.listFiles()?.filter { it.name.matches(Regex("frame-\\d{4}\\.png")) }?.forEach { it.delete() }
            var frameIndex = 0
            fun capture(name: String? = null): Bitmap {
                decor.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                decor.layout(0, 0, 360, 800)
                val bitmap = Bitmap.createBitmap(360, 480, Bitmap.Config.ARGB_8888)
                decor.draw(Canvas(bitmap))
                val bytes = ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    stream.toByteArray()
                }
                File(frames, "frame-%04d.png".format(frameIndex++)).outputStream().use { it.write(bytes) }
                if (name != null) File(folder, "$name.png").outputStream().use { it.write(bytes) }
                return bitmap
            }
            fun advanceFrames(count: Int) {
                repeat(count) {
                    advance(50)
                    capture()
                }
            }

            assertEquals(CafeCustomerAnimation.Phase.ENTERING, scene.customerPhase)
            assertFalse(submit.isEnabled)
            val arrival = capture("cafe-customer-arriving")
            advanceFrames(15)
            assertEquals(CafeCustomerAnimation.Phase.WAITING, scene.customerPhase)
            assertTrue(submit.isEnabled)
            assertFalse("Customer did not visibly enter", arrival.sameAs(capture("cafe-customer-waiting")))
            answer.setText("커")
            advanceFrames(6)
            answer.setText("커피")
            advanceFrames(6)
            submit.performClick()
            assertEquals(CafeCustomerAnimation.Phase.HAPPY, scene.customerPhase)
            capture("cafe-customer-happy")
            assertFalse(submit.isEnabled)
            assertTrue(answer.hasFocus())
            answer.append("잘못된 중복 입력")
            submit.performClick()
            assertEquals("", answer.text.toString())
            advanceFrames(12)
            advanceFrames(14)
            capture("cafe-customer-leaving-happy")
            advanceFrames(24)
            assertEquals(CafeCustomerAnimation.Phase.WAITING, scene.customerPhase)
            assertEquals(1, scene.customerIndex)
            assertEquals("우유", order.text.toString())
            capture("cafe-next-customer")
            answer.setText("커피")
            advanceFrames(8)
            submit.performClick()
            assertEquals(CafeCustomerAnimation.Phase.DISAPPOINTED, scene.customerPhase)
            capture("cafe-customer-disappointed")
            advanceFrames(12)
            advanceFrames(14)
            capture("cafe-customer-leaving-sad")
            advanceFrames(24)
            assertEquals(CafeCustomerAnimation.Phase.WAITING, scene.customerPhase)
            assertEquals(2, scene.customerIndex)
            assertEquals("딸기우유", order.text.toString())

            repeat(3) { index ->
                answer.setText(if (index == 2) "오답" else order.text.toString())
                submit.performClick()
                advance(2_600)
            }
            assertNotNull(activity.findViewById<TextView>(R.id.result_accuracy))
            assertEquals(1, LearningProgressStore.read(activity).totalSessions)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun doesNotAdvanceAfterLeavingGame() {
        assumeTrue(BuildConfig.EXPOSE_GAMES)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            startCafe(activity)
            val scene = activity.findViewById<CafeSceneView>(R.id.cafe_scene)
            assertEquals(CafeCustomerAnimation.Phase.ENTERING, scene.customerPhase)
            activity.onBackPressed()
            advance(4_000)
            assertEquals(CafeCustomerAnimation.Phase.GONE, scene.customerPhase)
            assertNull(activity.findViewById<View>(R.id.cafe_scene))
            assertNotNull(activity.findViewById<View>(R.id.open_app_settings))
            assertEquals(0, LearningProgressStore.read(activity).totalSessions)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun startCafe(activity: MainActivity) {
        buttons(activity.window.decorView).first { it.text == "한글 카페" }.performClick()
        buttons(activity.window.decorView).first { it.text == "한국어 보고 한글 입력" }.performClick()
    }

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }

}
