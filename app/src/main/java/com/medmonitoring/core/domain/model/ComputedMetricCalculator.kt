package com.medmonitoring.core.domain.model

import kotlin.math.roundToInt

/**
 * Pure calculator for derived metrics (e.g. BMI). Inputs and output are canonical values, so the
 * same logic backs the live input form and the persisted record.
 */
object ComputedMetricCalculator {
    fun compute(
        definition: ComputedMetricDefinition,
        canonicalValues: Map<String, Double>
    ): Double? = when (definition.formula) {
        ComputedMetricFormula.BMI -> {
            val weightKg = definition.sourceMetricIds.getOrNull(0)?.let { canonicalValues[it] }
            val heightCm = definition.sourceMetricIds.getOrNull(1)?.let { canonicalValues[it] }
            if (weightKg == null || heightCm == null || heightCm <= 0.0) {
                null
            } else {
                val heightM = heightCm / 100.0
                (weightKg / (heightM * heightM) * 10).roundToInt() / 10.0
            }
        }
    }
}
