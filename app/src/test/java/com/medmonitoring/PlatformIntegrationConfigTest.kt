package com.medmonitoring

import com.medmonitoring.core.domain.model.HealthConnectRecordType
import com.medmonitoring.core.domain.model.HealthConnectMappingRole
import com.medmonitoring.program.bloodpressure.BloodPressureDefinitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformIntegrationConfigTest {
    @Test
    fun hypertensionBuildDeclaresOnlyItsRequiredIntegrations() {
        val integrations = BloodPressureDefinitions.program.integrations

        assertTrue(integrations.hardwareSensors.any { it.sensorId == "android.sensor.pressure" })
        assertTrue(integrations.hardwareSensors.all { it.rules.isNotEmpty() })
        assertEquals(
            setOf(HealthConnectRecordType.BLOOD_PRESSURE, HealthConnectRecordType.HEART_RATE),
            integrations.healthConnectMappings
                .filter { it.role == HealthConnectMappingRole.PRIMARY_METRIC }
                .map { it.recordType }.toSet()
        )
        assertEquals(
            setOf(
                HealthConnectRecordType.STEPS,
                HealthConnectRecordType.EXERCISE,
                HealthConnectRecordType.SLEEP,
                HealthConnectRecordType.WEIGHT
            ),
            integrations.healthConnectMappings
                .filter { it.role == HealthConnectMappingRole.CONTEXT_TAG }
                .map { it.recordType }.toSet()
        )
        assertTrue(integrations.backgroundReadEnabled)
        assertTrue(
            integrations.healthConnectMappings.all {
                it.role == HealthConnectMappingRole.PRIMARY_METRIC ||
                    it.metricMappings.isNotEmpty() ||
                    it.rules.isNotEmpty()
            }
        )
    }
}
