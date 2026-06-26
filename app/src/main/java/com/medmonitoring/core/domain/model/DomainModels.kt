package com.medmonitoring.core.domain.model

import com.medmonitoring.core.analytics.AnalyticsRule
import com.medmonitoring.core.premium.PremiumConfig
import java.time.Instant

enum class SourceType { MANUAL, HEALTH_CONNECT, SYSTEM_SENSOR }
enum class AggregationStrategy { None, Average, Median, Max, Min }
enum class RecordFlag { Normal, Alert, Anomaly }
enum class MedicationStatus { TAKEN, MISSED, NOT_RECORDED }
enum class MetricType { BLOOD_PRESSURE, PULSE, MEDICATION_ADHERENCE }
enum class WidgetType {
    GraphWidget,
    DateTimeWidget,
    EventStatusWidget,
    EventAmountWidget,
    PairedVerticalWheelInputWidget,
    SingleHorizontalWheelInputWidget,
    TagGroupsWidget,
    NoteWidget,
    SaveButtonWidget,
    AnalyticsSummaryWidget,
    AnalyticsDetailsWidget,
    HistoryWidget
}
enum class SettingsSectionType {
    DevicesSection,
    SensorLaboratorySection,
    HealthConnectSection,
    UnitsSection,
    RemindersSection,
    DataExportImportSection,
    AboutSection
}
enum class TransformType { METRIC, TAG, ALERT }
enum class ComparisonOperator { GREATER_THAN, LESS_THAN, EQUALS }
enum class ContextDomain { ACTIVITY, ENVIRONMENT, BEHAVIOR, RECOVERY, NUTRITION, DATA_QUALITY }
enum class ContextPriority { CRITICAL, IMPORTANT, OPTIONAL }
enum class HealthConnectRecordType {
    BLOOD_PRESSURE,
    HEART_RATE,
    BLOOD_GLUCOSE,
    STEPS,
    WEIGHT,
    EXERCISE,
    SLEEP
}
enum class HealthConnectMappingRole { PRIMARY_METRIC, CONTEXT_TAG }
enum class RecordField {
    Id,
    Timestamp,
    MedicationName,
    MedicationStatus,
    DoseValue,
    DoseUnit,
    Systolic,
    Diastolic,
    Pulse,
    HealthyTags,
    UnhealthyTags,
    SymptomTags,
    OtherMedicationTags,
    SideEffectTags,
    CustomTags,
    Note,
    CreatedAt,
    UpdatedAt
}
enum class DataActionType {
    ExportCsv,
    ImportCsv
}
enum class TagColorRole {
    Healthy,
    Unhealthy,
    Symptom,
    Medication,
    Neutral
}
enum class GraphSeriesType {
    Line,
    Bar,
    PointOrLine,
    EventMarker
}
enum class AxisScaleStrategy {
    FromZero,
    DataRange,
    PaddedDataRange,
    FixedRange,
    PerMetricAxis,
    LogScale
}

data class MetricComponent(
    val id: String,
    val label: String,
    val unit: String,
    val inputRange: IntRange,
    val normalRange: IntRange? = null,
    val cautionRange: IntRange? = null,
    val inputStyle: MetricInputStyle = MetricInputStyle.VerticalWheel,
    val palette: MetricZonePalette,
    val aggregationStrategy: AggregationStrategy = AggregationStrategy.None,
    val defaultValue: Int? = null,
    val isRequired: Boolean = true,
    val labelKey: String? = null,
    val unitKey: String? = null
)

data class AutoCollectionConfig(
    val enabled: Boolean = false,
    val intervalMinutes: Long = 60,
    val aggregationWindowMinutes: Long = 60,
    val sourceTypes: Set<SourceType> = setOf(SourceType.HEALTH_CONNECT),
    val recordSlots: List<RecordSlotDefinition> = emptyList()
)

data class RecordSlotDefinition(
    val id: String,
    val startHourInclusive: Int,
    val endHourExclusive: Int
)

enum class MetricInputStyle {
    VerticalWheel,
    HorizontalWheel,
    NumericField
}

data class MetricZonePalette(
    val normalAccentHex: String,
    val normalSurfaceHex: String,
    val cautionAccentHex: String,
    val cautionSurfaceHex: String,
    val dangerAccentHex: String,
    val dangerSurfaceHex: String
)

data class EmptyStateDefinition(
    val title: String,
    val message: String,
    val actionLabel: String,
    val imageResourceName: String,
    val imageContentDescription: String,
    val instructions: List<String>,
    val titleKey: String? = null,
    val messageKey: String? = null,
    val actionLabelKey: String? = null,
    val imageContentDescriptionKey: String? = null,
    val instructionKeys: List<String> = emptyList()
)

