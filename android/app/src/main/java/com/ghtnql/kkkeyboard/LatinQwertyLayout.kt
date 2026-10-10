package com.ghtnql.kkkeyboard

object LatinQwertyLayout {
    val rows: List<List<String>> = listOf(
        "qwertyuiop".map(Char::toString),
        "asdfghjkl".map(Char::toString),
        "zxcvbnm".map(Char::toString),
    )

    val characterRows: List<List<String>> = rows.take(2)
    val bottomRow: List<String> = rows.last()

    private val shiftableKeys = rows.flatten().toSet()

    fun labelFor(baseLabel: String, shifted: Boolean): String =
        if (shifted && isShiftable(baseLabel)) baseLabel.uppercase() else baseLabel

    fun isShiftable(label: String): Boolean = label in shiftableKeys

    fun hasShiftVariant(label: String): Boolean = isShiftable(label)
}
