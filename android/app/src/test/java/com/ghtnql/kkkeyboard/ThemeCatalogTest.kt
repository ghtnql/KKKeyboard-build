package com.ghtnql.kkkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeCatalogTest {
    private fun bundledJson(): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("themes.json")
        return checkNotNull(stream) { "themes.json missing from test resources" }
            .bufferedReader().use { it.readText() }
    }

    @Test fun bundledCatalogParsesWithExpectedThemes() {
        val entries = ThemeCatalog.parse(bundledJson())
        assertEquals(
            listOf("system", "basic_light", "basic_dark", "seoul_day", "seoul_night", "taegeuk_default", "busan_ocean", "jeju_default"),
            entries.map { it.id },
        )
        entries.forEach { assertTrue(it.titleKo.isNotBlank()) }
        assertEquals(
            setOf("seoul_night", "seoul_day"),
            entries.filter { it.unlock == "launch_set" }.map { it.id }.toSet(),
        )
    }

    @Test fun lightAndDarkMatchHardcodedPalettes() {
        val entries = ThemeCatalog.parse(bundledJson()).associateBy { it.id }
        assertEquals(KeyboardThemeSettings.LIGHT, entries.getValue("basic_light").palette)
        assertEquals(KeyboardThemeSettings.DARK, entries.getValue("basic_dark").palette)
        assertNull(entries.getValue("system").palette)
    }

    @Test fun imageThemesReferenceBundledImages() {
        val entries = ThemeCatalog.parse(bundledJson())
        val images = entries.filter { it.kind == "image" }
        assertEquals(5, images.size)
        images.forEach { entry ->
            assertNotNull(entry.palette)
            assertNotNull(entry.imageAsset)
            assertTrue(entry.scrimAlpha in 0..100)
            assertNotNull(
                "Missing ${entry.imageAsset}",
                javaClass.classLoader!!.getResource(entry.imageAsset),
            )
        }
    }

    @Test fun malformedCatalogIsRejected() {
        try {
            ThemeCatalog.parse("""{"version":1,"themes":[{"id":"","titleKo":"x","kind":"palette"}]}""")
            throw AssertionError("expected blank id rejection")
        } catch (_: IllegalArgumentException) {
        }
        try {
            ThemeCatalog.parse("""{"version":2,"themes":[]}""")
            throw AssertionError("expected version rejection")
        } catch (_: IllegalArgumentException) {
        }
    }
}
