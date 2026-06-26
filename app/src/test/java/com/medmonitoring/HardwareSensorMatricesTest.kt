package com.medmonitoring

import com.medmonitoring.core.config.HealthContextCatalog
import com.medmonitoring.core.normalization.SensorRuleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareSensorMatricesTest {
    private val matrix = HealthContextCatalog.hardwareSensors

    @Test
    fun highPressureIsTransformedToTag() {
        val pressure = matrix.first { it.sensorId == "android.sensor.pressure" }
        val result = SensorRuleEvaluator.evaluate(
            mapOf(pressure.sensorId to 1025.0),
            pressure.rules
        )

        assertEquals(
            listOf("context.environment.pressure_high"),
            result.tags.map { it.label }
        )
    }

    @Test
    fun eachConfiguredHardwareSourceProducesTags() {
        assertTrue(matrix.isNotEmpty())
        assertTrue(matrix.all { config ->
            config.rules.isNotEmpty() &&
                config.rules.all {
                    it.sensorId == config.sensorId &&
                        it.targetTag?.startsWith("context.") == true
                }
        })
    }

    @Test
    fun matrixDoesNotDeclareSourcesWithoutContextRules() {
        assertTrue(matrix.none { it.rules.isEmpty() })
    }
}
