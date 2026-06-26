package com.medmonitoring

import com.medmonitoring.core.domain.model.HealthConnectRecordType
import com.medmonitoring.core.normalization.SensorRuleEvaluator
import com.medmonitoring.program.bloodpressure.BloodPressureDefinitions
import org.junit.Assert.assertEquals
import org.junit.Test

class SecondaryHealthContextTest {
    @Test
    fun weightAboveConfiguredTargetBecomesContextTag() {
        val mapping = mapping(HealthConnectRecordType.WEIGHT)

        val result = SensorRuleEvaluator.evaluate(
            mapOf("hc.weight.weight" to 95.0),
            mapping.rules
        )

        assertEquals(listOf("context.nutrition.weight_above_target"), result.tags.map { it.label })
    }

    @Test
    fun highGlucoseBecomesContextTag() {
        val mapping = mapping(HealthConnectRecordType.BLOOD_GLUCOSE)

        val result = SensorRuleEvaluator.evaluate(
            mapOf("hc.blood_glucose.level" to 8.0),
            mapping.rules
        )

        assertEquals(listOf("context.nutrition.glucose_high"), result.tags.map { it.label })
    }

    private fun mapping(type: HealthConnectRecordType) =
        BloodPressureDefinitions.program.integrations.healthConnectMappings.first { it.recordType == type }
}
