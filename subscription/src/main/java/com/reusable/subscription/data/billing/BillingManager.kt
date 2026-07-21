package com.reusable.subscription.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.reusable.subscription.data.local.BillingDataStore
import com.reusable.subscription.domain.manager.SubscriptionEntitlementManager
import com.reusable.subscription.domain.model.BillingState
import com.reusable.subscription.domain.model.SubscriptionProduct
import com.reusable.subscription.utils.SubscriptionConstant
import com.reusable.subscription.data.security.BillingSecurity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BillingManager(
    private val context: Context,
    private val licenseKey: String,
    private val dataStore: BillingDataStore
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    private val _products = MutableStateFlow<List<SubscriptionProduct>?>(null)
    val products: StateFlow<List<SubscriptionProduct>?> = _products.asStateFlow()

    private val _entitlementGranted = MutableSharedFlow<Unit>(replay = 1)
    val entitlementGranted: SharedFlow<Unit> = _entitlementGranted.asSharedFlow()

    private var productIds: List<String> = emptyList()

    fun startConnection(ids: List<String>) {
        productIds = ids
        _billingState.value = BillingState.Loading
        billingClient.startConnection(this)
    }
    
    fun endConnection() {
        billingClient.endConnection()
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _billingState.value = BillingState.Connected
            
            val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    handlePurchases(purchases)
                }
            }
            queryProductDetails(productIds)
        } else {
            _billingState.value = BillingState.Error(billingResult.responseCode, billingResult.debugMessage)
        }
    }

    override fun onBillingServiceDisconnected() {
        _billingState.value = BillingState.Error(-1, "Service disconnected")
    }

    private fun queryProductDetails(productIds: List<String>) {
        val productList = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val mappedProducts = queryProductDetailsResult.productDetailsList?.mapNotNull { details ->
                    if (details.productType == BillingClient.ProductType.SUBS) {
                        val offer = details.subscriptionOfferDetails?.firstOrNull()
                        val pricingPhases = offer?.pricingPhases?.pricingPhaseList
                        val regularPhase = pricingPhases?.find { it.priceAmountMicros > 0 } ?: pricingPhases?.lastOrNull()
                        val freePhase = pricingPhases?.find { it.priceAmountMicros == 0L }
                        
                        if (offer != null && regularPhase != null) {
                            SubscriptionProduct(
                                productId = details.productId,
                                productDetails = details,
                                formattedPrice = regularPhase.formattedPrice,
                                priceMicros = regularPhase.priceAmountMicros,
                                title = details.name,
                                description = details.description,
                                offerToken = offer.offerToken,
                                subscriptionPeriod = regularPhase.billingPeriod,
                                hasTrial = freePhase != null,
                                trialDescription = if (freePhase != null) "Free trial for ${freePhase.billingPeriod}" else null,
                                introductoryPriceText = null,
                                introductoryPriceMicros = 0L
                            )
                        } else null
                    } else null
                }
                queryInAppProductDetails(productIds, (mappedProducts ?: emptyList()).toMutableList())
            } else {
                queryInAppProductDetails(productIds, mutableListOf())
            }
        }
    }

    private fun queryInAppProductDetails(productIds: List<String>, currentProducts: MutableList<SubscriptionProduct>) {
        val inAppProductList = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }

        val inAppParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inAppProductList)
            .build()

        billingClient.queryProductDetailsAsync(inAppParams) { billingResult, queryProductDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val mappedInApp = queryProductDetailsResult.productDetailsList?.mapNotNull { details ->
                    if (details.productType == BillingClient.ProductType.INAPP) {
                        val oneTimeDetails = details.oneTimePurchaseOfferDetails
                        if (oneTimeDetails != null) {
                            SubscriptionProduct(
                                productId = details.productId,
                                productDetails = details,
                                formattedPrice = oneTimeDetails.formattedPrice,
                                priceMicros = oneTimeDetails.priceAmountMicros,
                                title = details.name,
                                description = details.description,
                                offerToken = null,
                                subscriptionPeriod = null,
                                hasTrial = false,
                                trialDescription = null,
                                introductoryPriceText = null,
                                introductoryPriceMicros = 0L
                            )
                        } else null
                    } else null
                }
                if (mappedInApp != null) currentProducts.addAll(mappedInApp)
            }
            _products.value = currentProducts
        }
    }

    fun launchBillingFlow(activity: Activity, product: SubscriptionProduct) {
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product.productDetails)
        if (product.offerToken != null) {
            paramsBuilder.setOfferToken(product.offerToken)
        }
        val productDetailsParamsList = listOf(paramsBuilder.build())
        
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else {
            _billingState.value = BillingState.Error(billingResult.responseCode, billingResult.debugMessage)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                // Play has confirmed the purchase. Grant entitlement and notify the screen
                // immediately; acknowledgment must not keep a non-cancelable loader visible.
                grantEntitlement(purchase)
                if (!purchase.isAcknowledged) {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            Log.w("BillingManager", "Purchase acknowledgment failed: ${billingResult.debugMessage}")
                        }
                    }
                }
            }
        }
    }

    private fun grantEntitlement(purchase: Purchase) {
        val purchasedProducts = purchase.products.toSet()
        val hasSubscription = purchasedProducts.any(SubscriptionConstant.getSubscriptionProductIds()::contains)
        val hasLifetime = purchasedProducts.any(SubscriptionConstant.getLifetimeProductIds()::contains)
        if (!hasSubscription && !hasLifetime) return

        if (hasSubscription) {
            SubscriptionEntitlementManager.applySubscriptionState(context, true)
        }
        if (hasLifetime) {
            SubscriptionEntitlementManager.applyLifetimeState(context, true)
        }

        // This is the purchase-success callback consumed by SubscriptionActivity. Emit it
        // before the auxiliary DataStore write so the progress dialog always closes promptly.
        _entitlementGranted.tryEmit(Unit)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { dataStore.savePremiumStatus(true) }
                .onFailure { Log.w("BillingManager", "Unable to mirror premium status", it) }
        }
    }

    fun restorePurchases() {
        if (billingClient.isReady) {
            val subsParams = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            billingClient.queryPurchasesAsync(subsParams) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    handlePurchases(purchases)
                }
            }
            
            val inappParams = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            billingClient.queryPurchasesAsync(inappParams) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    handlePurchases(purchases)
                }
            }
        }
    }
}
