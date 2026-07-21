package com.reusable.subscription.domain.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.reusable.subscription.R

object SubscriptionHostConfig {

    /**
     * Programmatic billing license key (takes precedence over XML resource if set).
     */
    @JvmStatic
    @Volatile
    var billingLicenseKey: String? = null

    /**
     * Programmatic Monthly subscription Product/SKU ID.
     */
    @JvmStatic
    @Volatile
    var productIdMonthly: String = "com.monthly"

    /**
     * Programmatic Yearly subscription Product/SKU ID.
     */
    @JvmStatic
    @Volatile
    var productIdYearly: String = "com.yearly"

    /**
     * Programmatic Lifetime in-app purchase Product/SKU ID.
     */
    @JvmStatic
    @Volatile
    var productIdLifetime: String = "com.lifetime"

    @JvmStatic
    fun getBillingLicenseKey(context: Context): String {
        val dynamicKey = billingLicenseKey
        if (!dynamicKey.isNullOrBlank()) {
            return dynamicKey
        }
        return runCatching { context.getString(R.string.subscription_billing_license_key) }
            .getOrDefault("")
    }

    @JvmStatic
    fun openPrivacyPolicy(context: Context) {
        val url = runCatching { context.getString(R.string.subscription_privacy_policy_url) }
            .getOrDefault("")
            .trim()
        if (url.isEmpty()) return
        val uri = Uri.parse(url)
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @JvmStatic
    fun buildHostLaunchIntent(context: Context): Intent? {
        return context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    /**
     * Callback for the host app to show an interstitial ad when exiting from splash.
     * The host app should implement this to load/show the ad and then invoke the continuation lambda.
     */
    var showInterstitialCallback: ((android.app.Activity, () -> Unit) -> Unit)? = null
}
