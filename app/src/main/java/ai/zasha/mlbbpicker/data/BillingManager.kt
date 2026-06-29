package ai.zasha.mlbbpicker.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages Google Play Billing for in-app purchases and subscriptions.
 *
 * Products:
 *   - pro_lifetime: One-time purchase for lifetime Pro access ($2.99-$4.99)
 *   - pro_monthly: Monthly subscription for Pro access ($0.99/month)
 */
class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        const val PRODUCT_LIFETIME = "pro_lifetime"
        const val PRODUCT_MONTHLY = "pro_monthly"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    _isConnected.value = true
                    queryProducts()
                    queryExistingPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    _isConnected.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                _isConnected.value = false
            }
        })
    }

    private fun queryProducts() {
        coroutineScope.launch {
            val detailsMap = mutableMapOf<String, ProductDetails>()

            // Query one-time purchase
            val inAppParams = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_LIFETIME)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()

            val inAppResult = billingClient.queryProductDetails(inAppParams)
            inAppResult.productDetailsList?.forEach { details ->
                detailsMap[details.productId] = details
            }

            // Query subscription
            val subsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_MONTHLY)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                )
                .build()

            val subsResult = billingClient.queryProductDetails(subsParams)
            subsResult.productDetailsList?.forEach { details ->
                detailsMap[details.productId] = details
            }

            _productDetails.value = detailsMap
            Log.d(TAG, "Queried ${detailsMap.size} products")
        }
    }

    private fun queryExistingPurchases() {
        coroutineScope.launch {
            // Check in-app purchases
            val inAppResult = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            processPurchaseList(inAppResult.purchasesList)

            // Check subscriptions
            val subsResult = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
            processPurchaseList(subsResult.purchasesList)
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val details = _productDetails.value[productId]
        if (details == null) {
            _purchaseState.value = PurchaseState.Error("Product not available")
            return
        }

        val productDetailsParamsList = if (productId == PRODUCT_MONTHLY) {
            // Subscription - need to select an offer
            val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                _purchaseState.value = PurchaseState.Error("No subscription offer available")
                return
            }
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build()
            )
        } else {
            // One-time purchase
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build()
            )
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        _purchaseState.value = PurchaseState.Pending
        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { processPurchaseList(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseState.value = PurchaseState.Idle
            }
            else -> {
                _purchaseState.value = PurchaseState.Error(
                    "Purchase failed: ${billingResult.debugMessage}"
                )
            }
        }
    }

    private fun processPurchaseList(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                // Acknowledge if not yet acknowledged
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }

                // Determine plan type
                val planType = if (purchase.products.contains(PRODUCT_LIFETIME)) {
                    "lifetime"
                } else {
                    "monthly"
                }

                PremiumManager.unlockPremium(planType, purchase.purchaseToken)
                _purchaseState.value = PurchaseState.Success(planType)
                Log.d(TAG, "Premium unlocked: $planType")
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        coroutineScope.launch {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = billingClient.acknowledgePurchase(params)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "Failed to acknowledge purchase: ${result.debugMessage}")
            }
        }
    }

    fun getFormattedPrice(productId: String): String? {
        val details = _productDetails.value[productId] ?: return null
        return if (productId == PRODUCT_MONTHLY) {
            details.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                ?.formattedPrice
        } else {
            details.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    fun endConnection() {
        billingClient.endConnection()
        _isConnected.value = false
    }
}

sealed class PurchaseState {
    data object Idle : PurchaseState()
    data object Pending : PurchaseState()
    data class Success(val planType: String) : PurchaseState()
    data class Error(val message: String) : PurchaseState()
}
