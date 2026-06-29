package com.medmonitoring.core.domain.model

import java.util.Locale
import kotlin.math.roundToInt

data class DisplayMetricValue(
    val metricId: String,
    val canonicalValue: Double,
    val displayValue: Double,
    val valueText: String,
    val unit: String,
    val unitKey: String? = null,
    val unitModeId: String? = null,
    val step: Double,
    val decimalPlaces: Int
)

object MetricUnitFormatter {
    fun activeUnit(
        metricId: String,
        program: UniversalProgramDefinition,
        ui: ProgramUiDefinition,
        unitPreferences: Map<String, String>
    ): InputUnitMode? {
        val metric = program.metricComponents.firstOrNull { it.id == metricId } ?: return null
        val inputConfig = ui.inputConfigForMetric(metricId)
        val modes = metric.unitModesOrDefault(inputConfig)
        val selectedId = unitPreferences[metricId].orEmpty()
        return modes.firstOrNull { it.id == selectedId }
            ?: modes.firstOrNull { it.unit == selectedId }
            ?: modes.firstOrNull()
    }

    fun displayValue(
        metric: MetricComponent,
        canonicalValue: Double,
        program: UniversalProgramDefinition,
        ui: ProgramUiDefinition,
        unitPreferences: Map<String, String>
    ): DisplayMetricValue {
        val inputConfig = ui.inputConfigForMetric(metric.id)
        val unit = activeUnit(metric.id, program, ui, unitPreferences)
        val displayConfig = inputConfig.forUnit(unit)
        val display = canonicalValue.fromCanonical(unit)
        return DisplayMetricValue(
            metricId = metric.id,
            canonicalValue = canonicalValue,
            displayValue = display,
            valueText = display.formatFor(displayConfig),
            unit = unit?.unit ?: metric.unit,
            unitKey = unit?.unitKey ?: metric.unitKey,
            unitModeId = unit?.id,
            step = displayConfig.step,
            decimalPlaces = displayConfig.decimalPlaces
        )
    }

    fun toDisplay(metricId: String, canonicalValue: Double, program: UniversalProgramDefinition, ui: ProgramUiDefinition, unitPreferences: Map<String, String>): Double =
        canonicalValue.fromCanonical(activeUnit(metricId, program, ui, unitPreferences))

    fun fromDisplay(metricId: String, displayValue: Double, program: UniversalProgramDefinition, ui: ProgramUiDefinition, unitPreferences: Map<String, String>): Double =
        displayValue.toCanonical(activeUnit(metricId, program, ui, unitPreferences))

    fun ProgramUiDefinition.inputConfigForMetric(metricId: String): InputBlockConfig {
        // Per-metric config on a multi-metric block wins (e.g. height inside a BMI block).
        recordBlocks.firstNotNullOfOrNull { it.inputConfigs[metricId] }?.let { return it }
        return recordBlocks.firstOrNull { block ->
            block.configId.split(',').map { it.trim() }.any { it == metricId }
        }?.inputConfig ?: InputBlockConfig()
    }

    fun ProgramUiDefinition.displayInputConfigForMetric(
        metricId: String,
        unitPreferences: Map<String, String>,
        program: UniversalProgramDefinition
    ): InputBlockConfig =
        inputConfigForMetric(metricId).forUnit(activeUnit(metricId, program, this, unitPreferences))

    fun MetricComponent.unitModesOrDefault(inputConfig: InputBlockConfig): List<InputUnitMode> =
        inputConfig.unitModes.ifEmpty {
            listOf(InputUnitMode(id = unit, label = unit, unit = unit, toCanonicalFactor = 1.0, unitKey = unitKey))
        }

    fun InputBlockConfig.forUnit(unit: InputUnitMode?): InputBlockConfig =
        copy(
            step = unit?.step ?: step,
            decimalPlaces = unit?.decimalPlaces ?: decimalPlaces
        )

    fun Double.fromCanonical(unit: InputUnitMode?): Double =
        if (unit == null || unit.toCanonicalFactor == 0.0) this else this / unit.toCanonicalFactor

    fun Double.toCanonical(unit: InputUnitMode?): Double =
        if (unit == null) this else this * unit.toCanonicalFactor

    fun Double.formatFor(inputConfig: InputBlockConfig): String =
        if (inputConfig.decimalPlaces == 0) {
            roundToInt().toString()
        } else {
            String.format(Locale.US, "%.${inputConfig.decimalPlaces}f", this)
        }
}
