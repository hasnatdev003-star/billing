package com.reusable.subscription.domain.config

/**
 * Provides English-only subscription terms HTML for the subscription screen.
 * Used by [DisplayTermOfUseActivity]. No localization; all content is in English.
 *
 * Plans: Monthly, Yearly, Lifetime (one-time). Trial text per plan comes from [trialInfo].
 */
object SubscriptionPolicyProvider {

    /**
     * Returns HTML for subscription terms in English only.
     *
     * @param trialInfo Map of plan key to trial text, e.g. "monthly" -> "3-day free trial", "yearly" -> "7-day free trial". Empty if no trials.
     */
    @JvmStatic
    fun getSubscriptionPolicyHtml(trialInfo: Map<String, String> = emptyMap()): String {
        return getEnglishPolicy(trialInfo)
    }

    private fun getEnglishPolicy(trialInfo: Map<String, String>): String {
        fun cleanTrialText(trial: String?): String {
            if (trial.isNullOrBlank()) return ""
            val cleaned = trial.trim().removePrefix("(").removeSuffix(")")
            val lower = cleaned.lowercase()
            return if (lower.contains("trial")) cleaned else "$cleaned free trial"
        }

        fun planLine(name: String, trialKey: String): String {
            val trial = trialInfo[trialKey]
            return if (trial.isNullOrBlank()) {
                "<li><strong>$name</strong>: Billed every ${trialKey.replace("ly", "")} on the same day.</li>"
            } else {
                val trialText = cleanTrialText(trial)
                "<li><strong>$name</strong>: Billed every ${trialKey.replace("ly", "")} on the same day. <b>Includes $trialText</b> (after the trial period, billing starts automatically unless you cancel).</li>"
            }
        }

        val monthlyLine = planLine("Monthly Access", "monthly")
        val yearlyLine = planLine("Yearly Access", "yearly")
        val lifetimeLine = "<li><strong>Lifetime (One-time)</strong>: One-time purchase. No recurring billing. You keep premium access indefinitely.</li>"

        val availableTrials = trialInfo.filterValues { it.isNotBlank() }
        val trialSection = if (availableTrials.isNotEmpty()) {
            """
        <h3>Free Trial</h3>
        <p>
            Certain subscription plans include a free trial period. If your selected plan offers a free trial, you will not be charged until the trial period ends. You can cancel anytime during the trial to avoid charges. At the end of the free trial, your subscription will begin and you will be charged automatically, unless you cancel beforehand.
        </p>
        """.trimIndent()
        } else {
            """
        <h3>No Free Trial</h3>
        <p>
            Our app may not offer a free trial period for some plans. The subscription fee will be charged as described at the time of purchase.
        </p>
        """.trimIndent()
        }

        return """
        <h2>Subscriptions &amp; Payment Terms</h2>
        <h3>Subscription Plans</h3>
        <p>Our app offers subscription and one-time options:</p>
        <ul>
            $monthlyLine
            $yearlyLine
            $lifetimeLine
        </ul>
        <h3>Payment</h3>
        <ul>
            <li>Payment for the subscription or one-time purchase will be charged to your Google Play account at the time of purchase confirmation.</li>
            <li>Your subscription will automatically renew at the end of the subscription period unless you cancel it at least 24 hours before the renewal date.</li>
            <li>The renewal charge will be the same as the original subscription fee, unless you are notified of a change in price.</li>
        </ul>
        <h3>Automatic Renewal &amp; Cancellation</h3>
        <ul>
            <li>Subscriptions renew automatically unless canceled at least 24 hours before the renewal date.</li>
            <li>You can manage or cancel your subscription by going to <strong>Google Play Store &gt; Payments &amp; Subscriptions &gt; Subscriptions</strong> and selecting the subscription you wish to cancel.</li>
            <li>If you cancel your subscription, you will still have access to the premium features until the end of your current billing cycle. No refunds will be issued for the remaining period after cancellation.</li>
        </ul>
        $trialSection
        <h3>Refunds</h3>
        <p>
            Subscription payments are non-refundable. All billing and transactions are handled by Google Play, and any issues related to payments or refunds are subject to their policies.
        </p>
    """.trimIndent()
    }
}

