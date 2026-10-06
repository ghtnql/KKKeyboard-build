package com.ghtnql.kkkeyboard

enum class KeyboardLanguage {
    KOREAN,
    JAPANESE,
    ENGLISH,
    ;

    fun next(): KeyboardLanguage = entries[(ordinal + 1) % entries.size]

    fun effectiveLayout(preferredHangulLayout: InputLayout): InputLayout =
        if (this == ENGLISH) InputLayout.QWERTY else preferredHangulLayout
}