data class EditAffordanceDefinition(
    val icon: EditAffordanceIcon,
    val tintHex: String,
    val sizeSp: Int
)

enum class EditAffordanceIcon {
    MaterialEdit
}

data class TagGroupDefinition(
    val id: String,
    val title: String,
    val icon: String,
    val colorRole: TagColorRole,
    val colorHex: String,
    val tags: List<String>,
    val otherEnabled: Boolean,
    val titleKey: String? = null,
    val tagKeys: Map<String, String> = emptyMap()
)

data class GraphSeriesDefinition(
    val metricId: String,
    val label: String,
    val type: GraphSeriesType,
    val colorHex: String,
    val labelKey: String? = null
)

data class GraphSafeZoneDefinition(
    val metricId: String,
    val range: IntRange,
    val colorHex: String,
    val alpha: Float
)

data class GraphPointOverlayDefinition(
    val metricId: String,
    val symbol: String,
    val normalRange: IntRange,
    val safeColorHex: String,
    val dangerColorHex: String
)

data class GraphEventMarkerDefinition(
    val seriesId: String,
    val eventKey: String,
    val activeStatuses: Set<String>,
    val symbol: String,
    val laneBottomDp: Int = 2
)

data class EventInputDefinition(
    val key: String,
    val label: String,
    val labelKey: String? = null,
    val defaultName: String = "",
    val defaultAmount: Double? = null,
    val defaultUnit: String? = null,
    val statuses: List<EventStatusDefinition>
)

data class EventStatusDefinition(
    val status: String,
    val label: String,
    val labelKey: String? = null,
    val positive: Boolean = false
)

data class GraphDefinition(
    val metrics: List<String>,
    val series: List<GraphSeriesDefinition>,
    val yAxisStrategy: AxisScaleStrategy = AxisScaleStrategy.PaddedDataRange,
    val minPadding: Int = 10,
    val maxPadding: Int = 10,
    val visibleRecordCount: Int = 7,
    val recordSlotWidthDp: Int = 64,
    val horizontalPastScroll: Boolean,
    val showGrid: Boolean,
    val showAxisLabels: Boolean,
    val showLegend: Boolean,
    val showTapMarker: Boolean,
    val showValueLabels: Boolean = true,
    val lineThicknessDp: Float = 2f,
    val lineCurvature: Float = 0f,
    val eventMarkerSizeDp: Int = 20,
    val eventLaneGapDp: Int = 2,
    val pointSeriesInBackground: Boolean = true,
    val safeZones: List<GraphSafeZoneDefinition>,
    val pointOverlay: GraphPointOverlayDefinition,
    val eventMarkers: List<GraphEventMarkerDefinition> = emptyList(),
    val emptyState: EmptyStateDefinition
)

data class SaveActionDefinition(
    val previewPattern: String,
    val fallbackText: String
)

data class RecordBlockDefinition(
    val type: WidgetType,
    val configId: String = type.name
)

data class AnalyticsRuleDefinition(
    val id: String,
    val label: String,
    val labelKey: String? = null
)

enum class StatisticRole {
    Primary,
    Secondary,
    Count,
    Percent
}

data class MetricConfig(
    val key: String,
    val label: String,
    val unit: String,
    val role: StatisticRole = StatisticRole.Secondary,
    val meaningfulDifference: Double? = null,
    val dangerousHigh: Double? = null,
    val dangerousLow: Double? = null,
    val labelKey: String? = null,
    val unitKey: String? = null
)

data class ActionConfig(
    val key: String,
    val statuses: List<String>
)

data class TagGroupConfig(
    val key: String,
    val label: String,
    val labelKey: String? = null
)

data class AnalyticsThresholds(
    val minRecordsForDashboard: Int = 3,
    val minRecordsForFindings: Int = 7,
    val minOccurrencesForTag: Int = 3,
    val minOccurrencesForCombination: Int = 3,
    val minGroupSizeForComparison: Int = 3,
    val minDifferencePercent: Double = 8.0,
    val maxFindingCards: Int = 50
)

data class AnalyticsConfig(
    val metrics: List<MetricConfig>,
    val actions: List<ActionConfig>,
    val tagGroups: List<TagGroupConfig>,
    val rules: List<AnalyticsRule>,
    val dashboardMetricOrder: List<String> = emptyList(),
    val thresholds: AnalyticsThresholds = AnalyticsThresholds()
)

typealias ProgramAnalyticsSchema = AnalyticsConfig
typealias MetricSpec = MetricConfig
typealias EventSpec = ActionConfig
typealias DimensionSpec = TagGroupConfig

