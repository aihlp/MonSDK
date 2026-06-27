package com.medmonitoring

import com.medmonitoring.core.analytics.AnalyticsEngine
import com.medmonitoring.core.analytics.AverageMetricRule
import com.medmonitoring.core.analytics.ExtremeValueRule
import com.medmonitoring.core.config.ConfigAudit
import com.medmonitoring.core.domain.model.*
import com.medmonitoring.core.program.GenericProgramRecordMapper
import com.medmonitoring.core.program.ProgramModuleDefinition
import com.medmonitoring.core.ai.AiModelRegistry
import com.medmonitoring.core.ai.AiPromptBuilder
import com.medmonitoring.core.premium.PremiumConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DiabetesProgramFixtureTest {
    @Test
    fun diabetesProgramPassesConfigAuditWithoutBaseCodeChanges() {
        assertEquals(emptyList<String>(), ConfigAudit.validate(DiabetesProgramFixture.program, DiabetesProgramFixture.ui))
    }

    @Test
    fun diabetesProgramUsesGlucoseAsPrimaryHealthConnectMetric() {
        val mappings = DiabetesProgramFixture.program.integrations.healthConnectMappings

        assertEquals(
            setOf(HealthConnectRecordType.BLOOD_GLUCOSE),
            mappings.filter { it.role == HealthConnectMappingRole.PRIMARY_METRIC }.map { it.recordType }.toSet()
        )
        assertFalse(mappings.any { it.recordType == HealthConnectRecordType.BLOOD_PRESSURE })
        assertEquals(mapOf("level" to "glucose"), mappings.first().metricMappings)
    }

    @Test
    fun diabetesAnalyticsAndAiPromptAreNotBloodPressureSpecific() {
        val records = (1..5).map { index ->
            UserRecord(
                id = "glucose-$index",
                programId = DiabetesProgramFixture.program.programId,
                timestamp = Instant.parse("2026-06-2${index}T08:00:00Z"),
                measurements = listOf(Measurement("glucose", 5.5 + index, "mmol/L")),
                dimensions = listOf(RecordDimension("meal_context", "fasting", "Fasting"))
            )
        }
        val mapped = GenericProgramRecordMapper(DiabetesProgramFixture.program).mapAll(records)
        val analytics = AnalyticsEngine().calculate(mapped, DiabetesProgramFixture.analytics)
        val request = AiPromptBuilder().buildDailyRequest(
            program = DiabetesProgramFixture.program,
            records = mapped,
            analytics = analytics,
            anamnesis = "Type 2 diabetes self-monitoring.",
            model = AiModelRegistry.recommendedModels.first()
        )

        assertEquals(5, analytics.dataSummary.recordCount)
        assertTrue(analytics.dashboardMetrics.any { it.id == "dashboard_avg_glucose" })
        assertTrue(request.prompt.contains("\"recordCount\":5"))
        assertTrue(request.prompt.contains("glucose", ignoreCase = true))
        assertFalse(request.prompt.contains("blood-pressure-monitor"))
        assertFalse(request.prompt.contains("systolic", ignoreCase = true))
        assertFalse(request.prompt.contains("diastolic", ignoreCase = true))
    }
}

private object DiabetesProgramFixture : ProgramModuleDefinition {
    private val palette = MetricZonePalette(
        normalAccentHex = "#15803D",
        normalSurfaceHex = "#F0FDF4",
        cautionAccentHex = "#D97706",
        cautionSurfaceHex = "#FFFBEB",
        dangerAccentHex = "#DC2626",
        dangerSurfaceHex = "#FEF2F2"
    )

