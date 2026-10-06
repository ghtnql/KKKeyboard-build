package com.ghtnql.kkkeyboard

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.ghtnql.kkkeyboard.sharedui.AdRemovalProduct
import com.ghtnql.kkkeyboard.sharedui.AdRemovalState
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.AcknowledgePurchaseResponseListener

/**
 * One-time non-consumable ad removal backed by Play Billing 9.1.0.
 *
 * Product: [AdRemovalProduct.PRODUCT_ID] (INAPP). Planned KRW 3900, but the
 * displayed [AdRemovalState.price] always comes from the store.
 *
 * Wiring (done by the manager, not here): call [refresh] on resume,
 * observe [onStateChanged], and render [state] including [AdRemovalState.messageCode].
 * No app accounts, no server, no test bypass.
 */
class AdRemovalBillingController internal constructor(
    private val activity: Activity,
    private val licensePublicKey: String,
    clientFactory: (PurchasesUpdatedListener) -> AdRemovalBillingGateway,
) {
    constructor(activity: Activity, licensePublicKey: String) : this(
        activity, licensePublicKey, { PlayAdRemovalBillingGateway(activity.applicationContext, it) },
    )
    companion object {
        private const val PREFS = "ad_removal_billing"
        private const val KEY_JSON = "verified_original_json"
        private const val KEY_SIG = "verified_signature"
    }

    @Volatile
    var state: AdRemovalState = AdRemovalState()
        private set

    /** Optional observer the manager/UI attaches; invoked on every state change. */
    var onStateChanged: ((AdRemovalState) -> Unit)? = null

    @Volatile
    private var closed = false

    @Volatile
    private var cachedDetails: com.android.billingclient.api.ProductDetails? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val pendingConnectCallbacks = mutableListOf<(Boolean) -> Unit>()

    @Volatile
    private var connecting = false
    private var connectionGeneration = 0

    @Volatile
    private var purchaseEventGeneration = 0

    private val keyValid: Boolean =
        licensePublicKey.isNotBlank() && runCatching {
            KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(Base64.decode(licensePublicKey, Base64.DEFAULT)),
            )
            true
        }.getOrDefault(false)

    private val prefs by lazy {
        activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (closed) return@PurchasesUpdatedListener
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    update(state.copy(busy = false, messageCode = "cancelled"))
                } else {
                    purchaseEventGeneration++
                    handlePurchases(purchases, purchaseFlow = true)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                update(state.copy(busy = false, messageCode = "cancelled"))
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                update(state.copy(busy = false, messageCode = "unavailable"))
            else -> update(state.copy(busy = false, messageCode = "purchase_failed"))
        }
    }

    private val billingClient = clientFactory(purchasesListener)

    init { restoreCachedOwnership() }

    private fun restoreCachedOwnership() {
        val json = prefs.getString(KEY_JSON, null)
        val signature = prefs.getString(KEY_SIG, null)
        val purchase = if (json != null && signature != null) {
            runCatching { Purchase(json, signature) }.getOrNull()
        } else null
        val owned = purchase != null && verifiedPurchased(purchase)
        state = state.copy(owned = owned)
        if (!owned) clearCache()
    }

    private fun verifiedPurchased(purchase: Purchase): Boolean =
        purchase.packageName == activity.packageName &&
            AdRemovalProduct.PRODUCT_ID in purchase.products &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            !purchase.isSuspended && purchase.purchaseToken.isNotBlank() && verify(purchase.originalJson, purchase.signature)

    /** Reconnect + reload store price and verified ownership. Safe to call on resume. */
    fun refresh() {
        if (closed || state.busy) return
        update(state.copy(busy = true, messageCode = null))
        ensureConnected { ok ->
            if (closed) return@ensureConnected
            if (!ok) {
                update(state.copy(busy = false, messageCode = "connection_failed"))
                return@ensureConnected
            }
            queryProductDetails(continueOnFailure = true) { queryPurchases(isRestore = false) }
        }
    }

    /** Fresh ProductDetails query first (avoids stale token), then launches the flow. */
    fun purchase() {
        if (closed || state.busy) return
        update(state.copy(busy = true, messageCode = null))
        ensureConnected { ok ->
            if (closed) return@ensureConnected
            if (!ok) {
                update(state.copy(busy = false, messageCode = "connection_failed"))
                return@ensureConnected
            }
            queryProductDetails(continueOnFailure = false) { launchFlow() }
        }
    }

    /** Re-checks owned purchases; grants only on valid signature + PURCHASED. */
    fun restore() {
        if (closed || state.busy) return
        update(state.copy(busy = true, messageCode = null))
        ensureConnected { ok ->
            if (closed) return@ensureConnected
            if (!ok) {
                update(state.copy(busy = false, messageCode = "connection_failed"))
                return@ensureConnected
            }
            queryPurchases(isRestore = true)
        }
    }

    /** Ends the BillingClient connection; later native callbacks are ignored. */
    fun close() {
        closed = true
        synchronized(pendingConnectCallbacks) { pendingConnectCallbacks.clear(); connecting = false }
        onStateChanged = null
        runCatching { billingClient.endConnection() }
    }

    private fun ensureConnected(done: (Boolean) -> Unit) {
        if (closed) return
        if (billingClient.isReady) {
            done(true)
            return
        }
        val generation: Int
        synchronized(pendingConnectCallbacks) {
            pendingConnectCallbacks.add(done)
            if (connecting) return
            connecting = true
            generation = ++connectionGeneration
        }
        try {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val queued: List<(Boolean) -> Unit>
                    synchronized(pendingConnectCallbacks) {
                        if (generation != connectionGeneration) return
                        connecting = false
                        queued = pendingConnectCallbacks.toList()
                        pendingConnectCallbacks.clear()
                    }
                    if (closed) return
                    val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                    queued.forEach { it(ok) }
                }

                override fun onBillingServiceDisconnected() {
                    // Auto-service-reconnection is enabled; next call reconnects.
                    val queued = synchronized(pendingConnectCallbacks) {
                        if (generation != connectionGeneration) return
                        connecting = false
                        pendingConnectCallbacks.toList().also { pendingConnectCallbacks.clear() }
                    }
                    if (!closed) queued.forEach { it(false) }
                }
            })
        } catch (_: Exception) {
            val queued = synchronized(pendingConnectCallbacks) {
                connecting = false
                pendingConnectCallbacks.toList().also { pendingConnectCallbacks.clear() }
            }
            if (!closed) queued.forEach { it(false) }
        }
    }

    private fun productParams(): QueryProductDetailsParams =
        QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(AdRemovalProduct.PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
            ),
        ).build()

    private fun queryProductDetails(continueOnFailure: Boolean, next: () -> Unit) {
        billingClient.queryProductDetailsAsync(
            productParams(),
            ProductDetailsResponseListener { result: BillingResult, details: QueryProductDetailsResult ->
                if (closed) return@ProductDetailsResponseListener
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    cachedDetails = null
                    update(
                        state.copy(
                            busy = continueOnFailure,
                            purchaseAvailable = false,
                            price = null,
                            messageCode = "connection_failed",
                        ),
                    )
                    if (continueOnFailure) next()
                    return@ProductDetailsResponseListener
                }
                val match = details.productDetailsList.firstOrNull {
                    it.productId == AdRemovalProduct.PRODUCT_ID
                }
                cachedDetails = match
                val price = match?.oneTimePurchaseOfferDetails?.formattedPrice
                if (!keyValid || match == null || match.oneTimePurchaseOfferDetails == null) {
                    // Fail closed: no valid license key means nothing can be trusted.
                    update(
                        state.copy(
                            purchaseAvailable = false,
                            price = price,
                            busy = continueOnFailure,
                            messageCode = if (!keyValid) "verification_failed" else "unavailable",
                        ),
                    )
                    if (continueOnFailure) next()
                    return@ProductDetailsResponseListener
                }
                update(state.copy(purchaseAvailable = true, price = price))
                next()
            },
        )
    }

    private fun launchFlow() {
        if (closed) return
        val details = cachedDetails
        if (!keyValid || details == null) {
            update(
                state.copy(
                    busy = false,
                    purchaseAvailable = false,
                    messageCode = if (!keyValid) "verification_failed" else "unavailable",
                ),
            )
            return
        }
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        details.oneTimePurchaseOfferDetails?.offerToken?.let { paramsBuilder.setOfferToken(it) }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            update(
                state.copy(
                    busy = false,
                    messageCode = if (result.responseCode ==
                        BillingClient.BillingResponseCode.USER_CANCELED
                    ) {
                        "cancelled"
                    } else {
                        "purchase_failed"
                    },
                ),
            )
        }
    }

    private fun queryPurchases(isRestore: Boolean) {
        val generation = purchaseEventGeneration
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { result: BillingResult, purchases: List<Purchase> ->
            if (closed) return@queryPurchasesAsync
            // A purchase notification received after this query started is newer evidence.
            // Never revoke a just-completed purchase with the earlier query snapshot.
            if (generation != purchaseEventGeneration) return@queryPurchasesAsync
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                // Failed query: keep prior verified ownership, just stop spinning.
                update(state.copy(busy = false, messageCode = "connection_failed"))
                return@queryPurchasesAsync
            }
            if (purchases.isEmpty()) {
                // Successful empty response revokes and clears the receipt cache.
                clearCache()
                update(
                    state.copy(
                        owned = false,
                        busy = false,
                        messageCode = if (isRestore) "no_purchase" else state.messageCode,
                    ),
                )
                return@queryPurchasesAsync
            }
            handlePurchases(purchases, purchaseFlow = false, isRestore = isRestore, authoritative = true)
        }
    }

    private fun handlePurchases(
        purchases: List<Purchase>,
        purchaseFlow: Boolean,
        isRestore: Boolean = false,
        authoritative: Boolean = false,
    ) {
        if (closed) return
        val matching = purchases.filter { AdRemovalProduct.PRODUCT_ID in it.products }
        val match = matching.firstOrNull(::verifiedPurchased) ?: matching.firstOrNull()
        when {
            match == null -> {
                if (authoritative) {
                    clearCache()
                    update(state.copy(owned = false, busy = false, messageCode = "no_purchase"))
                } else if (purchaseFlow) {
                    update(state.copy(busy = false, messageCode = "no_purchase"))
                } else {
                    update(state.copy(busy = false))
                }
            }
            match.purchaseState == Purchase.PurchaseState.PENDING -> {
                if (authoritative) {
                    clearCache()
                    update(state.copy(owned = false, busy = false, messageCode = "pending"))
                } else {
                    update(state.copy(busy = false, messageCode = "pending"))
                }
            }
            match.purchaseState != Purchase.PurchaseState.PURCHASED -> {
                if (authoritative) {
                    clearCache()
                    update(state.copy(owned = false, busy = false, messageCode = "purchase_failed"))
                } else {
                    update(state.copy(busy = false, messageCode = "purchase_failed"))
                }
            }
            !verifiedPurchased(match) -> {
                if (authoritative) {
                    clearCache()
                    update(state.copy(owned = false, busy = false, messageCode = "verification_failed"))
                } else {
                    update(state.copy(busy = false, messageCode = "verification_failed"))
                }
            }
            else -> {
                storeCache(match.originalJson, match.signature)
                val code = when {
                    purchaseFlow -> "purchased"
                    isRestore -> "restored"
                    else -> null
                }
                update(state.copy(owned = true, busy = false, messageCode = code))
                if (!match.isAcknowledged) acknowledge(match.purchaseToken)
            }
        }
    }

    private fun acknowledge(token: String) {
        if (closed) return
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build(),
        ) { result: BillingResult ->
            if (closed || !state.owned ||
                runCatching { Purchase(prefs.getString(KEY_JSON, "")!!, prefs.getString(KEY_SIG, "")!!).purchaseToken }
                    .getOrNull() != token) return@acknowledgePurchase
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                update(state.copy(busy = false, messageCode = "purchase_failed"))
            }
        }
    }

    private fun verify(originalJson: String, signature: String): Boolean {
        if (!keyValid || originalJson.isBlank() || signature.isBlank()) return false
        return runCatching {
            val keyBytes = Base64.decode(licensePublicKey, Base64.DEFAULT)
            val key = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(keyBytes))
            val sig = Signature.getInstance("SHA1withRSA")
            sig.initVerify(key)
            sig.update(originalJson.toByteArray(Charsets.UTF_8))
            sig.verify(Base64.decode(signature, Base64.DEFAULT))
        }.getOrDefault(false)
    }

    private fun storeCache(originalJson: String, signature: String) {
        prefs.edit().putString(KEY_JSON, originalJson).putString(KEY_SIG, signature).apply()
    }

    private fun clearCache() {
        prefs.edit().remove(KEY_JSON).remove(KEY_SIG).apply()
    }

    private fun update(next: AdRemovalState) {
        if (closed) return
        state = next
        if (Looper.myLooper() == Looper.getMainLooper()) onStateChanged?.invoke(next)
        else mainHandler.post { if (!closed) onStateChanged?.invoke(state) }
    }
}

