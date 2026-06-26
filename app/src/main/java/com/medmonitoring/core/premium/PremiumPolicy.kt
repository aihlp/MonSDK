package com.medmonitoring.core.premium

import java.time.Duration
import java.time.Instant

object PremiumPolicy {
    fun statusFor(
        installationDate: Instant,
        now: Instant,
        trialDuration: Duration = PremiumRepository.TRIAL_DURATION
    ): PremiumStatus {
        val expiresAt = installationDate.plus(trialDuration)
        return if (now.isBefore(expiresAt)) PremiumStatus.Trial(expiresAt) else PremiumStatus.Basic
    }

    fun hasAccess(feature: AppFeature, status: PremiumStatus): Boolean {
        return feature.isBasicAvailable ||
            status is PremiumStatus.Trial ||
            status is PremiumStatus.Premium
    }
}
