package com.reusable.subscription.domain.model

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase

data class SubscriptionProduct(
    val productId: String,
    val productDetails: ProductDetails,
    val formattedPrice: String,
    val priceMicros: Long,
    val title: String,
    val description: String,
    val offerToken: String?,
    val subscriptionPeriod: String?,
    val hasTrial: Boolean,
    val trialDescription: String?,
    val introductoryPriceText: String?,
    val introductoryPriceMicros: Long
)

sealed class BillingState {
    object Idle : BillingState()
    object Loading : BillingState()
    object Connected : BillingState()
    data class Error(val code: Int, val message: String) : BillingState()
}

data class PurchaseResult(
    val success: Boolean,
    val purchases: List<Purchase>? = null,
    val errorMessage: String? = null
)
