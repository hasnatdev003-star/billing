package com.reusable.subscription.data.local

import android.content.Context
import com.reusable.subscription.R
import com.reusable.subscription.domain.model.SubscriptionPlanItem
import com.reusable.subscription.utils.SubscriptionConstant
import org.json.JSONArray
import org.json.JSONObject

/**
 * In-memory + cached runtime store for subscription plans and offer metadata.
 * Splash initializes this store from cache so MainActivity can immediately react to offers.
 */
object SubscriptionRuntimeStore {

    private const val PREF_KEY_RUNTIME_PLANS = "subscription_runtime_plans_json"
    private var plans: List<SubscriptionPlanItem> = emptyList()
    @JvmStatic
    var isSubscriptionDataFetched: Boolean = false
        private set

    @JvmStatic
    fun initializeOnSplash(context: Context) {
        val cached = loadFromCache(context)
        plans = if (cached.isNotEmpty()) cached else buildFallbackPlans(context)
        isSubscriptionDataFetched = cached.isNotEmpty()
        if (cached.isEmpty()) {
            saveToCache(context, plans)
        }
    }

    @JvmStatic
    fun updatePlans(context: Context, planItems: List<SubscriptionPlanItem>) {
        plans = planItems
        saveToCache(context, planItems)
        isSubscriptionDataFetched = true
    }

    @JvmStatic
    fun getPlans(): List<SubscriptionPlanItem> = plans

    @JvmStatic
    fun doesAnyOfferExist(): Boolean {
        return plans.any { plan ->
            plan.showTrial || plan.showOfferSubtitle || plan.showDiscountedPrice || plan.showSavingsPercent
        }
    }

    private fun buildFallbackPlans(context: Context): List<SubscriptionPlanItem> {
        return listOf(
            SubscriptionPlanItem(
                planId = SubscriptionConstant.PLAN_MONTHLY,
                title = context.getString(R.string.subscription_plan_monthly),
                description = context.getString(R.string.subscription_plan_monthly_desc),
                priceText = context.getString(R.string.subscription_loading_placeholder)
            ),
            SubscriptionPlanItem(
                planId = SubscriptionConstant.PLAN_YEARLY,
                title = context.getString(R.string.subscription_plan_yearly),
                description = context.getString(R.string.subscription_plan_yearly_desc),
                priceText = context.getString(R.string.subscription_loading_placeholder)
            ),
            SubscriptionPlanItem(
                planId = SubscriptionConstant.PLAN_LIFETIME,
                title = context.getString(R.string.subscription_plan_lifetime),
                description = context.getString(R.string.subscription_plan_lifetime_desc),
                priceText = context.getString(R.string.subscription_loading_placeholder)
            )
        )
    }

    private fun loadFromCache(context: Context): List<SubscriptionPlanItem> {
        val json = SubscriptionPreferences.getString(
            context.applicationContext,
            PREF_KEY_RUNTIME_PLANS,
            ""
        )
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            val parsed = mutableListOf<SubscriptionPlanItem>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                parsed.add(
                    SubscriptionPlanItem(
                        planId = item.optString("planId"),
                        title = item.optString("title"),
                        description = item.optString("description"),
                        priceText = item.optString("priceText"),
                        originalPrice = item.optString("originalPrice").takeIf { it.isNotBlank() },
                        trialText = item.optString("trialText").takeIf { it.isNotBlank() },
                        trialAvailable = item.optBoolean("trialAvailable", false),
                        offerSubtitle = item.optString("offerSubtitle").takeIf { it.isNotBlank() },
                        features = item.optString("features").takeIf { it.isNotBlank() },
                        isBestValue = item.optBoolean("isBestValue", false),
                        savingsPercent = if (item.has("savingsPercent")) item.optInt("savingsPercent") else null
                    )
                )
            }
            parsed
        }.getOrDefault(emptyList())
    }

    private fun saveToCache(context: Context, planItems: List<SubscriptionPlanItem>) {
        val array = JSONArray()
        planItems.forEach { plan ->
            val obj = JSONObject()
                .put("planId", plan.planId)
                .put("title", plan.title)
                .put("description", plan.description)
                .put("priceText", plan.priceText)
                .put("originalPrice", plan.originalPrice ?: "")
                .put("trialText", plan.trialText ?: "")
                .put("trialAvailable", plan.trialAvailable)
                .put("offerSubtitle", plan.offerSubtitle ?: "")
                .put("features", plan.features ?: "")
                .put("isBestValue", plan.isBestValue)
            if (plan.savingsPercent != null) {
                obj.put("savingsPercent", plan.savingsPercent)
            }
            array.put(obj)
        }
        SubscriptionPreferences.putString(
            context.applicationContext,
            PREF_KEY_RUNTIME_PLANS,
            array.toString()
        )
    }
}