/** SDK boundary for controlled store responses in receipt-verification tests. */
internal interface AdRemovalBillingGateway {
    val isReady: Boolean
    fun startConnection(listener: BillingClientStateListener)
    fun endConnection()
    fun queryProductDetailsAsync(params: QueryProductDetailsParams, listener: ProductDetailsResponseListener)
    fun queryPurchasesAsync(params: QueryPurchasesParams, listener: PurchasesResponseListener)
    fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult
    fun acknowledgePurchase(params: AcknowledgePurchaseParams, listener: AcknowledgePurchaseResponseListener)
}

private class PlayAdRemovalBillingGateway(context: Context, listener: PurchasesUpdatedListener) : AdRemovalBillingGateway {
    private val client = BillingClient.newBuilder(context).setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()
    override val isReady get() = client.isReady
    override fun startConnection(listener: BillingClientStateListener) = client.startConnection(listener)
    override fun endConnection() = client.endConnection()
    override fun queryProductDetailsAsync(params: QueryProductDetailsParams, listener: ProductDetailsResponseListener) =
        client.queryProductDetailsAsync(params, listener)
    override fun queryPurchasesAsync(params: QueryPurchasesParams, listener: PurchasesResponseListener) =
        client.queryPurchasesAsync(params, listener)
    override fun launchBillingFlow(activity: Activity, params: BillingFlowParams) = client.launchBillingFlow(activity, params)
    override fun acknowledgePurchase(params: AcknowledgePurchaseParams, listener: AcknowledgePurchaseResponseListener) =
        client.acknowledgePurchase(params, listener)
}
