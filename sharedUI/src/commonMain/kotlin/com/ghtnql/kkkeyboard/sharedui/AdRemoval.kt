package com.ghtnql.kkkeyboard.sharedui

/**
 * Shared immutable UI state for the one-time non-consumable ad removal.
 *
 * Product ID: com.ghtnql.kkkeyboard.remove_ads (planned KRW 3900;
 * displayed price always comes from the store, never hard-coded here).
 *
 * messageCode is one of: unavailable, pending, cancelled, purchase_failed,
 * verification_failed, restored, no_purchase, purchased, connection_failed,
 * or null. No raw tokens, logs, or user text travel through this state.
 */
data class AdRemovalState(
    val owned: Boolean = false,
    val purchaseAvailable: Boolean = false,
    val price: String? = null,
    val busy: Boolean = false,
    val messageCode: String? = null,
)

object AdRemovalProduct {
    const val PRODUCT_ID = "com.ghtnql.kkkeyboard.remove_ads"
}