    private val visuals = ProgramVisualConfig(
        theme = ThemeConfig(
            seedColorHex = "#0F766E",
            lightColors = StaticColorSchemeConfig(
                primary = "#0F766E",
                onPrimary = "#FFFFFF",
                primaryContainer = "#CCFBF1",
                secondary = "#2563EB",
                onSecondary = "#FFFFFF",
                surface = "#FFFFFF",
                surfaceVariant = "#F8FAFC",
                onSurface = "#111827",
                onSurfaceVariant = "#64748B",
                outline = "#CBD5E1",
                outlineVariant = "#E2E8F0",
                error = "#DC2626",
                onError = "#FFFFFF"
            )
        ),
        severityPalette = SeverityLevel.entries.associateWith {
            when (it) {
                SeverityLevel.Normal -> SemanticColorConfig("#15803D", "#F0FDF4", "#BBF7D0")
                SeverityLevel.Elevated -> SemanticColorConfig("#D97706", "#FFFBEB", "#FDE68A")
                SeverityLevel.High -> SemanticColorConfig("#EA580C", "#FFF7ED", "#FED7AA")
                SeverityLevel.Critical -> SemanticColorConfig("#DC2626", "#FEF2F2", "#FECACA")
                SeverityLevel.Neutral -> SemanticColorConfig("#64748B", "#F8FAFC", "#CBD5E1")
            }
        },
        tagPalettes = TagColorRole.entries.associateWith {
            when (it) {
                TagColorRole.Healthy -> SemanticColorConfig("#15803D", "#F0FDF4", "#BBF7D0")
                TagColorRole.Unhealthy -> SemanticColorConfig("#DC2626", "#FEF2F2", "#FECACA")
                TagColorRole.Symptom -> SemanticColorConfig("#7C3AED", "#F5F3FF", "#DDD6FE")
                TagColorRole.Medication -> SemanticColorConfig("#2563EB", "#EFF6FF", "#BFDBFE")
                TagColorRole.Neutral -> SemanticColorConfig("#64748B", "#F8FAFC", "#CBD5E1")
            }
        }
    )

