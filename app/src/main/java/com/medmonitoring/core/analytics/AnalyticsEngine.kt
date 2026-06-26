package com.medmonitoring.core.analytics

import com.medmonitoring.core.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface AnalyticsRule {
    val id: String
    fun evaluate(records: List<UserRecord>, schema: ProgramAnalyticsSchema): List<Finding>
}

@Singleton
class AnalyticsEngine @Inject constructor() {
    fun calculate(records: List<UserRecord>, schema: ProgramAnalyticsSchema): AnalyticsState {
        val dashboardMetrics = buildDashboardMetrics(records, schema)
        val dataSummary = buildDataSummary(records, schema)
        val allFindings = schema.rules
            .flatMap { it.evaluate(records, schema) }
            .sortedWith(compareBy<Finding> { it.severity.priority }.thenBy { it.stableKey })
        return AnalyticsState(
            dashboardMetrics = dashboardMetrics,
            showcaseFindings = allFindings.take(10).map { it.toCardModel() },
            allFindings = allFindings,
            dataSummary = dataSummary,
            analysisLevel = analysisLevelFor(records.size)
        )
    }

    private fun buildDashboardMetrics(records: List<UserRecord>, config: ProgramAnalyticsSchema): List<StatisticMetric> {
        val metrics = mutableListOf<StatisticMetric>()
        val trackedAction = config.actions.firstOrNull { it.statuses.size >= 2 }
        if (trackedAction != null) {
            val positiveStatus = trackedAction.statuses[0]
            val negativeStatus = trackedAction.statuses[1]
            val positive = records.countActions(trackedAction.key, positiveStatus)
            val negative = records.countActions(trackedAction.key, negativeStatus)
            val total = positive + negative
            metrics += StatisticMetric(
                id = "dashboard_adherence",
                label = "Adherence",
                value = if (total == 0) "0" else ((positive.toDouble() / total) * 100.0).roundToInt().toString(),
                unit = "%",
                role = StatisticRole.Percent,
                sourceRuleId = "dashboard",
                labelKey = "dashboard_adherence"
            )
            metrics += StatisticMetric(
                id = "dashboard_missed_medication",
                label = "Missed",
                value = negative.toString(),
                role = StatisticRole.Count,
                sourceRuleId = "dashboard",
                labelKey = "dashboard_missed_medication"
            )
        }
        config.metrics.forEach { metric ->
            val average = records.metricValues(metric.key).averageOrNull()
            metrics += StatisticMetric(
                id = "dashboard_avg_${metric.key}",
                label = "Avg ${metric.label}",
                value = if (records.size < config.thresholds.minRecordsForDashboard || average == null) "-" else average.roundToInt().toString(),
                unit = metric.unit,
                role = metric.role,
                sourceRuleId = "dashboard",
                labelKey = "dashboard_avg_${metric.key}",
                unitKey = metric.unitKey
            )
        }
        metrics += StatisticMetric(
            id = "dashboard_records",
            label = "Records",
            value = records.size.toString(),
            role = StatisticRole.Count,
            sourceRuleId = "dashboard",
            labelKey = "dashboard_records"
        )
        if (config.dashboardMetricOrder.isEmpty()) return metrics
        val order = config.dashboardMetricOrder.withIndex().associate { it.value to it.index }
        return metrics.sortedBy { order[it.id] ?: Int.MAX_VALUE }
    }

    private fun buildDataSummary(records: List<UserRecord>, schema: ProgramAnalyticsSchema): DataSummary {
        return DataSummary(
            recordCount = records.size,
            metricCoverage = schema.metrics.associate { metric -> metric.key to records.metricValues(metric.key).size },
            eventCoverage = schema.actions.associate { action -> action.key to records.count { record -> record.events.any { it.key == action.key } } },
            dimensionCoverage = schema.tagGroups.associate { group -> group.key to records.count { record -> record.dimensions.any { it.group == group.key } } },
            minRecordsForDashboard = schema.thresholds.minRecordsForDashboard,
            minRecordsForFindings = schema.thresholds.minRecordsForFindings,
            minGroupSizeForComparison = schema.thresholds.minGroupSizeForComparison
        )
    }
}

data class AverageMetricRule(private val metricKey: String) : AnalyticsRule {
    override val id = "average_$metricKey"

    override fun evaluate(records: List<UserRecord>, schema: ProgramAnalyticsSchema): List<Finding> = emptyList()
}

data class CountActionStatusRule(private val actionKey: String, private val status: String) : AnalyticsRule {
    override val id = "count_${actionKey}_$status"

    override fun evaluate(records: List<UserRecord>, schema: ProgramAnalyticsSchema): List<Finding> = emptyList()
}

