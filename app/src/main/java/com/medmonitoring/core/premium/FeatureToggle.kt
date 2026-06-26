package com.medmonitoring.core.premium

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureToggle @Inject constructor(
    private val premiumRepository: PremiumRepository
) {
    val premiumStatus: StateFlow<PremiumStatus> = premiumRepository.status

    fun hasAccess(feature: AppFeature): Boolean {
        return PremiumPolicy.hasAccess(feature, premiumRepository.status.value)
    }

    fun runWithPremiumCheck(feature: AppFeature, action: () -> Unit): Boolean {
        if (!hasAccess(feature)) return false
        action()
        return true
    }
}
