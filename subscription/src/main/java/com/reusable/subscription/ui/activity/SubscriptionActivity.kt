package com.reusable.subscription.ui.activity

import com.reusable.subscription.R
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.reusable.subscription.utils.SubscriptionConstant
import com.reusable.subscription.ui.utils.SubscriptionFooterHandler
import com.reusable.subscription.domain.config.SubscriptionHostConfig
import com.reusable.subscription.domain.model.SubscriptionPlanItem
import com.reusable.subscription.data.local.SubscriptionRuntimeStore
import androidx.lifecycle.lifecycleScope
import com.reusable.subscription.data.billing.BillingManager
import com.reusable.subscription.data.local.BillingDataStore
import com.reusable.subscription.domain.model.BillingState
import com.reusable.subscription.domain.model.SubscriptionProduct
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.reusable.subscription.databinding.ActivitySubscriptionBinding

/**
 * Subscription screen showing plan options (Monthly, Yearly, Lifetime) with selection,
 * pricing, trial/offer text, and BEST VALUE badge for the lifetime plan.
 *
 * Top-right close button finishes the activity via [setupCloseButton].
 * Plan data is loaded only from Google Play Billing (SKU); no fake or fallback data.
 * Plan IDs and product IDs come from [SubscriptionConstant].
 * Uses [BillingProcessor] from InAppSubcriptions for Play Billing (purchase, subscribe, SKU details).
 */
class SubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscriptionBinding

    companion object {
        private const val TAG = "SubscriptionPlans"
        const val EXTRA_FROM_SPLASH = "extra_from_splash"
    }

    private var plans: List<SubscriptionPlanItem> = emptyList()

    private var selectedPlanId: String = SubscriptionConstant.PLAN_YEARLY

    /** Map of plan ID to its card [LinearLayout] for updating selection state. */
    private val planCards = mutableMapOf<String, LinearLayout>()

    private lateinit var billingManager: BillingManager
    private lateinit var billingDataStore: BillingDataStore
    private var subscriptionProducts: List<SubscriptionProduct> = emptyList()
    private var purchaseLoadingDialog: Dialog? = null
    private var isHandlingSplashExit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fallback init in case splash did not preload runtime subscription data.
        SubscriptionRuntimeStore.initializeOnSplash(this)
        supportActionBar?.hide()
        setupBackPressHandler()
        setupCloseButton()
        applyResponsiveLayout()
        val containerPlans = binding.containerPlans
        plans = getLoadingPlans()
        SubscriptionRuntimeStore.updatePlans(this, plans)
        setupPlanCards(containerPlans, plans)
        showSubscribeButtonLoadingState()
        billingDataStore = BillingDataStore(this)
        billingManager = BillingManager(this, SubscriptionHostConfig.getBillingLicenseKey(this), billingDataStore)
        initBilling()
        setupSubscribeButton()
        setupRestoreButton()
        setupFooterLinks()

        binding.root.findViewById<Button>(R.id.btn_reload_plans)?.setOnClickListener {
            retryBilling()
        }
    }

    private fun retryBilling() {
        binding.root.findViewById<View>(R.id.error_card)?.visibility = View.GONE
        binding.root.findViewById<View>(R.id.plans_card)?.visibility = View.VISIBLE
        plans = getLoadingPlans()
        setupPlanCards(binding.containerPlans, plans)
        showSubscribeButtonLoadingState()
        val productIds = SubscriptionConstant.getSubscriptionProductIds() + SubscriptionConstant.PRODUCT_ID_LIFETIME
        billingManager.startConnection(productIds)
    }

    override fun onDestroy() {
        dismissPurchaseLoading()
        billingManager.endConnection()
        super.onDestroy()
    }

    /**
     * Returns a map of plan key ("monthly", "yearly") to trial text for [DisplayTermOfUseActivity].
     * Used by [SubscriptionFooterHandler] when opening Terms of Service.
     */
    fun getTrialInfoMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        plans.find { it.planId == SubscriptionConstant.PLAN_MONTHLY }?.trialText?.let { if (it.isNotBlank()) map["monthly"] = it }
        plans.find { it.planId == SubscriptionConstant.PLAN_YEARLY }?.trialText?.let { if (it.isNotBlank()) map["yearly"] = it }
        return map
    }

    private fun setupFooterLinks() {
        SubscriptionFooterHandler(this, binding.tvTerms, binding.tvPrivacyPolicy)
    }

    private fun applyResponsiveLayout() {
        val screenHeightDp = resources.configuration.screenHeightDp
        if (screenHeightDp > 900) return

        val isVeryCompact = screenHeightDp <= 640
        val isCompact = screenHeightDp <= 760

        binding.subscriptionRoot?.setPadding(
            if (isCompact) dp(16) else dp(20),
            if (isCompact) dp(12) else dp(16),
            if (isCompact) dp(16) else dp(20),
            if (isCompact) dp(12) else dp(16)
        )

        binding.iconPremium?.let { icon ->
            icon.layoutParams = icon.layoutParams.apply {
                width = when {
                    isVeryCompact -> dp(32)
                    isCompact -> dp(36)
                    else -> dp(40)
                }
                height = width
            }
            val iconPadding = when {
                isVeryCompact -> dp(7)
                isCompact -> dp(8)
                else -> dp(9)
            }
            icon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        }

        binding.tvHeadline?.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            if (isCompact) 20f else 21f
        )
        binding.tvChoosePlan?.let { choosePlan ->
            choosePlan.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isCompact) 15f else 16f)
            (choosePlan.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                lp.topMargin = dp(4)
                choosePlan.layoutParams = lp
            }
        }


        binding.scrollPlans?.let { scrollPlans ->
            (scrollPlans.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                lp.topMargin = if (isCompact) dp(6) else dp(8)
                scrollPlans.layoutParams = lp
            }
        }

        binding.btnSubscribe?.let { subscribeBtn ->
            subscribeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isCompact) 15f else 16f)
            (subscribeBtn.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                lp.height = if (isCompact) dp(48) else dp(52)
                lp.topMargin = if (isCompact) dp(8) else dp(10)
                subscribeBtn.layoutParams = lp
            }
        }

        binding.bottomLinksContainer?.let { links ->
            (links.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                lp.topMargin = dp(4)
                links.layoutParams = lp
            }
            for (i in 0 until links.childCount) {
                val child = links.getChildAt(i)
                if (child is TextView) {
                    child.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    child.setPadding(dp(4), dp(4), dp(4), dp(4))
                }
            }
        }
    }

    /** Returns dummy/loading plan items for all three plans; used until real SKU data is loaded. */
    private fun getLoadingPlans(): List<SubscriptionPlanItem> {
        val loading = getString(R.string.subscription_loading)
        val placeholder = getString(R.string.subscription_loading_placeholder)
        return SubscriptionConstant.PLAN_IDS_ORDERED.map { planId ->
            SubscriptionPlanItem(
                planId = planId,
                title = loading,
                description = placeholder,
                priceText = placeholder,
                originalPrice = null,
                trialText = null,
                trialAvailable = false,
                offerSubtitle = null,
                features = null,
                isBestValue = false,
                savingsPercent = null
            )
        }
    }

    /** Enables or disables the Subscribe button and updates its CTA text. */
    private fun setSubscribeButtonEnabled(enabled: Boolean) {
        binding.btnSubscribe.apply {
            isEnabled = enabled
            text = if (enabled) {
                getSubscribeButtonLabel()
            } else {
                getString(R.string.subscription_btn_loading)
            }
        }
    }

    private fun showSubscribeButtonLoadingState() {
        binding.btnSubscribe.apply {
            isEnabled = false
            text = getString(R.string.subscription_btn_loading)
        }
    }

    private fun refreshSubscribeButtonState() {
        val hasSelectablePlan = plans.any { it.planId == selectedPlanId }
        binding.btnSubscribe.apply {
            isEnabled = hasSelectablePlan
            text = if (hasSelectablePlan) {
                getSubscribeButtonLabel()
            } else {
                getString(R.string.subscription_btn_loading)
            }
        }
    }

    private fun getSubscribeButtonLabel(): String {
        val selectedPlan = plans.firstOrNull { it.planId == selectedPlanId }
            ?: return getString(R.string.subscription_btn_loading)
        return when {
            selectedPlan.planId == SubscriptionConstant.PLAN_LIFETIME ->
                getString(R.string.subscription_btn_lifetime)
            selectedPlan.showTrial ->
                getString(R.string.subscription_btn_trial)
            selectedPlan.showDiscountedPrice || selectedPlan.showSavingsPercent || selectedPlan.showOfferSubtitle ->
                getString(R.string.subscription_btn_offer)
            else ->
                getString(R.string.subscription_btn_subscribe_now)
        }
    }


        private fun initBilling() {
        val productIds = SubscriptionConstant.getSubscriptionProductIds() + SubscriptionConstant.PRODUCT_ID_LIFETIME
        billingManager.startConnection(productIds)

        lifecycleScope.launch {
            billingManager.products.collectLatest { products ->
                if (products != null) {
                    subscriptionProducts = products
                    val containerPlans = binding.containerPlans
                    plans = buildPlansFromSubscriptionProducts(products)
                    SubscriptionRuntimeStore.updatePlans(this@SubscriptionActivity, plans)
                    setupPlanCards(containerPlans, plans)
                    selectFirstAvailablePlan()
                    setSubscribeButtonEnabled(plans.isNotEmpty())
                    showNoPlansMessageIfNeeded()
                    
                    if (plans.isEmpty()) {
                        binding.root.findViewById<View>(R.id.plans_card)?.visibility = View.GONE
                        binding.root.findViewById<View>(R.id.error_card)?.visibility = View.VISIBLE
                    } else {
                        binding.root.findViewById<View>(R.id.plans_card)?.visibility = View.VISIBLE
                        binding.root.findViewById<View>(R.id.error_card)?.visibility = View.GONE
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            billingManager.entitlementGranted.collectLatest {
                dismissPurchaseLoading()
                finishAfterEntitlementGranted()
            }
        }

        lifecycleScope.launch {
            billingManager.billingState.collectLatest { state ->
                if (state is BillingState.Error) {
                    dismissPurchaseLoading()
                    // Show error state if plans are empty and we hit an error
                    if (plans.isEmpty() || plans.firstOrNull()?.title == getString(R.string.subscription_loading)) {
                        binding.root.findViewById<View>(R.id.plans_card)?.visibility = View.GONE
                        binding.root.findViewById<View>(R.id.error_card)?.visibility = View.VISIBLE
                    }
                }
            }
        }

    }

    private fun finishAfterEntitlementGranted() {
        if (isOpenedFromSplash()) {
            openHostLaunchActivity()
            return
        }
        setResult(Activity.RESULT_OK)
        finish()
    }

    
    private fun selectFirstAvailablePlan() {
        selectedPlanId = plans
            .firstOrNull { it.showTrial }?.planId
            ?: plans.firstOrNull { it.showDiscountedPrice || it.showSavingsPercent || it.showOfferSubtitle }?.planId
            ?: plans.firstOrNull { it.planId == selectedPlanId }?.planId
            ?: plans.firstOrNull { it.planId == SubscriptionConstant.PLAN_YEARLY }?.planId
            ?: plans.firstOrNull()?.planId
            ?: SubscriptionConstant.PLAN_YEARLY
        selectPlan(selectedPlanId)
    }

    private fun showNoPlansMessageIfNeeded() {
        if (plans.isNotEmpty()) return
        Toast.makeText(this, getString(R.string.subscription_no_plans_available), Toast.LENGTH_SHORT).show()
    }

    private fun buildPlansFromSubscriptionProducts(skuList: List<SubscriptionProduct>): List<SubscriptionPlanItem> {
        val remaining = skuList.toMutableList()
        val result = SubscriptionConstant.PLAN_IDS_ORDERED.mapNotNull { planId ->
            val productId = SubscriptionConstant.getProductIdForPlan(planId)
            val index = remaining.indexOfFirst { it.productId == productId }
            if (index < 0) return@mapNotNull null
            val sku = remaining.removeAt(index)
            productToPlanItem(sku, planId)
        }
        return result
    }

    private fun productToPlanItem(product: SubscriptionProduct, planId: String): SubscriptionPlanItem {
        val title = resolvePlanTitle(product, planId)
        val periodDisplay = formatBillingPeriodDisplay(product.subscriptionPeriod)
        val slash = getString(R.string.subscription_separator_slash)
        val priceText = if (periodDisplay.isNotBlank()) "${product.formattedPrice}$slash$periodDisplay" else product.formattedPrice
        val description = when (planId) {
            SubscriptionConstant.PLAN_MONTHLY -> getString(R.string.subscription_plan_monthly_desc)
            SubscriptionConstant.PLAN_YEARLY -> getString(R.string.subscription_plan_yearly_desc)
            SubscriptionConstant.PLAN_LIFETIME -> getString(R.string.subscription_plan_lifetime_desc)
            else -> product.description
        }
        return SubscriptionPlanItem(
            planId = planId,
            title = title,
            description = description,
            priceText = priceText,
            originalPrice = null,
            trialText = product.trialDescription,
            trialAvailable = product.hasTrial,
            offerSubtitle = null,
            features = null,
            isBestValue = planId == SubscriptionConstant.PLAN_LIFETIME,
            savingsPercent = null
        )
    }

    private fun resolvePlanTitle(product: SubscriptionProduct, planId: String): String {
        if (planId == SubscriptionConstant.PLAN_LIFETIME) {
            return getString(R.string.subscription_plan_lifetime)
        }

        val playTitle = sanitizePlayTitle(product.title)
        if (playTitle.isNotBlank()) return playTitle

        return when {
            !product.subscriptionPeriod.isNullOrBlank() -> buildRecurringPlanTitle(product.subscriptionPeriod)
            else -> fallbackPlanTitle(planId)
        }
    }

    private fun sanitizePlayTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        return title
            .replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
            .trim()
    }

    private fun fallbackPlanTitle(planId: String): String {
        return when (planId) {
            SubscriptionConstant.PLAN_MONTHLY -> getString(R.string.subscription_plan_monthly)
            SubscriptionConstant.PLAN_YEARLY -> getString(R.string.subscription_plan_yearly)
            SubscriptionConstant.PLAN_LIFETIME -> getString(R.string.subscription_plan_lifetime)
            else -> planId
        }
    }

    private fun buildRecurringPlanTitle(period: String?): String {
        if (period.isNullOrBlank()) return ""
        val match = Regex("^P(\\d+)([DWMY])$").matchEntire(period) ?: return ""
        val quantity = match.groupValues[1].toIntOrNull() ?: return ""
        val unit = match.groupValues[2]

        return when (unit) {
            "D" -> if (quantity == 1) getString(R.string.subscription_daily_plan) else getString(R.string.subscription_days_plan, quantity)
            "W" -> if (quantity == 1) getString(R.string.subscription_weekly_plan) else getString(R.string.subscription_weeks_plan, quantity)
            "M" -> if (quantity == 1) getString(R.string.subscription_plan_monthly) else getString(R.string.subscription_months_plan, quantity)
            "Y" -> if (quantity == 1) getString(R.string.subscription_plan_yearly) else getString(R.string.subscription_years_plan, quantity)
            else -> ""
        }
    }

    /** Converts Play billing period (e.g. P1M, P1Y) to short display string. */
    private fun formatBillingPeriodDisplay(period: String?): String {
        if (period.isNullOrBlank()) return ""
        return formatIsoPeriod(period, displayWithSlash = false)
    }

    private fun formatBillingPeriodForSentence(period: String?): String {
        if (period.isNullOrBlank()) {
            return getString(R.string.subscription_period_month)
        }
        return formatIsoPeriod(period, displayWithSlash = false)
    }

    private fun formatIsoPeriod(period: String, displayWithSlash: Boolean): String {
        val match = Regex("^P(\\d+)([DWMY])$").matchEntire(period) ?: return period
        val quantity = match.groupValues[1].toIntOrNull() ?: return period
        val unit = match.groupValues[2]
        return when (unit) {
            "D" -> formatPeriodLabel(
                quantity = quantity,
                singularRes = if (displayWithSlash) R.string.subscription_unit_day else R.string.subscription_period_day,
                pluralRes = if (displayWithSlash) R.plurals.subscription_units_days else R.plurals.subscription_period_days
            )
            "W" -> formatPeriodLabel(
                quantity = quantity,
                singularRes = if (displayWithSlash) R.string.subscription_unit_week else R.string.subscription_period_week,
                pluralRes = if (displayWithSlash) R.plurals.subscription_units_weeks else R.plurals.subscription_period_weeks
            )
            "M" -> formatPeriodLabel(
                quantity = quantity,
                singularRes = if (displayWithSlash) R.string.subscription_unit_month else R.string.subscription_period_month,
                pluralRes = if (displayWithSlash) R.plurals.subscription_units_months else R.plurals.subscription_period_months
            )
            "Y" -> formatPeriodLabel(
                quantity = quantity,
                singularRes = if (displayWithSlash) R.string.subscription_unit_year else R.string.subscription_period_year,
                pluralRes = if (displayWithSlash) R.plurals.subscription_units_years else R.plurals.subscription_period_years
            )
            else -> period
        }
    }

    private fun formatPeriodLabel(quantity: Int, singularRes: Int, pluralRes: Int): String {
        return if (quantity == 1) {
            getString(singularRes)
        } else {
            resources.getQuantityString(pluralRes, quantity, quantity)
        }
    }

    private fun computeSavingsPercent(regularMicros: Long, introMicros: Long): Int? {
        if (regularMicros <= 0 || introMicros <= 0) return null
        if (introMicros >= regularMicros) return null
        val savings = ((regularMicros - introMicros).toDouble() / regularMicros.toDouble()) * 100.0
        return savings.toInt().coerceIn(1, 99)
    }

    /**
     * Configures the top-right close button to finish the activity.
     * Uses [androidx.activity.OnBackPressedDispatcher] so back animation and stack behavior are consistent.
     */
    private fun setupCloseButton() {
        binding.btnClose.setOnClickListener {
            handleCloseAction()
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this) {
            handleCloseAction()
        }
    }

    private fun handleCloseAction() {
        if (isOpenedFromSplash()) {
            openHostLaunchActivity()
            return
        }
        finish()
    }

    private fun isOpenedFromSplash(): Boolean =
        intent?.getBooleanExtra(EXTRA_FROM_SPLASH, false) == true

    private fun openHostLaunchActivity() {
        if (isHandlingSplashExit || isFinishing) return
        isHandlingSplashExit = true
        
        val proceedToHost: () -> Unit = {
            val mainIntent = SubscriptionHostConfig.buildHostLaunchIntent(this)
            if (mainIntent == null) {
                finish()
            } else {
                startActivity(mainIntent)
                finish()
            }
        }

        if (SubscriptionConstant.showInterstitialOnSplashExit && SubscriptionHostConfig.showInterstitialCallback != null) {
            SubscriptionHostConfig.showInterstitialCallback?.invoke(this) {
                proceedToHost()
            }
        } else {
            proceedToHost()
        }
    }

    /**
     * Clears [container], then inflates one plan card per [plans] and adds it to [container].
     * Uses [getLayoutResForPlan] to pick the correct layout (lifetime vs monthly/yearly).
     *
     * @param container The [LinearLayout] that holds the plan cards.
     * @param plans The list of plans to display (real from Play or fallback).
     */
    private fun setupPlanCards(container: LinearLayout, plans: List<SubscriptionPlanItem>) {
        this.plans = plans
        planCards.clear()
        container.removeAllViews()
        val useCompactCards = shouldUseCompactCards()
        val verticalSpacing = if (useCompactCards) dp(4) else dp(6)
        for ((index, plan) in plans.withIndex()) {
            val root = inflatePlanCard(plan, container)
            root.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (index == 0) 0 else verticalSpacing
            }
            val cardPlan = root.findViewById<LinearLayout>(R.id.card_plan)
            cardPlan.setOnClickListener { selectPlan(plan.planId) }
            planCards[plan.planId] = cardPlan
            if (useCompactCards) {
                applyCompactPlanCard(root)
            }
            bindPlanToView(plan, root)
            container.addView(root)
        }
    }

    private fun shouldUseCompactCards(): Boolean = resources.configuration.screenHeightDp <= 900

    private fun applyCompactPlanCard(root: View) {
        root.findViewById<LinearLayout>(R.id.card_plan)?.setPadding(
            dp(12),
            dp(9),
            dp(12),
            dp(9)
        )

        root.findViewById<TextView>(R.id.tv_plan_title)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        root.findViewById<TextView>(R.id.tv_plan_price)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        root.findViewById<TextView>(R.id.tv_plan_period)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        root.findViewById<TextView>(R.id.tv_plan_original_price)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        root.findViewById<TextView>(R.id.tv_plan_save)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        root.findViewById<TextView>(R.id.tv_plan_desc)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        root.findViewById<TextView>(R.id.tv_plan_subtitle)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        root.findViewById<TextView>(R.id.tv_plan_features)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)

        makeSingleLine(root.findViewById(R.id.tv_plan_desc))
        makeSingleLine(root.findViewById(R.id.tv_plan_subtitle))
        makeSingleLine(root.findViewById(R.id.tv_plan_features))
    }

    private fun makeSingleLine(textView: TextView?) {
        textView ?: return
        textView.maxLines = 1
        textView.ellipsize = TextUtils.TruncateAt.END
    }

    /**
     * Returns the layout resource for the given [planId] (lifetime has BEST VALUE badge; others do not).
     *
     * @param planId One of [SubscriptionConstant.PLAN_MONTHLY], [SubscriptionConstant.PLAN_YEARLY], [SubscriptionConstant.PLAN_LIFETIME].
     * @return Layout resource for the plan item.
     */
    private fun getLayoutResForPlan(planId: String): Int = when (planId) {
        SubscriptionConstant.PLAN_LIFETIME -> R.layout.item_lifetime_plan
        SubscriptionConstant.PLAN_MONTHLY -> R.layout.item_monthly_plan
        SubscriptionConstant.PLAN_YEARLY -> R.layout.item_yearly_plan
        else -> R.layout.item_monthly_plan
    }

    /**
     * Inflates the plan item view for [plan] and attaches [plan.planId] as tag for later lookup.
     *
     * @param plan The plan model.
     * @param container Parent to attach the inflated view to (attach is done by caller).
     * @return The inflated root view (not yet added to [container]).
     */
    private fun inflatePlanCard(plan: SubscriptionPlanItem, container: LinearLayout): View {
        val layoutRes = getLayoutResForPlan(plan.planId)
        val root = LayoutInflater.from(this).inflate(layoutRes, container, false)
        root.setTag(R.id.card_plan, plan.planId)
        return root
    }

    /**
     * Sets up the subscribe button to launch Google Play Billing for the selected plan.
     * Lifetime uses one-time [BillingProcessor.purchase] (INAPP); monthly/yearly use [BillingProcessor.subscribe] (SUBS).
     */
    private fun setupSubscribeButton() {
        binding.btnSubscribe.setOnClickListener {
            if (plans.none { it.planId == selectedPlanId }) {
                Toast.makeText(this, getString(R.string.general_error), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (com.reusable.subscription.domain.manager.SubscriptionEntitlementManager.hasSubscriptionState(this) ||
                com.reusable.subscription.domain.manager.SubscriptionEntitlementManager.hasLifetimeState(this)) {
                finishAfterEntitlementGranted()
                return@setOnClickListener
            }
            val productId = SubscriptionConstant.getProductIdForPlan(selectedPlanId)
            val product = subscriptionProducts.find { it.productId == productId }
            if (product != null) {
                showPurchaseLoading()
                billingManager.launchBillingFlow(this, product)
            } else {
                Toast.makeText(this, getString(R.string.general_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Restore: re-queries purchases from Play and updates prefs; shows toast on success. */
    private fun setupRestoreButton() {
        val btnRestore = binding.root.findViewById<View>(R.id.tv_restore)
        btnRestore?.setOnClickListener {
            billingManager.restorePurchases()
            Toast.makeText(this, getString(R.string.subscription_restore_no_active), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Binds a [SubscriptionPlanItem] to an inflated plan item view.
     * Handles lifetime, monthly, and yearly layouts (some views like [R.id.badge_best_value]
     * or [R.id.tv_plan_period] may be null depending on layout).
     *
     * @param plan The plan data.
     * @param root The inflated plan item root view.
     */
    private fun bindPlanToView(plan: SubscriptionPlanItem, root: View) {
        bindPlanTitleAndRadio(plan, root)
        bindPlanPriceAndPeriod(plan, root)
        bindPlanDescription(plan, root)
        bindPlanSubtitle(plan, root)
        bindPlanOriginalPriceAndSave(plan, root)
        bindPlanFeatures(plan, root)
        bindPlanBadge(plan, root)
        updateCardSelectionState(plan, root)
    }

    /**
     * Sets plan title and radio checked state from [plan].
     */
    private fun bindPlanTitleAndRadio(plan: SubscriptionPlanItem, root: View) {
        root.findViewById<TextView>(R.id.tv_plan_title).text = plan.title
        root.findViewById<RadioButton>(R.id.radio_plan).isChecked = (plan.planId == selectedPlanId)
    }

    /**
     * Sets price text; if layout has [R.id.tv_plan_period], splits into price + period.
     * When plan has introductory/discounted price (originalPrice set): show discounted as main price,
     * and period from priceText. Otherwise: priceText is the regular price, split into price + period.
     */
    private fun bindPlanPriceAndPeriod(plan: SubscriptionPlanItem, root: View) {
        val tvPrice = root.findViewById<TextView>(R.id.tv_plan_price)
        val tvPeriod = root.findViewById<TextView>(R.id.tv_plan_period)
        val hasIntroPrice = !plan.originalPrice.isNullOrBlank()
        if (tvPeriod != null && plan.priceText.contains(getString(R.string.subscription_separator_slash))) {
            val parts = plan.priceText.split(getString(R.string.subscription_separator_slash), limit = 2)
            val periodPart = "${getString(R.string.subscription_separator_slash)}${parts[1]}"
            if (hasIntroPrice) {
                // Play: priceText = regular price, originalPrice = discounted â†’ show discounted as main
                tvPrice.text = plan.originalPrice
                tvPeriod.text = periodPart
                tvPeriod.visibility = View.VISIBLE
            } else {
                tvPrice.text = parts[0]
                tvPeriod.text = periodPart
                tvPeriod.visibility = View.VISIBLE
            }
        } else {
            if (hasIntroPrice) {
                tvPrice.text = plan.originalPrice
                tvPeriod?.visibility = View.GONE
            } else {
                tvPrice.text = plan.priceText
                tvPeriod?.visibility = View.GONE
            }
        }
    }

    /**
     * Sets description text on [R.id.tv_plan_desc] if present (monthly/yearly layouts).
     */
    private fun bindPlanDescription(plan: SubscriptionPlanItem, root: View) {
        val tvDesc = root.findViewById<TextView>(R.id.tv_plan_desc) ?: return
        if (plan.description.isNotBlank()) {
            tvDesc.visibility = View.VISIBLE
            tvDesc.text = plan.description
        } else {
            tvDesc.visibility = View.GONE
        }
    }

    /**
     * Sets subtitle (trial text or offer subtitle) on [R.id.tv_plan_subtitle].
     */
    private fun bindPlanSubtitle(plan: SubscriptionPlanItem, root: View) {
        val tvSubtitle = root.findViewById<TextView>(R.id.tv_plan_subtitle)
        when {
            plan.showTrial -> {
                tvSubtitle.visibility = View.VISIBLE
                tvSubtitle.text = plan.trialText
            }
            plan.showOfferSubtitle && plan.offerSubtitle != null -> {
                tvSubtitle.visibility = View.VISIBLE
                tvSubtitle.text = plan.offerSubtitle
            }
            else -> tvSubtitle.visibility = View.GONE
        }
    }

    /**
     * Sets original (strikethrough) price and SAVE % chip visibility and text.
     * When plan has intro price: Play gives priceText = regular/original, originalPrice = discounted.
     * So we show priceText (regular) as strikethrough and originalPrice as main price (in bindPlanPriceAndPeriod).
     */
    private fun bindPlanOriginalPriceAndSave(plan: SubscriptionPlanItem, root: View) {
        val tvOriginalPrice = root.findViewById<TextView>(R.id.tv_plan_original_price)
        if (plan.showDiscountedPrice && plan.originalPrice != null) {
            tvOriginalPrice.visibility = View.VISIBLE
            // Strikethrough = regular/original price (from priceText); main price = discounted (originalPrice) is set in bindPlanPriceAndPeriod
            val slash = getString(R.string.subscription_separator_slash)
            val regularPriceDisplay = if (plan.priceText.contains(slash)) plan.priceText.split(slash, limit = 2)[0].trim() else plan.priceText
            tvOriginalPrice.text = regularPriceDisplay
            tvOriginalPrice.paintFlags = tvOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            tvOriginalPrice.visibility = View.GONE
            tvOriginalPrice.paintFlags = tvOriginalPrice.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        val tvSave = root.findViewById<TextView>(R.id.tv_plan_save)
        if (plan.showSavingsPercent && plan.savingsPercent != null) {
            tvSave.visibility = View.VISIBLE
            tvSave.text = getString(R.string.subscription_save_percent, plan.savingsPercent)
        } else {
            tvSave.visibility = View.GONE
        }
    }

    /**
     * Sets benefits/features text on [R.id.tv_plan_features] if present (lifetime layout).
     * Builds text from [SubscriptionPlanItem.description] and [SubscriptionPlanItem.features].
     */
    private fun bindPlanFeatures(plan: SubscriptionPlanItem, root: View) {
        val tvDesc = root.findViewById<TextView>(R.id.tv_plan_desc)
        val benefits = buildString {
            if (!plan.description.isNullOrBlank() && tvDesc == null) append(plan.description)
            if (!plan.features.isNullOrBlank()) {
                if (isNotEmpty()) append("\n")
                append(plan.features)
            }
        }
        val tvFeatures = root.findViewById<TextView>(R.id.tv_plan_features) ?: return
        if (benefits.isNotEmpty()) {
            tvFeatures.visibility = View.VISIBLE
            tvFeatures.text = benefits
        } else {
            tvFeatures.visibility = View.GONE
        }
    }

    /**
     * Shows or hides the BEST VALUE badge; only lifetime layout has this view.
     */
    private fun bindPlanBadge(plan: SubscriptionPlanItem, root: View) {
        val badgeBestValue = root.findViewById<View>(R.id.badge_best_value) ?: return
        if (plan.isBestValue) {
            badgeBestValue.visibility = View.VISIBLE
            badgeBestValue.bringToFront()
            val badgeText = root.findViewById<TextView>(R.id.tv_badge_text)
            if (badgeText != null) {
                val hasLifetimeOffer = plan.planId == SubscriptionConstant.PLAN_LIFETIME && plan.showDiscountedPrice
                badgeText.text = if (hasLifetimeOffer) {
                    getString(R.string.subscription_limited_time_offer)
                } else {
                    getString(R.string.subscription_best_value)
                }
            }
        } else {
            badgeBestValue.visibility = View.GONE
        }
    }

    /**
     * Updates the card's selected state and refreshes its drawable (selection background).
     */
    private fun updateCardSelectionState(plan: SubscriptionPlanItem, root: View) {
        val cardPlan = root.findViewById<LinearLayout>(R.id.card_plan)
        cardPlan.isSelected = (plan.planId == selectedPlanId)
        cardPlan.refreshDrawableState()
    }

    /**
     * Marks [planId] as the selected plan and updates all card backgrounds and radio buttons.
     *
     * @param planId One of [SubscriptionConstant.PLAN_MONTHLY], [SubscriptionConstant.PLAN_YEARLY], [SubscriptionConstant.PLAN_LIFETIME].
     */
    private fun selectPlan(planId: String) {
        selectedPlanId = planId
        updateAllCardsSelectionState(planId)
        updateAllRadios(planId)
        refreshSubscribeButtonState()
    }

    /**
     * Sets [View.isSelected] and [View.refreshDrawableState] on each plan card based on [planId].
     */
    private fun updateAllCardsSelectionState(planId: String) {
        planCards.forEach { (id, card) ->
            card.isSelected = (id == planId)
            card.refreshDrawableState()
        }
    }

    /**
     * Sets checked state on each plan's radio button to match [planId].
     */
    private fun updateAllRadios(planId: String) {
        val containerPlans = binding.containerPlans
        for (i in 0 until containerPlans.childCount) {
            val child = containerPlans.getChildAt(i)
            val tagPlanId = child.getTag(R.id.card_plan) as? String ?: continue
            child.findViewById<RadioButton>(R.id.radio_plan).isChecked = (tagPlanId == planId)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showPurchaseLoading() {
        if (purchaseLoadingDialog?.isShowing == true) return
        purchaseLoadingDialog = Dialog(this, R.style.TransparentDialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.progress_loader)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            window?.setGravity(Gravity.CENTER)
            setCancelable(false)
            show()
        }
    }

    private fun dismissPurchaseLoading() {
        purchaseLoadingDialog?.let {
            if (it.isShowing) it.dismiss()
        }
        purchaseLoadingDialog = null
    }
}


