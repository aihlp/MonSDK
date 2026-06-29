package com.medmonitoring.core.analytics

import com.medmonitoring.core.domain.model.AnalyticsState
import com.medmonitoring.core.domain.model.MetricUnitFormatter
import com.medmonitoring.core.domain.model.ProgramUiDefinition
import com.medmonitoring.core.domain.model.StatisticMetric
import com.medmonitoring.core.domain.model.UniversalProgramDefinition

object AnalyticsUnitFormatter {
    fun apply(
        analytics: AnalyticsState,
        program: UniversalProgramDefinition,
        ui: ProgramUiDefinition,
        unitPreferences: Map<String, String>
    ): AnalyticsState {
        val dashboard = analytics.dashboardMetrics.map { metric ->
            metric.convert(program, ui, unitPreferences)
        }
        val findings = analytics.allFindings.map { finding ->
            finding.copy(evidence = finding.evidence.map { it.convert(program, ui, unitPreferences) })
        }
        return analytics.copy(
            dashboardMetrics = dashboard,
            allFindings = findings,
            showcaseFindings = findings.take(10).map { it.toCardModel() }
        )
    }

    private fun StatisticMetric.convert(
        program: UniversalProgramDefinition,
        ui: ProgramUiDefinition,
        unitPreferences: Map<String, String>
    ): StatisticMetric {
        val metricId = metricIdFromStatisticId() ?: return this
        val definition = program.metricComponents.firstOrNull { it.id == metricId } ?: return this
        if (value == "-") return this
        val number = value.replace(',', '.').toDoubleOrNull() ?: return this
        val display = MetricUnitFormatter.displayValue(definition, number, program, ui, unitPreferences)
        return copy(
            value = display.valueText,
            unit = display.unit,
            unitKey = display.unitKey
        )
    }

    private fun StatisticMetric.metricIdFromStatisticId(): String? = when {
        id.startsWith("dashboard_avg_") -> id.removePrefix("dashboard_avg_")
        id.endsWith("_left") || id.endsWith("_right") -> labelKey?.removePrefix("metric_")
        else -> null
    }
}
