package com.reusable.subscription.utils

/**
 * Central place for all subscription-related constants.
 * Used by [SubscriptionActivity], subscription billing logic, and the subscription checker
 * so the app has a single source of truth for plan IDs, product IDs, and preference keys.
 *
 * Subscription state (is user subscribed) is persisted with [PREF_KEY_REMOVE_ADS];
 * existing code reads it via SharedPref and [Constants.InAppSuccessful]. The subscription
 * checker should update both that pref and Constants.InAppSuccessful when purchases are queried.
 */
/**
 * Central place for all subscription-related constants.
 * Used by [SubscriptionActivity], subscription billing logic, and the subscription checker
 * so the app has a single source of truth for plan IDs, product IDs, and preference keys.
 *
 * Subscription state (is user subscribed) is persisted with [PREF_KEY_REMOVE_ADS];
 * existing code reads it via SharedPref and [Constants.InAppSuccessful]. The subscription
 * checker should update both that pref and Constants.InAppSuccessful when purchases are queried.
 */
object  SubscriptionConstant {

    // -------------------------------------------------------------------------
    // Plan IDs (UI / data source â€“ map to product IDs for billing)
    // -------------------------------------------------------------------------

    /** Plan ID for monthly subscription. */
    const val PLAN_MONTHLY = "com.monthly"

    /** Plan ID for yearly subscription. */
    const val PLAN_YEARLY = "com.yearly"

    /** Plan ID for lifetime access. Same as Play product: remove.ads.smsbyvoice (one-time INAPP). */
    const val PLAN_LIFETIME = "com.lifetime"

    /** Title for monthly subscription. */
    const val TITLE_MONTHLY = "Monthly Access"

    /** Title for yearly subscription. */
    const val TITLE_YEARLY = "Yearly Access"

    /** Title for lifetime access. */
    const val TITLE_LIFETIME = "Lifetime Access"

    /** All plan IDs in display order (monthly â†’ yearly â†’ lifetime). */
    val PLAN_IDS_ORDERED: List<String> = listOf(PLAN_MONTHLY, PLAN_YEARLY, PLAN_LIFETIME)

    // -------------------------------------------------------------------------
    // Google Play product IDs (must match Play Console)
    // -------------------------------------------------------------------------

    /** Product ID for monthly subscription (SUBS). */
    val PRODUCT_ID_MONTHLY: String
        get() = com.reusable.subscription.domain.config.SubscriptionHostConfig.productIdMonthly

    /** Product ID for yearly subscription (SUBS). */
    val PRODUCT_ID_YEARLY: String
        get() = com.reusable.subscription.domain.config.SubscriptionHostConfig.productIdYearly

    /**
     * Product ID for lifetime access (one-time INAPP).
     */
    val PRODUCT_ID_LIFETIME: String
        get() = com.reusable.subscription.domain.config.SubscriptionHostConfig.productIdLifetime

    /**
     * Legacy "remove ads" (INAPP) product in Play for previously purchased users.
     */
    val PRODUCT_ID_LEGACY_AD_REMOVAL: String
        get() = "com.ads.free.advance"
    /**
     * Maps plan ID to Google Play product ID for launching billing flow.
     */
    fun getProductIdForPlan(planId: String): String = when (planId) {
        PLAN_MONTHLY -> PRODUCT_ID_MONTHLY
        PLAN_YEARLY -> PRODUCT_ID_YEARLY
        PLAN_LIFETIME -> PRODUCT_ID_LIFETIME
        else -> PRODUCT_ID_YEARLY
    }

    /**
     * Subscription product IDs (monthly, yearly only) for querying SUBS. Lifetime is INAPP ([PRODUCT_ID_LIFETIME]).
     */
    fun getSubscriptionProductIds(): List<String> = listOf(
        PRODUCT_ID_MONTHLY,
        PRODUCT_ID_YEARLY
    )

    /**
     * One-time products that should permanently unlock premium access.
     * Includes the current lifetime SKU and the older legacy remove-ads SKU.
     */
    fun getLifetimeProductIds(): List<String> = listOf(
        PRODUCT_ID_LIFETIME,
        PRODUCT_ID_LEGACY_AD_REMOVAL
    )

    // -------------------------------------------------------------------------
    // Persistence (SharedPreferences)
    // -------------------------------------------------------------------------

    /**
     * Preference key for "user has active subscription / remove ads".
     * Same key as existing app usage ("removeads") so MainActivity and ad logic keep working.
     */
    const val PREF_KEY_REMOVE_ADS = "removeads"

    /** Preference key for active recurring subscription entitlement (monthly/yearly). */
    const val PREF_KEY_HAS_SUBSCRIPTION = "pref_has_subscription"

    /** Preference key for owned lifetime/legacy one-time entitlement. */
    const val PREF_KEY_HAS_LIFETIME_INAPP = "pref_has_lifetime_inapp"

    /** Show subscription paywall after every N successful dictionary word opens for free users. */
    const val FREE_DICTIONARY_SEARCH_PAYWALL_STEP = 5

    /** Persistent count of successful dictionary word opens. */
    const val PREF_KEY_DICTIONARY_SEARCH_COUNT = "pref_dictionary_search_count"

    /** Last dictionary-search count at which paywall was already shown (prevents duplicate trigger). */
    const val PREF_KEY_LAST_DICTIONARY_PAYWALL_SHOWN_AT = "pref_last_dictionary_paywall_shown_at"

    /** Show subscription paywall after every N successful translations for free users. */
    const val FREE_TRANSLATION_PAYWALL_STEP = 5

    /** Persistent count of successful translations. */
    const val PREF_KEY_TRANSLATION_COUNT = "pref_translation_count"

    /** Last translation count at which paywall was already shown (prevents duplicate trigger). */
    const val PREF_KEY_LAST_TRANSLATION_PAYWALL_SHOWN_AT = "pref_last_translation_paywall_shown_at"

    /** App-level variable to determine whether to show an interstitial ad when exiting the subscription screen to the main app (from splash). */
    var showInterstitialOnSplashExit: Boolean = false
}
