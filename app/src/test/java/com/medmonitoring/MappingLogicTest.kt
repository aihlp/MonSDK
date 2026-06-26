package com.medmonitoring

import com.medmonitoring.core.domain.model.ComparisonOperator
import com.medmonitoring.core.domain.model.SensorRule
import com.medmonitoring.core.domain.model.TransformType
import com.medmonitoring.core.normalization.SensorRuleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Test

class MappingLogicTest {
    @Test
    fun highPressureAddsMeteoRiskTag() {
        val result = SensorRuleEvaluator.evaluate(
            values = mapOf("android.sensor.pressure" to 1030.0),
            rules = listOf(
                SensorRule(
                    sensorId = "android.sensor.pressure",
                    transformType = TransformType.TAG,
                    operator = ComparisonOperator.GREATER_THAN,
                    threshold = 1020.0,
                    targetTag = "Meteo Risk"
                )
            )
        )

        assertEquals(listOf("Meteo Risk"), result.tags.map { it.label })
    }
}
