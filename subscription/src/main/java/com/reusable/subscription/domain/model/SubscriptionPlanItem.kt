package com.reusable.subscription.domain.model

/**
 * Data model for a subscription plan. Used by [SubscriptionPlanDataSource] and the subscription UI.
 * Trial, discounted price, BEST VALUE badge, and SAVE % are optional; UI shows them only when data is present.
 */
data class SubscriptionPlanItem(
    /** Billing plan ID (e.g. monthlyadv, yearlyadv, com.ads.free.advance). */
    val planId: String,
    /** Display title (e.g. "Monthly Plan"). */
    val title: String,
    /** Short description (e.g. "Cancel anytime", "Billed annually after trial"). */
    val description: String,
    /** Current / discounted price string (e.g. "150 PKR / month"). Always shown. */
    val priceText: String,
    /**
     * Original price before discount. If not null, UI shows it with strikethrough next to [priceText].
     */
    val originalPrice: String? = null,
    /** Trial offer text (e.g. "3-day free trial"). If null or blank, trial view is hidden. */
    val trialText: String? = null,
    /** Whether a trial is available. If false, trial view is hidden even if [trialText] is set. */
    val trialAvailable: Boolean = false,
    /** Offer subtitle when no trial (e.g. "Limited-time lifetime offer"). Shown in accent color. */
    val offerSubtitle: String? = null,
    /** Optional second line (e.g. "No ads • All premium features"). Hidden if null/blank. */
    val features: String? = null,
    /** Show "BEST VALUE" badge on this card. One plan should have this. */
    val isBestValue: Boolean = false,
    /** Savings percent for discount chip (e.g. 73 for "SAVE 73%"). Hidden if null. */
    val savingsPercent: Int? = null
) {
    /** True if trial UI should be shown. */
    val showTrial: Boolean get() = trialAvailable && !trialText.isNullOrBlank()

    /** True if offer subtitle (no trial) should be shown. */
    val showOfferSubtitle: Boolean get() = !offerSubtitle.isNullOrBlank()

    /** True if original (strikethrough) price should be shown. */
    val showDiscountedPrice: Boolean get() = !originalPrice.isNullOrBlank()

    /** True if SAVE X% chip should be shown. */
    val showSavingsPercent: Boolean get() = savingsPercent != null
}

