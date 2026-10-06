package com.ghtnql.kkkeyboard

object TwoBeolsikLayout {
    const val ROW_UNITS = 10f
    const val EDGE_KEY_UNITS = 1.5f

    val auxiliaryNumberRow: List<String> = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

    val characterRows: List<List<String>> = listOf(
        listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"),
        listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ"),
    )

    val bottomRow: List<String> = listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ")

    private val shiftedKeys = mapOf(
        "ㅂ" to "ㅃ",
        "ㅈ" to "ㅉ",
        "ㄷ" to "ㄸ",
        "ㄱ" to "ㄲ",
        "ㅅ" to "ㅆ",
        "ㅐ" to "ㅒ",
        "ㅔ" to "ㅖ",
    )

    fun labelFor(baseLabel: String, shifted: Boolean): String =
        if (shifted) shiftedKeys[baseLabel] ?: baseLabel else baseLabel

    fun hasShiftVariant(baseLabel: String): Boolean = shiftedKeys.containsKey(baseLabel)
}
