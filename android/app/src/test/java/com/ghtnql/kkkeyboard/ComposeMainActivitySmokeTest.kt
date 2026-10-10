package com.ghtnql.kkkeyboard

import android.view.ViewGroup
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComposeMainActivitySmokeTest {
    @Test fun launcherCreatesSharedComposeRoot() {
        val activity = Robolectric.buildActivity(ComposeMainActivity::class.java).setup().visible().get()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        assertTrue(content.childCount > 0)
    }
}
