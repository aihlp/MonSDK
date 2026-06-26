package com.medmonitoring

import com.medmonitoring.core.premium.AppFeature
import com.medmonitoring.core.premium.PremiumPolicy
import com.medmonitoring.core.premium.PremiumStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumPolicyTest {
    private val installedAt = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun premiumFeaturesAreAvailableOnTrialDay89() {
        val status = PremiumPolicy.statusFor(installedAt, installedAt.plus(89, ChronoUnit.DAYS))

        assertTrue(status is PremiumStatus.Trial)
        assertTrue(PremiumPolicy.hasAccess(AppFeature.SENSOR_AUTO_RECORD, status))
        assertTrue(PremiumPolicy.hasAccess(AppFeature.AI_ASSISTANT, status))
    }

    @Test
    fun trialExpiresAtDay90Boundary() {
        val status = PremiumPolicy.statusFor(installedAt, installedAt.plus(90, ChronoUnit.DAYS))

        assertTrue(status is PremiumStatus.Basic)
        assertFalse(PremiumPolicy.hasAccess(AppFeature.SENSOR_AUTO_RECORD, status))
        assertFalse(PremiumPolicy.hasAccess(AppFeature.AI_ASSISTANT, status))
        assertTrue(PremiumPolicy.hasAccess(AppFeature.MANUAL_RECORDING, status))
    }

    @Test
    fun subscriptionUnlocksEveryFeature() {
        val status = PremiumStatus.Premium("subs_monthly_2usd", "purchase-token")

        assertTrue(AppFeature.entries.all { PremiumPolicy.hasAccess(it, status) })
    }
}
