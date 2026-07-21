package com.reusable.subscription.domain.manager

import android.content.Context
import com.reusable.subscription.data.local.SubscriptionPreferences
import com.reusable.subscription.utils.SubscriptionConstant

/**
 * Keeps all premium entitlement flags in sync across app modules.
 */
object SubscriptionEntitlementManager {

    @JvmStatic
    fun applyEntitlement(context: Context, hasEntitlement: Boolean) {
        val appContext = context.applicationContext
        SubscriptionPreferences.putBoolean(appContext, SubscriptionConstant.PREF_KEY_REMOVE_ADS, hasEntitlement)
    }

    @JvmStatic
    fun applySubscriptionState(context: Context, hasSubscription: Boolean) {
        val appContext = context.applicationContext
        SubscriptionPreferences.putBoolean(
            appContext,
            SubscriptionConstant.PREF_KEY_HAS_SUBSCRIPTION,
            hasSubscription
        )
        applyEntitlement(appContext, hasSubscription || hasLifetimeState(appContext))
    }

    @JvmStatic
    fun applyLifetimeState(context: Context, hasLifetime: Boolean) {
        val appContext = context.applicationContext
        SubscriptionPreferences.putBoolean(
            appContext,
            SubscriptionConstant.PREF_KEY_HAS_LIFETIME_INAPP,
            hasLifetime
        )
        applyEntitlement(appContext, hasLifetime || hasSubscriptionState(appContext))
    }

    @JvmStatic
    fun hasSubscriptionState(context: Context): Boolean {
        return SubscriptionPreferences.getBoolean(
            context.applicationContext,
            SubscriptionConstant.PREF_KEY_HAS_SUBSCRIPTION,
            false
        )
    }

    @JvmStatic
    fun hasLifetimeState(context: Context): Boolean {
        return SubscriptionPreferences.getBoolean(
            context.applicationContext,
            SubscriptionConstant.PREF_KEY_HAS_LIFETIME_INAPP,
            false
        )
    }
}