data class ThresholdSpec(
    val metricKey: String,
    val dangerHigh: Double? = null,
    val dangerLow: Double? = null
)

data class ReminderTypeDefinition(
    val id: String,
    val label: String,
    val labelKey: String? = null
)

data class DataActionDefinition(
    val type: DataActionType,
    val label: String,
    val labelKey: String? = null
)

data class ProgramLocalizationConfig(
    val defaultLocaleTag: String = "en",
    val supportedLocaleTags: Set<String> = setOf("en"),
    val appNameStringKey: String,
    val programNameStringKey: String,
    val translatableStringKeys: Set<String>,
    val aiPromptLocaleField: String = "locale",
    val analyticsLocaleField: String = "locale"
)

data class SensorRule(
    val sensorId: String,
    val transformType: TransformType,
    val operator: ComparisonOperator = ComparisonOperator.GREATER_THAN,
    val threshold: Double? = null,
    val targetMetricId: String? = null,
    val targetTag: String? = null,
    val lookbackMinutes: Long? = null,
    val enabled: Boolean = true
)

data class HardwareSensorConfig(
    val androidSensorType: Int,
    val sensorId: String,
    val displayName: String,
    val unit: String,
    val contextDomain: ContextDomain,
    val priority: ContextPriority = ContextPriority.IMPORTANT,
    val rules: List<SensorRule> = emptyList()
)

data class HealthConnectMapping(
    val recordType: HealthConnectRecordType,
    val metricMappings: Map<String, String>,
    val role: HealthConnectMappingRole = HealthConnectMappingRole.PRIMARY_METRIC,
    val rules: List<SensorRule> = emptyList()
)

data class PlatformIntegrationConfig(
    val hardwareSensors: List<HardwareSensorConfig> = emptyList(),
    val healthConnectMappings: List<HealthConnectMapping> = emptyList(),
    val backgroundReadEnabled: Boolean = false
)

data class UniversalProgramDefinition(
    val programId: String,
    val displayName: String,
    val displayNameKey: String? = null,
    val medicationName: String,
    val defaultDose: Double,
    val metrics: List<MetricType>,
    val metricComponents: List<MetricComponent>,
    val recordSchema: List<RecordField>,
    val tagGroups: List<TagGroupDefinition>,
    val eventInputs: List<EventInputDefinition> = emptyList(),
    val recordScreenBlocks: List<WidgetType>,
    val statisticsScreenBlocks: List<WidgetType>,
    val settingsSections: List<SettingsSectionType>,
    val analyticsRules: List<AnalyticsRuleDefinition>,
    val reminderTypes: List<ReminderTypeDefinition>,
    val dataActions: List<DataActionDefinition>,
    val graphDefinition: GraphDefinition,
    val saveActionDefinition: SaveActionDefinition,
    val editAffordance: EditAffordanceDefinition,
    val visualConfig: ProgramVisualConfig,
    val autoCollection: AutoCollectionConfig = AutoCollectionConfig(),
    val sensorRules: List<SensorRule> = emptyList(),
    val integrations: PlatformIntegrationConfig = PlatformIntegrationConfig()
    ,
    val localization: ProgramLocalizationConfig,
    val premiumConfig: PremiumConfig
)

data class ProgramUiDefinition(
    val recordScreenBlocks: List<WidgetType>,
    val statisticsScreenBlocks: List<WidgetType>,
    val settingsSections: List<SettingsSectionType>,
    val recordBlocks: List<RecordBlockDefinition> = recordScreenBlocks.map { RecordBlockDefinition(it) }
)

data class RawSourceEvent(
    val id: String,
    val sourceType: SourceType,
    val payloadJson: String,
    val capturedAt: Instant,
    val sourceTimestamp: Instant?,
    val schemaVersion: Int,
    val error: String?,
    /** Stable identifier supplied by the upstream provider; never infer it from JSON at read time. */
    val sourceRecordId: String? = null
)

typealias HealthRecord = UserRecord

data class NormalizedObservation(
    val record: UserRecord
)

enum class AnalysisLevel { NONE, BASIC_3_9, COMPARATIVE_10_30, ADVANCED_31_PLUS }

data class DataSummary(
    val recordCount: Int,
    val metricCoverage: Map<String, Int>,
    val eventCoverage: Map<String, Int>,
    val dimensionCoverage: Map<String, Int>,
    val minRecordsForDashboard: Int,
    val minRecordsForFindings: Int,
    val minGroupSizeForComparison: Int
)