data class AdherenceRule(
    private val actionKey: String,
    private val positiveStatus: String,
    private val negativeStatus: String
) : AnalyticsRule {
    override val id = "adherence_$actionKey"

    override fun evaluate(records: List<UserRecord>, schema: ProgramAnalyticsSchema): List<Finding> {
        if (schema.actions.none { it.key == actionKey }) return emptyList()
        if (records.size < schema.thresholds.minRecordsForFindings) return emptyList()
        val positive = records.countActions(actionKey, positiveStatus)
        val negative = records.countActions(actionKey, negativeStatus)
        val total = positive + negative
        if (total == 0) return emptyList()
        val percent = ((positive.toDouble() / total) * 100.0).roundToInt()
        return listOf(
            Finding(
                stableKey = id,
                type = "adherence",
                title = "Medication adherence",
                message = "$percent% of medication records were marked as $positiveStatus.",
                effectDirection = if (negative == 0) EffectDirection.HIGHER else EffectDirection.LOWER,
                evidence = listOf(
                    StatisticMetric("${id}_percent", "Adherence", percent.toString(), "%", StatisticRole.Percent, id, labelKey = "dashboard_adherence"),
                    StatisticMetric("${id}_missed", "Missed", negative.toString(), null, StatisticRole.Count, id, labelKey = "dashboard_missed_medication")
                ),
                basis = "Based on $total medication records.",
                titleKey = "finding_adherence",
                messageKey = "msg_adherence",
                basisKey = "basis_meds",
                severity = if (negative == 0) FindingSeverity.Positive else FindingSeverity.Risk,
                sourceRuleId = id,
                eventKey = actionKey,
                leftGroupLabel = positiveStatus,
                rightGroupLabel = negativeStatus,
                leftGroupSize = positive,
                rightGroupSize = negative,
                recordCount = records.size
            )
        )
    }
}

data class FrequentTagRule(private val groupKey: String) : AnalyticsRule {
    override val id = "frequent_tag_$groupKey"

    override fun evaluate(records: List<UserRecord>, schema: ProgramAnalyticsSchema): List<Finding> {
        val dimension = schema.tagGroups.firstOrNull { it.key == groupKey } ?: return emptyList()
        if (records.size < schema.thresholds.minRecordsForFindings) return emptyList()
        val top = records.flatMap { record -> record.dimensions.filter { it.group == groupKey } }
            .groupingBy { it.label }
            .eachCount()
            .entries
            .filter { it.value >= schema.thresholds.minOccurrencesForTag }
            .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })
            ?: return emptyList()
        val groupLabel = dimension.label
        return listOf(
            Finding(
                stableKey = "${id}_${top.key.sanitizeId()}",
                type = "dimension_frequency",
                title = "Frequent ${groupLabel.lowercase()}",
                message = "${top.key} appeared in ${top.value} of ${records.size} records.",
                effectDirection = EffectDirection.UNKNOWN,
                evidence = listOf(StatisticMetric("${id}_count", "Occurrences", top.value.toString(), null, StatisticRole.Count, id, labelKey = "occurrences")),
                basis = "Based on ${records.size} records.",
                titleKey = "finding_frequent_prefix",
                messageKey = "msg_frequent",
                basisKey = "basis_records",
                severity = groupKey.tagSeverity(),
                sourceRuleId = id,
                dimensionKey = groupKey,
                leftGroupLabel = top.key,
                leftGroupSize = top.value,
                recordCount = records.size
            )
        )
    }
}

data class FrequentTagCombinationRule(private val groups: List<String>) : AnalyticsRule {
    override val id = "frequent_combination_${groups.joinToString("_")}"

    override fun evaluate(records: List<UserRecord>, schema: ProgramAnalyticsSchema): List<Finding> {
        if (records.size < schema.thresholds.minRecordsForFindings) return emptyList()
        val combinations = records.flatMap { record ->
            val perGroup = groups.map { group -> record.dimensions.filter { it.group == group }.map { it.label }.distinct() }
            if (perGroup.any { it.isEmpty() }) emptyList() else perGroup.cartesianProduct().map { it.joinToString(" + ") }
        }
        val top = combinations.groupingBy { it }
            .eachCount()
            .entries
            .filter { it.value >= schema.thresholds.minOccurrencesForCombination }
            .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })
            ?: return emptyList()
        return listOf(
            Finding(
                stableKey = "${id}_${top.key.sanitizeId()}",
                type = "dimension_combination",
                title = "Frequent combination",
                message = "${top.key} appeared together in ${top.value} records.",
                effectDirection = EffectDirection.UNKNOWN,
                evidence = listOf(StatisticMetric("${id}_count", "Together", top.value.toString(), null, StatisticRole.Count, id, labelKey = "together")),
                basis = "Based on ${records.size} records.",
                titleKey = "finding_combination",
                messageKey = "msg_combination",
                basisKey = "basis_records",
                severity = if (groups.any { it.tagSeverity() == FindingSeverity.Risk }) FindingSeverity.Risk else FindingSeverity.Neutral,
                sourceRuleId = id,
                dimensionKey = groups.joinToString("+"),
                leftGroupLabel = top.key,
                leftGroupSize = top.value,
                recordCount = records.size
            )
        )
    }
}

