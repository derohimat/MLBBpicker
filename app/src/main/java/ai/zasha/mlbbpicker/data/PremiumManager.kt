package ai.zasha.mlbbpicker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages premium unlock state for the app.
 * Premium state is persisted in SharedPreferences and updated by BillingManager.
 */
object PremiumManager {

    private const val PREFS_NAME = "mlbb_premium"
    private const val KEY_IS_PREMIUM = "is_premium"
    private const val KEY_PLAN_TYPE = "plan_type" // "lifetime" or "monthly"
    private const val KEY_PURCHASE_TOKEN = "purchase_token"

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _planType = MutableStateFlow("")
    val planType: StateFlow<String> = _planType.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isPremium.value = prefs?.getBoolean(KEY_IS_PREMIUM, false) ?: false
        _planType.value = prefs?.getString(KEY_PLAN_TYPE, "") ?: ""
    }

    /**
     * Called by BillingManager when a purchase is verified.
     */
    fun unlockPremium(planType: String, purchaseToken: String) {
        prefs?.edit()?.apply {
            putBoolean(KEY_IS_PREMIUM, true)
            putString(KEY_PLAN_TYPE, planType)
            putString(KEY_PURCHASE_TOKEN, purchaseToken)
            apply()
        }
        _isPremium.value = true
        _planType.value = planType
    }

    /**
     * Called when a subscription expires or is revoked.
     */
    fun revokePremium() {
        prefs?.edit()?.apply {
            putBoolean(KEY_IS_PREMIUM, false)
            putString(KEY_PLAN_TYPE, "")
            putString(KEY_PURCHASE_TOKEN, "")
            apply()
        }
        _isPremium.value = false
        _planType.value = ""
    }

    /**
     * Check if a specific feature is available.
     * All premium features require isPremium to be true.
     */
    fun isFeatureAvailable(feature: PremiumFeature): Boolean {
        return _isPremium.value
    }
}

enum class PremiumFeature {
    SOLO_QUEUE,
    FULL_SCREEN_DRAFT,
    AD_FREE,
    PRIORITY_OTA,
    ADVANCED_ANALYTICS,
    CUSTOM_THEMES
}