data class UserRecord(
    val id: String,
    val programId: String = "",
    val timestamp: Instant,
    val measurements: List<Measurement> = emptyList(),
    val events: List<RecordEvent> = emptyList(),
    val dimensions: List<RecordDimension> = emptyList(),
    val quality: DataQuality = DataQuality(),
    val sourceType: SourceType = SourceType.MANUAL,
    val source: RecordSource = RecordSource(sourceType),
    val note: String? = null,
    val createdAt: Instant = timestamp,
    val updatedAt: Instant = timestamp,
    val flag: RecordFlag = RecordFlag.Normal,
    // Compatibility fields for legacy screens/code paths. New program-driven flows should use
    // measurements, events, and dimensions above.
    val medicationName: String = "",
    val medicationStatus: MedicationStatus = MedicationStatus.NOT_RECORDED,
    val doseValue: Double? = null,
    val doseUnit: String = "",
    val healthyTags: List<String> = emptyList(),
    val unhealthyTags: List<String> = emptyList(),
    val symptomTags: List<String> = emptyList(),
    val otherMedicationTags: List<String> = emptyList(),
    val sideEffectTags: List<String> = emptyList(),
    val customTags: List<String> = emptyList()
)

data class Measurement(
    val key: String,
    val value: Double,
    val unit: String,
    val group: String? = null
)

data class RecordEvent(
    val key: String,
    val name: String,
    val status: String,
    val amount: Double?,
    val unit: String?
)

data class RecordDimension(
    val group: String,
    val key: String,
    val label: String
)

data class DataQuality(
    val complete: Boolean = true,
    val warnings: List<String> = emptyList()
)

data class RecordSource(
    val type: SourceType,
    val name: String? = null
)

typealias Observation = Measurement
typealias ActionEvent = RecordEvent
typealias TagEntry = RecordDimension

data class StatisticMetric(
    val id: String,
    val label: String,
    val value: String,
    val unit: String? = null,
    val role: StatisticRole,
    val sourceRuleId: String,
    val labelKey: String? = null,
    val unitKey: String? = null
)

data class FindingCardModel(
    val id: String,
    val title: String,
    val message: String,
    val metrics: List<StatisticMetric>,
    val basis: String,
    val severity: FindingSeverity,
    val sourceRuleId: String,
    val titleKey: String? = null,
    val messageKey: String? = null,
    val basisKey: String? = null
)

enum class FindingSeverity {
    Positive,
    Neutral,
    Risk
}

enum class EffectDirection { HIGHER, LOWER, SAME, MIXED, UNKNOWN }

data class Finding(
    val stableKey: String,
    val type: String,
    val title: String,
    val message: String,
    val effectDirection: EffectDirection,
    val severity: FindingSeverity,
    val sourceRuleId: String,
    val metricKey: String? = null,
    val dimensionKey: String? = null,
    val eventKey: String? = null,
    val leftGroupLabel: String? = null,
    val rightGroupLabel: String? = null,
    val leftGroupSize: Int? = null,
    val rightGroupSize: Int? = null,
    val recordCount: Int,
    val evidence: List<StatisticMetric> = emptyList(),
    val basis: String,
    val titleKey: String? = null,
    val messageKey: String? = null,
    val basisKey: String? = null
) {
    fun toCardModel(): FindingCardModel = FindingCardModel(
        id = stableKey,
        title = title,
        message = message,
        metrics = evidence,
        basis = basis,
        severity = severity,
        sourceRuleId = sourceRuleId,
        titleKey = titleKey,
        messageKey = messageKey,
        basisKey = basisKey
    )
}

data class AnalyticsState(
    val dashboardMetrics: List<StatisticMetric>,
    val showcaseFindings: List<FindingCardModel>,
    val allFindings: List<Finding>,
    val dataSummary: DataSummary,
    val analysisLevel: AnalysisLevel
) {
    val findings: List<FindingCardModel>
        get() = showcaseFindings
}

fun analysisLevelFor(recordCount: Int): AnalysisLevel = when {
    recordCount >= 31 -> AnalysisLevel.ADVANCED_31_PLUS
    recordCount >= 10 -> AnalysisLevel.COMPARATIVE_10_30
    recordCount >= 3 -> AnalysisLevel.BASIC_3_9
    else -> AnalysisLevel.NONE
}

fun ProgramAnalyticsSchema.withThresholds(thresholds: List<ThresholdSpec>): ProgramAnalyticsSchema {
    return copy(
        metrics = metrics.map { metric ->
            val threshold = thresholds.firstOrNull { it.metricKey == metric.key }
            metric.copy(dangerousHigh = threshold?.dangerHigh, dangerousLow = threshold?.dangerLow)
        }
    )
}
