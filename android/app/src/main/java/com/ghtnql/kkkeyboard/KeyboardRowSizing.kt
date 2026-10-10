package com.ghtnql.kkkeyboard

/**
 * Calculates a safe render plan before the IME view is built.
 *
 * This runs only when an input view is created/restarted, never on the key-input hot path.
 * Optional rows are dropped before core rows are allowed to shrink below the compact 44dp
 * touch target. If even the core layout cannot fit, row height is reduced as a last resort so
 * Space/Enter/Backspace stay reachable instead of being laid out below the visible IME area.
 */
data class KeyboardRowPlan(
    val keyHeightDp: Int,
    val numberRowEnabled: Boolean,
    val cursorRowEnabled: Boolean,
)

object KeyboardBaseRows {
    fun forMode(mode: InputFieldMode): Int = when (mode) {
        InputFieldMode.TEXT, InputFieldMode.PASSWORD, InputFieldMode.EMAIL, InputFieldMode.URI -> 5
        InputFieldMode.NUMBER -> 5
        InputFieldMode.PHONE -> 6
    }
}

object KeyboardRowSizing {
    private const val ROOT_VERTICAL_PADDING_DP = 14
    // Each touch cell includes a 1dp visual inset above and below its key face.
    private const val BUTTON_VERTICAL_MARGIN_DP = 2
    private const val MIN_USABLE_KEY_HEIGHT_DP = 44

    fun plan(
        requestedKeyHeightDp: Int,
        availableHeightDp: Int,
        baseRowCount: Int,
        numberRowRequested: Boolean,
        cursorRowRequested: Boolean,
        supportsNumberRow: Boolean,
    ): KeyboardRowPlan {
        require(requestedKeyHeightDp > 0)
        require(availableHeightDp > 0)
        require(baseRowCount > 0)

        var showNumberRow = supportsNumberRow && numberRowRequested
        var showCursorRow = cursorRowRequested

        fun fitHeightDp(): Int {
            val rowCount = baseRowCount + (if (showNumberRow) 1 else 0) + (if (showCursorRow) 1 else 0)
            val contentHeight = (availableHeightDp - ROOT_VERTICAL_PADDING_DP).coerceAtLeast(rowCount)
            return (contentHeight / rowCount - BUTTON_VERTICAL_MARGIN_DP).coerceAtLeast(1)
        }

        // Cursor helpers are less essential than direct number entry; remove them first when
        // keeping both would make every touch target smaller than the compact profile.
        if (showCursorRow && fitHeightDp() < MIN_USABLE_KEY_HEIGHT_DP) showCursorRow = false
        if (showNumberRow && fitHeightDp() < MIN_USABLE_KEY_HEIGHT_DP) showNumberRow = false

        return KeyboardRowPlan(
            keyHeightDp = minOf(requestedKeyHeightDp, fitHeightDp()),
            numberRowEnabled = showNumberRow,
            cursorRowEnabled = showCursorRow,
        )
    }
}
