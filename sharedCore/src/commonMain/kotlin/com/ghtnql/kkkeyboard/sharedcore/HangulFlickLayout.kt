package com.ghtnql.kkkeyboard.sharedcore

import kotlin.math.abs

enum class FlickDirection { CENTER, LEFT, UP, RIGHT, DOWN }

data class FlickKey(
    val center: String,
    val left: String? = null,
    val up: String? = null,
    val right: String? = null,
    val down: String? = null,
) {
    fun label(direction: FlickDirection): String? = when (direction) {
        FlickDirection.CENTER -> center
        FlickDirection.LEFT -> left
        FlickDirection.UP -> up
        FlickDirection.RIGHT -> right
        FlickDirection.DOWN -> down
    }
}

/** Custom Hangul mapping; the tap/four-direction interaction follows Japanese flick keyboards. */
object HangulFlickLayout {
    val rows = listOf(
        listOf(FlickKey("ㅏ", "ㅑ", "ㅐ", "ㅒ"), FlickKey("ㅓ", "ㅕ", "ㅔ", "ㅖ"), FlickKey("ㅗ", "ㅛ", "ㅘ", "ㅙ", "ㅚ")),
        listOf(FlickKey("ㄱ", "ㅋ", "ㄲ"), FlickKey("ㄴ", "ㄹ"), FlickKey("ㅜ", "ㅠ", "ㅝ", "ㅞ", "ㅟ")),
        listOf(FlickKey("ㄷ", "ㅌ", "ㄸ"), FlickKey("ㅂ", "ㅍ", "ㅃ"), FlickKey("ㅡ", "ㅣ", "ㅢ")),
        listOf(FlickKey("ㅅ", "ㅎ", "ㅆ"), FlickKey("ㅈ", "ㅊ", "ㅉ"), FlickKey("ㅇ", "ㅁ")),
    )
    val punctuation = FlickKey(".", ",", "?", "!", "…")
}

object FlickDirectionResolver {
    fun resolve(dx: Float, dy: Float, threshold: Float, previous: FlickDirection = FlickDirection.CENTER): FlickDirection {
        require(threshold > 0)
        val deadZone = if (previous == FlickDirection.CENTER) threshold else threshold * .65f
        if (dx * dx + dy * dy < deadZone * deadZone) return FlickDirection.CENTER
        // Keep a selected axis near a diagonal so small finger tremors cannot flip the choice.
        if (abs(abs(dx) - abs(dy)) < threshold * .25f) {
            when (previous) {
                FlickDirection.LEFT -> if (dx < 0) return previous
                FlickDirection.RIGHT -> if (dx > 0) return previous
                FlickDirection.UP -> if (dy < 0) return previous
                FlickDirection.DOWN -> if (dy > 0) return previous
                else -> Unit
            }
        }
        return if (abs(dx) >= abs(dy)) {
            if (dx < 0) FlickDirection.LEFT else FlickDirection.RIGHT
        } else {
            if (dy < 0) FlickDirection.UP else FlickDirection.DOWN
        }
    }
}

/** Scalar bridge for native renderers; mapping and direction choice stay common. */
class FlickLayoutBridge {
    fun key(row: Int, column: Int): FlickKey = HangulFlickLayout.rows[row][column]
    fun punctuation(): FlickKey = HangulFlickLayout.punctuation
    fun resolve(dx: Float, dy: Float, threshold: Float, previous: FlickDirection): FlickDirection =
        FlickDirectionResolver.resolve(dx, dy, threshold, previous)
}
