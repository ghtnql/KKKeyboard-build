package com.ghtnql.kkkeyboard.sharedui

/**
 * Shared cadence gate for game-end fullscreen ads.
 *
 * Host contract:
 * - Epoch millis are passed in by the host ([nowMillis]).
 * - [lastFullscreenAtMillis] has no meaningful default inside this class, so the host
 *   must install init-now (or install time) on first use; that guarantees the first ad
 *   cannot appear within 3 minutes of initial use.
 * - Hosts persist [completedRoundsSinceAd] and [lastFullscreenAtMillis] and restore them
 *   on restart (see "restart with saved state" test).
 * - Hosts call [onCompletedRound] ONLY at natural round completion, never on cancel.
 * - A failed/skipped ad attempt mutates nothing (there is simply no method for it).
 * - A reward fullscreen ad also calls [onFullscreenShown].
 */
class GameEndAdPolicy(
    completedRoundsSinceAd: Int = 0,
    lastFullscreenAtMillis: Long,
) {
    companion object {
        const val REQUIRED_ROUNDS: Int = 3
        const val MIN_INTERVAL_MILLIS: Long = 180_000L
        const val MAX_ROUNDS: Int = 3
    }

    var completedRoundsSinceAd: Int = completedRoundsSinceAd.coerceIn(0, MAX_ROUNDS)
        private set

    var lastFullscreenAtMillis: Long = if (lastFullscreenAtMillis < 0L) 0L else lastFullscreenAtMillis
        private set

    /** Natural completion only; saturates at 3. */
    fun onCompletedRound() {
        if (completedRoundsSinceAd < MAX_ROUNDS) {
            completedRoundsSinceAd += 1
        }
    }

    /**
     * Pure query: never mutates. True only when count >= 3 AND now >= last
     * AND elapsed >= 180_000 ms. Overflow-safe: the now < last guard runs first,
     * so the subtraction cannot wrap for the clamped non-negative timestamp domain.
     */
    fun canShow(nowMillis: Long): Boolean {
        if (completedRoundsSinceAd < REQUIRED_ROUNDS) return false
        if (nowMillis < lastFullscreenAtMillis) return false
        return nowMillis - lastFullscreenAtMillis >= MIN_INTERVAL_MILLIS
    }

    /** Fullscreen (or reward fullscreen) shown: resets count to 0, saves timestamp. */
    fun onFullscreenShown(nowMillis: Long) {
        completedRoundsSinceAd = 0
        lastFullscreenAtMillis = if (nowMillis < 0L) 0L else nowMillis
    }
}
