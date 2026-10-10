package com.ghtnql.kkkeyboard

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Intent
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File

/** Captures the production IME renderer; previews never bake in imaginary key rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-rKR-w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecoveryRenderingTest {
    @Test fun captureAvailableThemesAndVerifySettingsRoute() {
        var baseHeight = 0
        for (id in listOf("basic_light", "basic_dark", "seoul_day", "seoul_night")) {
            val controller = Robolectric.buildService(KoreanKeyboardService::class.java).create()
            val service = controller.get()
            service.getSharedPreferences("keyboard_layout", 0).edit()
                .putString("theme", id)
                .putLong("theme_seoul_unlock_expiry", System.currentTimeMillis() + 86_400_000)
                .apply()
            KeyboardThemeSettings.grantSeoulUnlock(service)
            KeyboardLayoutSettings.writeInputLayout(service, InputLayout.QWERTY)
            val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
            ReflectionHelpers.setField(service, "mInputEditorInfo", info)
            service.onStartInput(info, false)
            val root = service.onCreateInputView() as LinearLayout
            val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
            try {
                activity.get().setContentView(root)
                root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                root.viewTreeObserver.dispatchOnPreDraw()
                if (baseHeight == 0) baseHeight = root.height
                assertEquals("Theme artwork must not enlarge typing area: $id", baseHeight, root.height)
                fun buttons(view: View): List<Button> = when(view) {
                    is Button -> listOf(view)
                    is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
                    else -> emptyList()
                }
                val keys = buttons(root)
                assertTrue("Bottom row is present", keys.any { it.text.toString() == "?123" })
                assertTrue("Enter is present", keys.any { it.text.toString() == "↵" || it.contentDescription?.toString()?.contains("엔터") == true || it.text.toString() == "⏎" })
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val out = File("build/reports/recovery/preview_$id.png")
                out.parentFile?.mkdirs()
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue(out.length() > 1000)
                bitmap.recycle()
                keys.single { it.text.toString() == "⚙" }.performClick()
                val launched = shadowOf(service).nextStartedActivity
                assertEquals(ComposeMainActivity::class.java.name, launched.component?.className)
                assertTrue(launched.getBooleanExtra("open_shared_settings", false))
            } finally {
                activity.pause().stop().destroy()
                controller.destroy()
            }
        }
    }
}
