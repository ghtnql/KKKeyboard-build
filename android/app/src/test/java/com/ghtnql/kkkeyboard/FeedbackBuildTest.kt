package com.ghtnql.kkkeyboard

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-rKR-w360dp-h800dp-mdpi")
class FeedbackBuildTest {
    @Test fun feedbackVariantContainsOnlyKeyboardEntryAndSettings() {
        if (BuildConfig.EXPOSE_GAMES) return
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        val activity = controller.get()
        try {
            val labels = buttons(activity.window.decorView).map { it.text.toString() }
            assertFalse(labels.contains("연습 시작"))
            assertFalse(labels.contains("Rain 타자게임"))
            assertNull(activity.findViewById<View>(R.id.progress_summary))

            activity.findViewById<Button>(R.id.open_app_settings).performClick()
            assertNotNull(activity.findViewById<EditText>(R.id.keyboard_test_field))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun buttons(view: View): List<Button> {
        if (view is Button) return listOf(view)
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
    }
}
