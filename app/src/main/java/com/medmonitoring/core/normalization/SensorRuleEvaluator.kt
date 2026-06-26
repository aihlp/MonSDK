package com.medmonitoring.core.normalization

import com.medmonitoring.core.domain.model.ComparisonOperator
import com.medmonitoring.core.domain.model.Observation
import com.medmonitoring.core.domain.model.SensorRule
import com.medmonitoring.core.domain.model.TagEntry
import com.medmonitoring.core.domain.model.TransformType

data class SensorContextResult(
    val observations: List<Observation> = emptyList(),
    val tags: List<TagEntry> = emptyList(),
    val alerts: List<String> = emptyList()
)

object SensorRuleEvaluator {
    fun evaluate(values: Map<String, Double>, rules: List<SensorRule>): SensorContextResult {
        val observations = mutableListOf<Observation>()
        val tags = mutableListOf<TagEntry>()
        val alerts = mutableListOf<String>()
        rules.filter(SensorRule::enabled).forEach { rule ->
            val value = values[rule.sensorId] ?: return@forEach
            if (!matches(value, rule)) return@forEach
            when (rule.transformType) {
                TransformType.METRIC -> rule.targetMetricId?.let {
                    observations += Observation(it, value, unitFor(rule.sensorId), "sensor_context")
                }
                TransformType.TAG -> rule.targetTag?.let {
                    tags += TagEntry("sensor_context", it.toKey(), it)
                }
                TransformType.ALERT -> rule.targetTag?.let(alerts::add)
            }
        }
        return SensorContextResult(observations, tags.distinctBy(TagEntry::key), alerts.distinct())
    }

    private fun matches(value: Double, rule: SensorRule): Boolean {
        val threshold = rule.threshold ?: return true
        return when (rule.operator) {
            ComparisonOperator.GREATER_THAN -> value > threshold
            ComparisonOperator.LESS_THAN -> value < threshold
            ComparisonOperator.EQUALS -> value == threshold
        }
    }

    private fun unitFor(sensorId: String) = when (sensorId) {
        "android.sensor.pressure" -> "hPa"
        "android.sensor.light" -> "lx"
        "android.sensor.ambient_temperature" -> "°C"
        "android.sensor.relative_humidity" -> "%"
        "android.sensor.step_counter", "hc.steps" -> "count"
        else -> ""
    }

    private fun String.toKey() = lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
