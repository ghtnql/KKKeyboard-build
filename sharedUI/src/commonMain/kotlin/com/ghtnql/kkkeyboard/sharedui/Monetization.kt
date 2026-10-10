package com.ghtnql.kkkeyboard.sharedui

data class SharedThemeSwatch(
    val surface: Int,
    val key: Int,
    val text: Int,
    val accent: Int,
)

data class SharedTheme(
    val id: String,
    val titleKo: String,
    val kind: String,
    val unlock: String,
    val swatch: SharedThemeSwatch?,
    val available: Boolean = true,
    val previewAsset: String? = null,
)

fun formatTrialRemaining(remainingMs: Long): String {
    if (remainingMs <= 0) return "만료됨"
    val minutes = remainingMs / 60_000
    if (minutes < 60) return "${minutes}분 남음"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "${hours}시간 남음" else "${hours}시간 ${rest}분 남음"
}
