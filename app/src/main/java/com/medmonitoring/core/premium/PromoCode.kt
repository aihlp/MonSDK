package com.medmonitoring.core.premium

import java.time.Duration

enum class PromoReward {
    LIFETIME_PREMIUM,
    EXTENDED_PREMIUM
}

data class PromoCodeDefinition(
    val code: String,
    val reward: PromoReward,
    val duration: Duration? = null
)

data class PremiumConfig(
    val monthlyProductId: String,
    val yearlyProductId: String,
    val promoCodes: List<PromoCodeDefinition> = emptyList()
)

enum class PromoCodeResult {
    APPLIED_LIFETIME,
    APPLIED_EXTENDED,
    INVALID
}
