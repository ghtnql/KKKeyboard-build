package com.ghtnql.kkkeyboard

object SymbolLayout {
    data class Page(val top: List<String>, val middle: List<String>, val bottom: List<String>)

    val pages = listOf(
        Page(
            TwoBeolsikLayout.auxiliaryNumberRow,
            listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
            listOf("*", "\"", "'", ":", ";", "!", "?"),
        ),
        Page(
            listOf("[", "]", "{", "}", "<", ">", "\u2190", "\u2191", "\u2193", "\u2192"),
            listOf("_", "\\", "|", "=", "^", "~", "`", "\u00a5", "\u00d7", "\u00f7"),
            listOf("\u300c", "\u300d", "\u3001", "\u3002", "\u30fc", "\u2026", "\u266a"),
        ),
        Page(
            listOf("\u2661", "\u2665", "\u2606", "\u2605", "\u25cb", "\u25cf", "\u25a1", "\u25a0", "\u25b3", "\u25b2"),
            listOf("\u25bd", "\u25bc", "\u2194", "\u2195", "\u20ac", "\u00a3", "\u2022", "\u00b0", "\u00a9", "\u00ae"),
            listOf("\u203b", "\u30fb", "\u3005", "\u3012", "\u2713", "\u300e", "\u300f"),
        ),
    )
}
