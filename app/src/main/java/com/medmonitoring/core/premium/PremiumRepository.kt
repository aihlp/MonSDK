package com.medmonitoring.core.premium

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences("premium_status", Context.MODE_PRIVATE)
    private val clock: Clock = Clock.systemUTC()
    private val installationDate = Instant.ofEpochMilli(
        preferences.getLong(KEY_INSTALLATION_DATE, 0L).takeIf { it > 0L }
            ?: clock.instant().toEpochMilli().also {
                preferences.edit().putLong(KEY_INSTALLATION_DATE, it).apply()
            }
    )
    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<PremiumStatus> = _status.asStateFlow()

    fun refreshTrial() {
        if (_status.value !is PremiumStatus.Premium) _status.value = readTrialStatus()
    }

    fun updateSubscription(productId: String, purchaseToken: String?, expiresAt: Instant? = null) {
        preferences.edit()
            .putString(KEY_PRODUCT_ID, productId)
            .putString(KEY_PURCHASE_TOKEN, purchaseToken)
            .putLong(KEY_EXPIRY, expiresAt?.toEpochMilli() ?: 0L)
            .apply()
        _status.value = PremiumStatus.Premium(productId, purchaseToken, expiresAt)
    }

    fun applyPromoCode(code: String, config: PremiumConfig): PromoCodeResult {
        val definition = config.promoCodes.firstOrNull {
            it.code.equals(code.trim(), ignoreCase = true)
        } ?: return PromoCodeResult.INVALID
        return when (definition.reward) {
            PromoReward.LIFETIME_PREMIUM -> {
                updateSubscription(PROMO_LIFETIME_PRODUCT, null)
                PromoCodeResult.APPLIED_LIFETIME
            }
            PromoReward.EXTENDED_PREMIUM -> {
                val duration = definition.duration ?: Duration.ofDays(90)
                val currentExpiry = (status.value as? PremiumStatus.Premium)?.expiresAt
                val base = currentExpiry?.takeIf { it.isAfter(clock.instant()) } ?: clock.instant()
                updateSubscription(PROMO_EXTENDED_PRODUCT, null, base.plus(duration))
                PromoCodeResult.APPLIED_EXTENDED
            }
        }
    }

    fun clearSubscription() {
        preferences.edit()
            .remove(KEY_PRODUCT_ID)
            .remove(KEY_PURCHASE_TOKEN)
            .remove(KEY_EXPIRY)
            .apply()
        _status.value = readTrialStatus()
    }

    private fun readStatus(): PremiumStatus {
        val token = preferences.getString(KEY_PURCHASE_TOKEN, null)
        val product = preferences.getString(KEY_PRODUCT_ID, null)
        if (!product.isNullOrBlank()) {
            val expiry = preferences.getLong(KEY_EXPIRY, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)
            return PremiumStatus.Premium(product, token, expiry)
        }
        return readTrialStatus()
    }

    private fun readTrialStatus(): PremiumStatus {
        return PremiumPolicy.statusFor(installationDate, clock.instant())
    }

    companion object {
        val TRIAL_DURATION: Duration = Duration.ofDays(90)
        private const val KEY_INSTALLATION_DATE = "installation_date"
        private const val KEY_PRODUCT_ID = "product_id"
        private const val KEY_PURCHASE_TOKEN = "purchase_token"
        private const val KEY_EXPIRY = "subscription_expiry"
        private const val PROMO_LIFETIME_PRODUCT = "promo_lifetime"
        private const val PROMO_EXTENDED_PRODUCT = "promo_extended"
    }
}
