package com.reusable.subscription.ui.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import com.reusable.subscription.ui.activity.DisplayTermOfUseActivity
import com.reusable.subscription.ui.activity.SubscriptionActivity
import com.reusable.subscription.domain.config.SubscriptionHostConfig

class SubscriptionFooterHandler(
    private val activity: Activity,
    private val termsView: TextView?,
    private val privacyView: TextView?
) {

    init {
        attachListeners()
    }

    private fun attachListeners() {
        termsView?.setOnClickListener { openTermsOfService() }
        privacyView?.setOnClickListener { openPrivacyPolicy() }
    }

    private fun openTermsOfService() {
        val intent = Intent(activity, DisplayTermOfUseActivity::class.java)
        if (activity is SubscriptionActivity) {
            val trialInfo = activity.getTrialInfoMap()
            intent.putExtra("trialInfo", HashMap(trialInfo))
        }
        activity.startActivity(intent)
    }

    private fun openPrivacyPolicy() {
        SubscriptionHostConfig.openPrivacyPolicy(activity)
    }
}