data class MetricByTagRule(private val metricKey: String, private val tagGroupKey: String) : AnalyticsRule {
    override val id = "${metricKey}_by_$tagGroupKey"

    override fun evaluate(records: List<UserRecord>, schema: ProgramAnalyticsSchema): List<Finding> {
        if (records.size < schema.thresholds.minRecordsForFindings) return emptyList()
        val metric = schema.metrics.firstOrNull { it.key == metricKey } ?: return emptyList()
        if (schema.tagGroups.none { it.key == tagGroupKey }) return emptyList()
        val taggedRecords = records.filter { record -> record.dimensions.any { it.group == tagGroupKey } }
        val untaggedRecords = records.filter { record -> record.dimensions.none { it.group == tagGroupKey } }
        val tagged = taggedRecords.metricValues(metricKey)
        val untagged = untaggedRecords.metricValues(metricKey)
        if (tagged.size < schema.thresholds.minGroupSizeForComparison || untagged.size < schema.thresholds.minGroupSizeForComparison) return emptyList()
        return comparisonFinding(
            id = id,
            type = "metric_by_dimension",
            title = "${metric.label} with ${tagGroupKey.replace('_', ' ')}",
            leftLabel = "Tagged",
            rightLabel = "Other",
            leftAverage = tagged.average(),
            rightAverage = untagged.average(),
            unit = metric.unit,
            config = schema,
            basis = "Based on ${tagged.size} tagged and ${untagged.size} other records.",
            recordCount = records.size,
            leftGroupSize = tagged.size,
            rightGroupSize = untagged.size,
            dimensionKey = tagGroupKey
        )
    }
}

data class MetricByActionStatusRule(
    private val metricKey: String,
    private val actionKey: String,
    private val statuses: List<String>
) : AnalyticsRule {
    override val id = "${metricKey}_by_${actionKey}_status"

    override fun evaluate(records: List<UserRecord>, schema: ProgramAnalyticsSchema): List<Finding> {
        if (schema.actions.none { it.key == actionKey }) return emptyList()
        if (records.size < schema.thresholds.minRecordsForFindings || statuses.size < 2) return emptyList()
        val metric = schema.metrics.firstOrNull { it.key == metricKey } ?: return emptyList()
        val leftStatus = statuses[0]
        val rightStatus = statuses[1]
        val left = records.filter { it.hasActionStatus(actionKey, leftStatus) }.metricValues(metricKey)
        val right = records.filter { it.hasActionStatus(actionKey, rightStatus) }.metricValues(metricKey)
        if (left.size < schema.thresholds.minGroupSizeForComparison || right.size < schema.thresholds.minGroupSizeForComparison) return emptyList()
        return comparisonFinding(
            id = id,
            type = "metric_by_event",
            title = "${metric.label}: $rightStatus vs $leftStatus",
            leftLabel = leftStatus.replaceFirstChar { it.uppercase() },
            rightLabel = rightStatus.replaceFirstChar { it.uppercase() },
            leftAverage = left.average(),
            rightAverage = right.average(),
            unit = metric.unit,
            config = schema,
            basis = "Based on ${left.size} $leftStatus and ${right.size} $rightStatus records.",
            recordCount = records.size,
            leftGroupSize = left.size,
            rightGroupSize = right.size,
            eventKey = actionKey
        )
    }
}

data class ExtremeValueRule(private val metricKey: String, private val mode: String) : AnalyticsRule {
    override val id = "${mode}_$metricKey"

    override fun evaluate(records: List<UserRecord>, schema: ProgramAnalyticsSchema): List<Finding> {
        if (records.size < schema.thresholds.minRecordsForFindings) return emptyList()
        val metric = schema.metrics.firstOrNull { it.key == metricKey } ?: return emptyList()
        val threshold = if (mode == "lowest") metric.dangerousLow else metric.dangerousHigh
        if (threshold == null) return emptyList()
        val values = records.mapNotNull { record -> record.measurement(metricKey)?.let { record to it } }
        val selected = when (mode) {
            "lowest" -> values.minByOrNull { it.second.value }
            else -> values.maxByOrNull { it.second.value }
        } ?: return emptyList()
        val exceedsDanger = if (mode == "lowest") selected.second.value < threshold else selected.second.value > threshold
        if (!exceedsDanger) return emptyList()
        val value = selected.second.value.roundToInt()
        val label = if (mode == "lowest") "Lowest" else "Highest"
        return listOf(
            Finding(
                stableKey = id,
                type = "extreme_value",
                title = "$label ${metric.label} recorded",
                message = "$label ${metric.label} was $value ${metric.unit}.",
                effectDirection = if (mode == "lowest") EffectDirection.LOWER else EffectDirection.HIGHER,
                evidence = listOf(StatisticMetric("${id}_value", metric.label, value.toString(), metric.unit, metric.role, id, labelKey = metric.labelKey, unitKey = metric.unitKey)),
                basis = "Based on ${records.size} records.",
                titleKey = "finding_extreme",
                messageKey = "msg_extreme",
                basisKey = "basis_records",
                severity = FindingSeverity.Risk,
                sourceRuleId = id,
                metricKey = metricKey,
                recordCount = records.size
            )
        )
    }
}

