package com.ghtnql.kkkeyboard

import android.content.Context
import android.graphics.Color
import org.json.JSONObject

data class ThemeEntry(
    val id: String,
    val titleKo: String,
    val kind: String,
    val unlock: String,
    val imageAsset: String?,
    val scrimAlpha: Int,
    val palette: KeyboardPalette?,
    val available: Boolean = true,
    val previewAsset: String? = null,
)

object ThemeCatalog {
    const val ASSET_PATH = "themes.json"
    const val IMAGE_DIR = "images/"
    private const val VERSION = 1

    fun load(context: Context): List<ThemeEntry>? = try {
        context.assets.open(ASSET_PATH).bufferedReader().use { parse(it.readText()) }
    } catch (_: Exception) {
        null
    }

    fun palette(context: Context, id: String): KeyboardPalette? =
        load(context)?.firstOrNull { it.id == id && it.available }?.palette

    fun parse(json: String): List<ThemeEntry> {
        val root = JSONObject(json)
        require(root.getInt("version") == VERSION) { "Unsupported theme catalog version" }
        val rows = root.getJSONArray("themes")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val id = row.getString("id")
            require(id.isNotBlank()) { "Theme id is blank" }
            val kind = row.getString("kind")
            require(kind == "system" || kind == "palette" || kind == "image") { "$id has unknown kind $kind" }
            val unlock = row.optString("unlock", "free")
            require(unlock == "free" || unlock == "launch_set") { "$id has unknown unlock $unlock" }
            val imageAsset = if (kind == "image") {
                val name = row.getString("image")
                require(name.isNotBlank()) { "$id has no image" }
                IMAGE_DIR + name
            } else null
            val scrimAlpha = if (kind == "image") {
                row.getInt("scrimAlpha").also { require(it in 0..100) { "$id scrimAlpha out of range" } }
            } else 0
            val palette = if (kind == "system") null else {
                val p = row.getJSONObject("palette")
                KeyboardPalette(
                    keyboardSurface = p.color("keyboardSurface"),
                    keySurface = p.color("keySurface"),
                    controlSurface = p.color("controlSurface"),
                    border = p.color("border"),
                    text = p.color("text"),
                    accent = p.color("accent"),
                    onAccent = p.color("onAccent"),
                    ripple = p.color("ripple"),
                    flickHint = p.color("flickHint"),
                    flickSelected = p.color("flickSelected"),
                )
            }
            ThemeEntry(
                id = id,
                titleKo = row.getString("titleKo"),
                kind = kind,
                unlock = unlock,
                imageAsset = imageAsset,
                scrimAlpha = scrimAlpha,
                palette = palette,
                available = row.optBoolean("available", true),
                previewAsset = row.optString("previewAsset").takeIf { it.isNotBlank() },
            )
        }.also { entries ->
            require(entries.isNotEmpty()) { "Empty theme catalog" }
            require(entries.map(ThemeEntry::id).distinct().size == entries.size) { "Duplicate theme id" }
        }
    }

    private fun JSONObject.color(key: String): Int = try {
        Color.parseColor(getString(key))
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Bad color for $key")
    }
}