    override val program = UniversalProgramDefinition(
        programId = "diabetes-glucose-monitor",
        displayName = "Glucose Monitoring",
        displayNameKey = "program_glucose_name",
        medicationName = "Insulin",
        defaultDose = 0.0,
        metrics = emptyList(),
        metricComponents = listOf(
            MetricComponent("glucose", "Glucose", "mmol/L", 2..25, 4..7, 8..13, MetricInputStyle.NumericField, palette, AggregationStrategy.Average, defaultValue = 6, labelKey = "metric_glucose"),
            MetricComponent("carbs", "Carbs", "g", 0..300, inputStyle = MetricInputStyle.NumericField, palette = palette, defaultValue = null, isRequired = false, labelKey = "metric_carbs")
        ),
        recordSchema = RecordField.entries,
        tagGroups = listOf(
            TagGroupDefinition("meal_context", "Meal context", "nutrition", TagColorRole.Healthy, "#15803D", listOf("Fasting", "Before meal", "After meal"), otherEnabled = true, titleKey = "group_meal_context", tagKeys = mapOf("Fasting" to "tag_fasting", "Before meal" to "tag_before_meal", "After meal" to "tag_after_meal")),
            TagGroupDefinition("symptoms", "Symptoms", "symptoms", TagColorRole.Symptom, "#7C3AED", listOf("Thirst", "Shaking", "Fatigue"), otherEnabled = true, titleKey = "group_symptoms", tagKeys = mapOf("Thirst" to "tag_thirst", "Shaking" to "tag_shaking", "Fatigue" to "tag_fatigue")),
            TagGroupDefinition("custom", "Custom", "custom", TagColorRole.Neutral, "#64748B", emptyList(), otherEnabled = true, titleKey = "group_custom")
        ),
        eventInputs = listOf(
            EventInputDefinition("medication", "Medication", "medication", defaultName = "Insulin", defaultUnit = "units", statuses = listOf(EventStatusDefinition("TAKEN", "Taken", "taken", positive = true), EventStatusDefinition("MISSED", "Missed", "missed")))
        ),
        recordScreenBlocks = listOf(WidgetType.GraphWidget, WidgetType.DateTimeWidget, WidgetType.EventStatusWidget, WidgetType.EventAmountWidget, WidgetType.SingleHorizontalWheelInputWidget, WidgetType.TagGroupsWidget, WidgetType.NoteWidget, WidgetType.SaveButtonWidget),
        statisticsScreenBlocks = listOf(WidgetType.AnalyticsSummaryWidget, WidgetType.AnalyticsDetailsWidget, WidgetType.HistoryWidget),
        settingsSections = listOf(SettingsSectionType.UnitsSection, SettingsSectionType.RemindersSection, SettingsSectionType.DataExportImportSection, SettingsSectionType.HealthConnectSection, SettingsSectionType.AboutSection),
        analyticsRules = listOf(AnalyticsRuleDefinition("averages", "Average glucose"), AnalyticsRuleDefinition("frequencies", "Frequent contexts")),
        reminderTypes = listOf(ReminderTypeDefinition("measurement", "Measurement", "measurement"), ReminderTypeDefinition("medication", "Medication", "medication")),
        dataActions = listOf(DataActionDefinition(DataActionType.ExportCsv, "Export CSV", "export_csv"), DataActionDefinition(DataActionType.ImportCsv, "Import CSV", "import_csv")),
        graphDefinition = GraphDefinition(
            metrics = listOf("glucose"),
            series = listOf(GraphSeriesDefinition("glucose", "Glucose", GraphSeriesType.Line, "#0F766E", "metric_glucose"), GraphSeriesDefinition("medicationStatus", "Medication", GraphSeriesType.EventMarker, "#2563EB", "medication")),
            horizontalPastScroll = true,
            showGrid = true,
            showAxisLabels = true,
            showLegend = true,
            showTapMarker = true,
            safeZones = listOf(GraphSafeZoneDefinition("glucose", 4..7, "#15803D", 0.12f)),
            pointOverlay = GraphPointOverlayDefinition("glucose", "●", 4..7, "#15803D", "#DC2626"),
            eventMarkers = listOf(GraphEventMarkerDefinition("medicationStatus", "medication", setOf("TAKEN"), "💉")),
            emptyState = EmptyStateDefinition("No glucose data", "Add glucose readings to see trends.", "Add reading", "app_mark", "Glucose monitoring", listOf("Record glucose", "Add meal context"), "ai_no_data_title", "ai_no_data_body", "save_record", "program_glucose_name", listOf("metric_glucose"))
        ),
        saveActionDefinition = SaveActionDefinition("{glucose} mmol/L", "Save glucose reading"),
        editAffordance = EditAffordanceDefinition(EditAffordanceIcon.MaterialEdit, "#64748B", 16),
        visualConfig = visuals,
        integrations = PlatformIntegrationConfig(
            healthConnectMappings = listOf(
                HealthConnectMapping(HealthConnectRecordType.BLOOD_GLUCOSE, mapOf("level" to "glucose"), HealthConnectMappingRole.PRIMARY_METRIC)
            )
        ),
        localization = ProgramLocalizationConfig(
            appNameStringKey = "app_name",
            programNameStringKey = "program_glucose_name",
            translatableStringKeys = setOf("app_name", "program_glucose_name", "metric_glucose", "metric_carbs", "group_meal_context", "tag_fasting", "tag_before_meal", "tag_after_meal", "group_symptoms", "tag_thirst", "tag_shaking", "tag_fatigue", "group_custom", "medication", "taken", "missed", "measurement", "export_csv", "import_csv", "ai_no_data_title", "ai_no_data_body", "save_record")
        ),
        premiumConfig = PremiumConfig("glucose_monthly", "glucose_yearly")
    )

    override val ui = ProgramUiDefinition(program.recordScreenBlocks, program.statisticsScreenBlocks, program.settingsSections)
    override val analytics = AnalyticsConfig(
        metrics = listOf(MetricConfig("glucose", "Glucose", "mmol/L", StatisticRole.Primary, dangerousHigh = 13.0, dangerousLow = 3.5, labelKey = "metric_glucose")),
        actions = listOf(ActionConfig("medication", listOf("TAKEN", "MISSED"))),
        tagGroups = listOf(TagGroupConfig("meal_context", "Meal context", "group_meal_context"), TagGroupConfig("symptoms", "Symptoms", "group_symptoms")),
        rules = listOf(AverageMetricRule("glucose"), ExtremeValueRule("glucose", "highest")),
        dashboardMetricOrder = listOf("dashboard_avg_glucose", "dashboard_records")
    )
    override val recordMapper = GenericProgramRecordMapper(program)
}
