package com.ghtnql.kkkeyboard

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.ghtnql.kkkeyboard.sharedui.AdRemovalProduct
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdRemovalBillingControllerTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private lateinit var activity: Activity
    private lateinit var gateway: FakeGateway
    private lateinit var listener: PurchasesUpdatedListener
    private val controllers = mutableListOf<AdRemovalBillingController>()
    private val prefs get() = activity.getSharedPreferences("ad_removal_billing", Context.MODE_PRIVATE)
    private val publicKey get() = Base64.getEncoder().encodeToString(keys.public.encoded)

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        gateway = FakeGateway()
        prefs.edit().clear().commit()
    }
    @After fun cleanup() {
        controllers.forEach { it.close() }
        prefs.edit().clear().commit()
        activity.finish()
    }
    private fun controller(key: String = publicKey): AdRemovalBillingController =
        AdRemovalBillingController(activity, key) { listener = it; gateway }.also { controllers += it }

    // Play's signed JSON encodes completed as 0, pending as 4; SDK constants differ.
    private fun receipt(product: String = AdRemovalProduct.PRODUCT_ID, packageName: String = activity.packageName,
                        rawState: Int = 0, acknowledged: Boolean = true, token: String = "test-purchase-token"): Purchase {
        val json = JSONObject().put("packageName", packageName).put("productId", product)
            .put("purchaseState", rawState).put("purchaseToken", token)
            .put("acknowledged", acknowledged).toString()
        val signature = Signature.getInstance("SHA1withRSA").run {
            initSign(keys.private); update(json.toByteArray(Charsets.UTF_8)); sign()
        }
        return Purchase(json, Base64.getEncoder().encodeToString(signature))
    }
    private fun cache(purchase: Purchase) {
        prefs.edit().putString("verified_original_json", purchase.originalJson)
            .putString("verified_signature", purchase.signature).commit()
    }
    private fun update(purchase: Purchase) = listener.onPurchasesUpdated(result(), listOf(purchase))

    @Test fun verifiedCacheSurvivesRecreationAndFailedStoreQuery() {
        cache(receipt())
        val billing = controller()
        assertTrue(billing.state.owned)
        billing.restore()
        gateway.answerPurchases(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
        assertTrue(billing.state.owned)
        assertFalse(billing.state.busy)
        assertTrue(prefs.contains("verified_original_json"))
        assertTrue(controller().state.owned)
    }
    @Test fun missingOrWrongKeyAndTamperedReceiptFailClosed() {
        cache(receipt()); assertFalse(controller("").state.owned)
        assertFalse(prefs.contains("verified_original_json"))
        cache(receipt()); assertFalse(controller("invalid").state.owned)
        val purchase = receipt()
        cache(Purchase(purchase.originalJson.replace("test-purchase-token", "tampered-token"), purchase.signature))
        assertFalse(controller().state.owned)
    }
    @Test fun validSignatureForWrongPackageOrProductNeverGrantsOwnership() {
        val billing = controller()
        update(receipt(packageName = "other.application")); assertFalse(billing.state.owned)
        update(receipt(product = "unrelated")); assertFalse(billing.state.owned)
        assertTrue(gateway.acknowledgedTokens.isEmpty())
    }
    @Test fun pendingNeverGrantsOrAcknowledgesAndCompletedPurchaseDoes() {
        val billing = controller()
        val pending = receipt(rawState = 4, acknowledged = false)
        assertEquals(Purchase.PurchaseState.PENDING, pending.purchaseState)
        update(pending); assertFalse(billing.state.owned)
        assertEquals("pending", billing.state.messageCode)
        assertTrue(gateway.acknowledgedTokens.isEmpty())
        update(receipt(acknowledged = false)); assertTrue(billing.state.owned)
        assertEquals(listOf("test-purchase-token"), gateway.acknowledgedTokens)
        assertTrue(controller().state.owned)
    }
    @Test fun successfulQueryWithOtherProductsRevokesAndClearsVerifiedCache() {
        cache(receipt()); val billing = controller(); billing.restore()
        gateway.answerPurchases(purchases = listOf(receipt(product = "unrelated")))
        assertFalse(billing.state.owned)
        assertFalse(prefs.contains("verified_original_json"))
        assertEquals("no_purchase", billing.state.messageCode)
    }
    @Test fun successfulEmptyQueryRevokesAndPendingAuthoritativeQueryAlsoRevokes() {
        cache(receipt()); val billing = controller(); billing.restore()
        gateway.answerPurchases(); assertFalse(billing.state.owned)
        update(receipt()); assertTrue(billing.state.owned)
        billing.restore(); gateway.answerPurchases(purchases = listOf(receipt(rawState = 4)))
        assertFalse(billing.state.owned); assertEquals("pending", billing.state.messageCode)
        assertFalse(prefs.contains("verified_signature"))
    }
    @Test fun productQueryFailureStillChecksOwnershipAndRefunds() {
        cache(receipt()); val billing = controller(); billing.refresh()
        gateway.productListener!!.onProductDetailsResponse(result(BillingClient.BillingResponseCode.ERROR),
            QueryProductDetailsResult.create(emptyList(), emptyList()))
        assertEquals(1, gateway.purchaseQueries)
        gateway.answerPurchases(); assertFalse(billing.state.owned)
        assertFalse(billing.state.purchaseAvailable)
    }
    @Test fun validCompletedReceiptWinsOverPendingOrInvalidDuplicate() {
        val billing = controller(); billing.restore()
        gateway.answerPurchases(purchases = listOf(receipt(rawState = 4), receipt(packageName = "other.app"), receipt()))
        assertTrue(billing.state.owned)
    }
    @Test fun failedAcknowledgementKeepsVerifiedOwnershipAndRefreshRetries() {
        val billing = controller(); update(receipt(acknowledged = false))
        gateway.ackListener!!.onAcknowledgePurchaseResponse(result(BillingClient.BillingResponseCode.ERROR))
        assertTrue(billing.state.owned)
        assertEquals("purchase_failed", billing.state.messageCode)
        billing.restore(); gateway.answerPurchases(purchases = listOf(receipt(acknowledged = false)))
        assertEquals(2, gateway.acknowledgedTokens.size)
    }
    @Test fun closeSuppressesLatePurchasesQueriesAndAcknowledgementCallbacks() {
        val billing = controller(); update(receipt(acknowledged = false)); billing.restore()
        var notifications = 0; billing.onStateChanged = { notifications++ }
        billing.close(); val closedState = billing.state
        gateway.answerPurchases()
        gateway.ackListener!!.onAcknowledgePurchaseResponse(result(BillingClient.BillingResponseCode.ERROR))
        update(receipt(rawState = 4)); billing.purchase(); billing.refresh()
        assertEquals(closedState, billing.state)
        assertEquals(0, notifications); assertTrue(gateway.ended)
    }
    @Test fun connectionFailureReleasesBusyAndAllowsRetryAndClosedSetupIsIgnored() {
        gateway.isReady = false; val billing = controller(); billing.restore()
        assertTrue(billing.state.busy); assertEquals(1, gateway.connectionCalls)
        billing.restore(); assertEquals(1, gateway.connectionCalls)
        gateway.connectionListener!!.onBillingServiceDisconnected()
        assertFalse(billing.state.busy)
        billing.restore(); assertEquals(2, gateway.connectionCalls)
        billing.close(); gateway.connectionListener!!.onBillingSetupFinished(result())
        assertEquals(0, gateway.purchaseQueries)
    }

    @Test fun oldConnectionCompletionCannotDrainNewRetryCallbacks() {
        gateway.isReady = false; val billing = controller(); billing.restore()
        val first = gateway.connectionListener!!
        first.onBillingServiceDisconnected(); billing.restore()
        first.onBillingSetupFinished(result())
        assertEquals(0, gateway.purchaseQueries)
        gateway.connectionListener!!.onBillingSetupFinished(result())
        assertEquals(1, gateway.purchaseQueries)
    }
    @Test fun observerClosingOnOwnershipGrantPreventsAcknowledgementAfterClose() {
        val billing = controller()
        billing.onStateChanged = { if (it.owned) billing.close() }
        update(receipt(acknowledged = false))
        assertTrue(gateway.ended)
        assertTrue(gateway.acknowledgedTokens.isEmpty())
    }

    @Test fun earlierEmptyQueryCannotRevokeNewCompletedPurchaseNotification() {
        val billing = controller(); billing.restore()
        update(receipt())
        gateway.answerPurchases()
        assertTrue(billing.state.owned)
        assertTrue(prefs.contains("verified_original_json"))
        // A later authoritative query still detects a refund normally.
        billing.restore(); gateway.answerPurchases()
        assertFalse(billing.state.owned)
    }

    private fun storeDetails(price: String): ProductDetails {
        val json = JSONObject().put("productId", AdRemovalProduct.PRODUCT_ID).put("type", "inapp")
            .put("title", "Remove ads").put("name", "Remove ads").put("description", "One-time purchase")
            .put("oneTimePurchaseOfferDetails", JSONObject().put("formattedPrice", price)
                .put("priceAmountMicros", 3900000000L).put("priceCurrencyCode", "KRW"))
        return ProductDetails::class.java.getDeclaredConstructor(String::class.java).apply { isAccessible = true }
            .newInstance(json.toString())
    }
    @Test fun displayUsesStorePriceAndPurchaseFetchesFreshDetailsBeforeLaunch() {
        val billing = controller(); billing.refresh()
        gateway.productListener!!.onProductDetailsResponse(result(),
            QueryProductDetailsResult.create(listOf(storeDetails("₩3,900")), emptyList()))
        gateway.answerPurchases()
        assertEquals("₩3,900", billing.state.price)
        assertTrue(billing.state.purchaseAvailable)
        billing.purchase(); assertEquals(0, gateway.launches)
        gateway.productListener!!.onProductDetailsResponse(result(),
            QueryProductDetailsResult.create(listOf(storeDetails("¥400")), emptyList()))
        assertEquals("¥400", billing.state.price)
        assertEquals(1, gateway.launches)
    }
    @Test fun absentPublicKeyDisablesStorePurchaseEvenWhenProductExists() {
        val billing = controller(""); billing.purchase()
        gateway.productListener!!.onProductDetailsResponse(result(),
            QueryProductDetailsResult.create(listOf(storeDetails("¥400")), emptyList()))
        assertFalse(billing.state.purchaseAvailable); assertFalse(billing.state.busy)
        assertEquals("verification_failed", billing.state.messageCode)
        assertEquals(0, gateway.launches)
    }
    @Test fun failedOldAcknowledgementCannotChangeRevokedEntitlement() {
        val billing = controller(); update(receipt(acknowledged = false)); billing.restore()
        gateway.answerPurchases(); val revoked = billing.state
        gateway.ackListener!!.onAcknowledgePurchaseResponse(result(BillingClient.BillingResponseCode.ERROR))
        assertEquals(revoked, billing.state)
    }

    private class FakeGateway : AdRemovalBillingGateway {
        override var isReady = true
        var ended = false
        var connectionCalls = 0
        var purchaseQueries = 0
        var launches = 0
        var connectionListener: BillingClientStateListener? = null
        var productListener: ProductDetailsResponseListener? = null
        var purchaseListener: PurchasesResponseListener? = null
        var ackListener: AcknowledgePurchaseResponseListener? = null
        val acknowledgedTokens = mutableListOf<String>()
        override fun startConnection(listener: BillingClientStateListener) { connectionCalls++; connectionListener = listener }
        override fun endConnection() { ended = true }
        override fun queryProductDetailsAsync(params: QueryProductDetailsParams, listener: ProductDetailsResponseListener) { productListener = listener }
        override fun queryPurchasesAsync(params: QueryPurchasesParams, listener: PurchasesResponseListener) { purchaseQueries++; purchaseListener = listener }
        override fun launchBillingFlow(activity: Activity, params: BillingFlowParams): BillingResult { launches++; return result() }
        override fun acknowledgePurchase(params: AcknowledgePurchaseParams, listener: AcknowledgePurchaseResponseListener) {
            acknowledgedTokens += params.purchaseToken; ackListener = listener
        }
        fun answerPurchases(code: Int = BillingClient.BillingResponseCode.OK, purchases: List<Purchase> = emptyList()) {
            purchaseListener!!.onQueryPurchasesResponse(result(code), purchases)
        }
    }
    companion object {
        private fun result(code: Int = BillingClient.BillingResponseCode.OK): BillingResult =
            BillingResult.newBuilder().setResponseCode(code).build()
    }
}
