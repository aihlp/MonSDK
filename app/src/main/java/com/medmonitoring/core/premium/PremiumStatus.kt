package com.medmonitoring.core.premium

import java.time.Instant

sealed interface PremiumStatus {
    data class Trial(val expiresAt: Instant) : PremiumStatus
    data object Basic : PremiumStatus
    data class Premium(
        val productId: String,
        val purchaseToken: String? = null,
        val expiresAt: Instant? = null
    ) : PremiumStatus
}