private fun comparisonFinding(
    id: String,
    type: String,
    title: String,
    leftLabel: String,
    rightLabel: String,
    leftAverage: Double,
    rightAverage: Double,
    unit: String,
    config: ProgramAnalyticsSchema,
    basis: String,
    recordCount: Int,
    leftGroupSize: Int,
    rightGroupSize: Int,
    dimensionKey: String? = null,
    eventKey: String? = null
): List<Finding> {
    val absoluteDifference = abs(rightAverage - leftAverage)
    val percentDifference = if (leftAverage == 0.0) 0.0 else (absoluteDifference / leftAverage) * 100.0
    val metricKey = id.substringBefore("_by_")
    val meaningfulDifference = config.metrics.firstOrNull { it.key == metricKey }?.meaningfulDifference ?: 0.0
    if (absoluteDifference < meaningfulDifference && percentDifference < config.thresholds.minDifferencePercent) return emptyList()
    val rightRounded = rightAverage.roundToInt()
    val leftRounded = leftAverage.roundToInt()
    return listOf(
        Finding(
            stableKey = id,
            type = type,
            title = title,
            message = "$rightLabel average was $rightRounded $unit vs $leftRounded $unit for $leftLabel.",
            effectDirection = when {
                rightAverage > leftAverage -> EffectDirection.HIGHER
                rightAverage < leftAverage -> EffectDirection.LOWER
                else -> EffectDirection.SAME
            },
            evidence = listOf(
                StatisticMetric("${id}_right", rightLabel, rightRounded.toString(), unit, StatisticRole.Primary, id, labelKey = rightLabel.analyticsLabelKey()),
                StatisticMetric("${id}_left", leftLabel, leftRounded.toString(), unit, StatisticRole.Primary, id, labelKey = leftLabel.analyticsLabelKey())
            ),
            basis = basis,
            titleKey = "finding_comparison",
            messageKey = "msg_comparison",
            basisKey = if (eventKey != null) "basis_comparison" else "basis_tagged_comparison",
            severity = if (rightAverage > leftAverage) FindingSeverity.Risk else FindingSeverity.Positive,
            sourceRuleId = id,
            metricKey = metricKey,
            dimensionKey = dimensionKey,
            eventKey = eventKey,
            leftGroupLabel = leftLabel,
            rightGroupLabel = rightLabel,
            leftGroupSize = leftGroupSize,
            rightGroupSize = rightGroupSize,
            recordCount = recordCount
        )
    )
}

private fun String.analyticsLabelKey(): String? = when (lowercase()) {
    "tagged" -> "tagged"
    "other" -> "other"
    "taken" -> "taken"
    "missed" -> "missed"
    else -> null
}

private fun UserRecord.measurement(metricKey: String): Measurement? = measurements.firstOrNull { it.key == metricKey }

private fun UserRecord.hasActionStatus(actionKey: String, status: String): Boolean {
    return events.any { it.key == actionKey && it.status.equals(status, ignoreCase = true) }
}

private fun List<UserRecord>.metricValues(metricKey: String): List<Double> {
    return mapNotNull { it.measurement(metricKey)?.value }
}

private fun List<UserRecord>.countActions(actionKey: String, status: String): Int {
    return count { it.hasActionStatus(actionKey, status) }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

private fun List<List<String>>.cartesianProduct(): List<List<String>> {
    return fold(listOf(emptyList())) { acc, values -> acc.flatMap { prefix -> values.map { prefix + it } } }
}

private fun String.sanitizeId(): String {
    return lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}

private val FindingSeverity.priority: Int
    get() = when (this) {
        FindingSeverity.Risk -> 0
        FindingSeverity.Positive -> 1
        FindingSeverity.Neutral -> 2
    }

private fun String.tagSeverity(): FindingSeverity {
    return when (this) {
        "healthy" -> FindingSeverity.Positive
        "unhealthy", "symptoms" -> FindingSeverity.Risk
        else -> FindingSeverity.Neutral
    }
}
