package com.medmonitoring.program.bloodpressure

import com.medmonitoring.core.analytics.AdherenceRule
import com.medmonitoring.core.analytics.AverageMetricRule
import com.medmonitoring.core.analytics.CountActionStatusRule
import com.medmonitoring.core.analytics.ExtremeValueRule
import com.medmonitoring.core.analytics.FrequentTagCombinationRule
import com.medmonitoring.core.analytics.FrequentTagRule
import com.medmonitoring.core.analytics.MetricByActionStatusRule
import com.medmonitoring.core.analytics.MetricByTagRule
import com.medmonitoring.core.domain.model.*
import com.medmonitoring.core.config.HealthContextCatalog
import com.medmonitoring.core.premium.PremiumConfig
import com.medmonitoring.core.premium.PromoCodeDefinition
import com.medmonitoring.core.premium.PromoReward
import java.time.Duration

object BloodPressureDefinitions {
    private val vitalPalette = MetricZonePalette(
        normalAccentHex = "#237A35",
        normalSurfaceHex = "#F1FAF2",
        cautionAccentHex = "#C47A00",
        cautionSurfaceHex = "#FFF8E8",
        dangerAccentHex = "#D92D3A",
        dangerSurfaceHex = "#FFF1F2"
    )

    private val visualConfig = ProgramVisualConfig(
        theme = ThemeConfig(
            seedColorHex = "#1565C0",
            lightColors = StaticColorSchemeConfig(
                primary = "#1565C0",
                onPrimary = "#FFFFFF",
                primaryContainer = "#D3E3FD",
                secondary = "#E53935",
                onSecondary = "#FFFFFF",
                surface = "#FFFFFF",
                surfaceVariant = "#F7F8FC",
                onSurface = "#111827",
                onSurfaceVariant = "#6B7280",
                outline = "#E8E8E8",
                outlineVariant = "#F0F0F0",
                error = "#DC2626",
                onError = "#FFFFFF"
            )
        ),
        severityPalette = mapOf(
            SeverityLevel.Normal to SemanticColorConfig("#16A34A", "#F0FDF4", "#BBF7D0"),
            SeverityLevel.Elevated to SemanticColorConfig("#D97706", "#FFFBEB", "#FDE68A"),
            SeverityLevel.High to SemanticColorConfig("#EA580C", "#FFF7ED", "#FED7AA"),
            SeverityLevel.Critical to SemanticColorConfig("#DC2626", "#FEF2F2", "#FECACA"),
            SeverityLevel.Neutral to SemanticColorConfig("#6B7280", "#F7F8FC", "#E8E8E8")
        ),
        tagPalettes = mapOf(
            TagColorRole.Healthy to SemanticColorConfig("#16A34A", "#F0FDF4", "#BBF7D0"),
            TagColorRole.Unhealthy to SemanticColorConfig("#DC2626", "#FEF2F2", "#FECACA"),
            TagColorRole.Symptom to SemanticColorConfig("#7C3AED", "#F5F3FF", "#DDD6FE"),
            TagColorRole.Medication to SemanticColorConfig("#2563EB", "#EFF6FF", "#BFDBFE"),
            TagColorRole.Neutral to SemanticColorConfig("#6B7280", "#F7F8FC", "#E8E8E8")
        ),
        components = ComponentStyleConfig(
            appBar = AppBarStyleConfig(
                iconResourceName = "app_mark"
            ),
            saveAction = SaveActionStyleConfig(
                gradientHex = listOf("#1565C0", "#1976D2")
            )
        )
    )

    val program = UniversalProgramDefinition(
        programId = "blood-pressure-monitor",
        displayName = "Blood Pressure Analytics",
        displayNameKey = "program_blood_pressure_name",
        medicationName = "Lisinopril",
        defaultDose = 10.0,
        metrics = listOf(
            MetricType.BLOOD_PRESSURE,
            MetricType.PULSE,
            MetricType.MEDICATION_ADHERENCE
        ),
        metricComponents = listOf(
            MetricComponent("systolic", "SYS", "mmHg", 70..220, 90..129, 80..139, MetricInputStyle.VerticalWheel, vitalPalette, defaultValue = 120, labelKey = "metric_systolic"),
            MetricComponent("diastolic", "DIA", "mmHg", 40..140, 60..79, 50..89, MetricInputStyle.VerticalWheel, vitalPalette, defaultValue = 80, labelKey = "metric_diastolic"),
            MetricComponent("pulse", "Pulse", "bpm", 40..180, 60..100, 50..110, MetricInputStyle.HorizontalWheel, vitalPalette, AggregationStrategy.Average, defaultValue = 70, labelKey = "pulse"),
            MetricComponent("dose", "Dose", "mg", 0..1000, inputStyle = MetricInputStyle.NumericField, palette = vitalPalette, defaultValue = null, isRequired = false, labelKey = "dose")
        ),
        recordSchema = RecordField.entries,
        tagGroups = listOf(
            TagGroupDefinition(
                id = "healthy",
                title = "Healthy",
                icon = "activity",
                colorRole = TagColorRole.Healthy,
                colorHex = "#15803D",
                tags = listOf("Walking", "Cardio", "Stretching", "Good Sleep", "Low Salt Diet", "Breathing Exercises", "Vegetables"),
                otherEnabled = true,
                titleKey = "group_healthy",
                tagKeys = mapOf(
                    "Walking" to "tag_walking",
                    "Cardio" to "tag_cardio",
                    "Stretching" to "tag_stretching",
                    "Good Sleep" to "tag_good_sleep",
                    "Low Salt Diet" to "tag_low_salt_diet",
                    "Breathing Exercises" to "tag_breathing_exercises",
                    "Vegetables" to "tag_vegetables"
                )
            ),
            TagGroupDefinition(
                id = "unhealthy",
                title = "Unhealthy",
                icon = "warning",
                colorRole = TagColorRole.Unhealthy,
                colorHex = "#DC2626",
                tags = listOf("Alcohol", "Smoking", "Fast Food", "Salt", "Irregular Eating", "Energy Drinks", "Poor Sleep", "Long Sitting"),
                otherEnabled = true,
                titleKey = "group_unhealthy",
                tagKeys = mapOf(
                    "Alcohol" to "tag_alcohol",
                    "Smoking" to "tag_smoking",
                    "Fast Food" to "tag_fast_food",
                    "Salt" to "tag_salt",
                    "Irregular Eating" to "tag_irregular_eating",
                    "Energy Drinks" to "tag_energy_drinks",
                    "Poor Sleep" to "tag_poor_sleep",
                    "Long Sitting" to "tag_long_sitting"
                )
            ),
            TagGroupDefinition(
                id = "symptoms",
                title = "Symptoms / Side effects",
                icon = "symptoms",
                colorRole = TagColorRole.Symptom,
                colorHex = "#7C3AED",
                tags = listOf("Headache", "Dizziness", "Fatigue", "Fast Heartbeat", "Coordination Problems", "Weakness", "Nausea", "Itchy Skin", "Dry Cough", "Swelling"),
                otherEnabled = true,
                titleKey = "group_symptoms",
                tagKeys = mapOf(
                    "Headache" to "tag_headache",
                    "Dizziness" to "tag_dizziness",
                    "Fatigue" to "tag_fatigue",
                    "Fast Heartbeat" to "tag_fast_heartbeat",
                    "Coordination Problems" to "tag_coordination_problems",
                    "Weakness" to "tag_weakness",
                    "Nausea" to "tag_nausea",
                    "Itchy Skin" to "tag_itchy_skin",
                    "Dry Cough" to "tag_dry_cough",
                    "Swelling" to "tag_swelling"
                )
            ),
            TagGroupDefinition(
                id = "other_medications",
                title = "Other Medications",
                icon = "medication",
                colorRole = TagColorRole.Medication,
                colorHex = "#2563EB",
                tags = listOf("Aspirin", "Statin", "Beta Blocker", "Diuretic", "Calcium Channel Blocker", "Other Medication"),
                otherEnabled = true,
                titleKey = "group_other_medications",
                tagKeys = mapOf(
                    "Aspirin" to "tag_aspirin",
                    "Statin" to "tag_statin",
                    "Beta Blocker" to "tag_beta_blocker",
                    "Diuretic" to "tag_diuretic",
                    "Calcium Channel Blocker" to "tag_calcium_channel_blocker",
                    "Other Medication" to "tag_other_medication"
                )
            ),
            TagGroupDefinition(
                id = "custom",
                title = "Custom",
                icon = "custom",
                colorRole = TagColorRole.Neutral,
                colorHex = "#6B7280",
                tags = emptyList(),
                otherEnabled = true,
                titleKey = "group_custom"
            )
        ),
        eventInputs = listOf(
            EventInputDefinition(
                key = "medication",
                label = "Medication",
                labelKey = "medication",
                defaultName = "Lisinopril",
                defaultAmount = 10.0,
                defaultUnit = "mg",
                statuses = listOf(
                    EventStatusDefinition("TAKEN", "Taken", "taken", positive = true),
                    EventStatusDefinition("MISSED", "Missed", "missed")
                )
            )
        ),
        recordScreenBlocks = listOf(
            WidgetType.GraphWidget,
            WidgetType.DateTimeWidget,
            WidgetType.EventStatusWidget,
            WidgetType.EventAmountWidget,
            WidgetType.PairedVerticalWheelInputWidget,
            WidgetType.SingleHorizontalWheelInputWidget,
            WidgetType.TagGroupsWidget,
            WidgetType.NoteWidget,
            WidgetType.SaveButtonWidget
        ),
        statisticsScreenBlocks = listOf(
            WidgetType.AnalyticsSummaryWidget,
            WidgetType.AnalyticsDetailsWidget,
            WidgetType.HistoryWidget
        ),
        settingsSections = listOf(
            SettingsSectionType.UnitsSection,
            SettingsSectionType.RemindersSection,
            SettingsSectionType.DataExportImportSection,
            SettingsSectionType.HealthConnectSection,
            SettingsSectionType.SensorLaboratorySection,
            SettingsSectionType.AboutSection
        ),
        analyticsRules = listOf(
            AnalyticsRuleDefinition("adherence", "Medication adherence"),
            AnalyticsRuleDefinition("averages", "Average blood pressure and pulse"),
            AnalyticsRuleDefinition("frequencies", "Most frequent symptoms and factors"),
            AnalyticsRuleDefinition("missed_sys", "Missed dose blood pressure comparison")
        ),
        reminderTypes = listOf(
            ReminderTypeDefinition("medication", "Medication", "medication"),
            ReminderTypeDefinition("measurement", "Measurement", "measurement"),
            ReminderTypeDefinition("diary", "Diary", "diary")
        ),
        dataActions = listOf(
            DataActionDefinition(DataActionType.ExportCsv, "Export CSV", "export_csv"),
            DataActionDefinition(DataActionType.ImportCsv, "Import CSV", "import_csv")
        ),
        graphDefinition = GraphDefinition(
            metrics = listOf("systolic", "diastolic", "pulse"),
            series = listOf(
                GraphSeriesDefinition("systolic", "SYS", GraphSeriesType.Line, "#1976D2", "metric_systolic"),
                GraphSeriesDefinition("diastolic", "DIA", GraphSeriesType.Line, "#D32F2F", "metric_diastolic"),
                GraphSeriesDefinition("pulse", "Pulse", GraphSeriesType.PointOrLine, "#2E7D32", "pulse"),
                GraphSeriesDefinition("medicationStatus", "Medication", GraphSeriesType.EventMarker, "#237A35", "medication")
            ),
            yAxisStrategy = AxisScaleStrategy.PaddedDataRange,
            minPadding = 10,
            maxPadding = 10,
            visibleRecordCount = 5,
            recordSlotWidthDp = 116,
            horizontalPastScroll = true,
            showGrid = false,
            showAxisLabels = true,
            showLegend = true,
            showTapMarker = true,
            showValueLabels = true,
            lineThicknessDp = 8f,
            lineCurvature = 0.35f,
            eventMarkerSizeDp = 22,
            eventLaneGapDp = 2,
            pointSeriesInBackground = false,
            safeZones = listOf(
                GraphSafeZoneDefinition("blood_pressure", 60..130, "#4CAF50", 0.10f)
            ),
            pointOverlay = GraphPointOverlayDefinition(
                metricId = "pulse",
                symbol = "♥",
                normalRange = 60..100,
                safeColorHex = "#2E7D32",
                dangerColorHex = "#D32F2F"
            ),
            eventMarkers = listOf(
                GraphEventMarkerDefinition(
                    seriesId = "medicationStatus",
                    eventKey = "medication",
                    activeStatuses = setOf("TAKEN", "taken"),
                    symbol = "\uD83D\uDC8A",
                    laneBottomDp = 2
                )
            ),
            emptyState = EmptyStateDefinition(
                title = "Discover what affects your blood pressure",
                message = "Record blood pressure with daily habits to reveal patterns over time.",
                actionLabel = "Set reminder",
                imageResourceName = "onboarding_blood_pressure",
                imageContentDescription = "Woman measuring blood pressure while tracking lifestyle factors",
                instructions = listOf(
                    "Enter blood pressure, pulse, and medication.",
                    "Select lifestyle factors and symptoms.",
                    "Save to replace this guide with your chart."
                ),
                titleKey = "graph_empty_bp_title",
                messageKey = "graph_empty_bp_message",
                actionLabelKey = "graph_empty_bp_action",
                imageContentDescriptionKey = "graph_empty_bp_image_desc",
                instructionKeys = listOf(
                    "graph_empty_bp_instruction_1",
                    "graph_empty_bp_instruction_2",
                    "graph_empty_bp_instruction_3"
                )
            )
        ),
        saveActionDefinition = SaveActionDefinition(
            previewPattern = "{systolic}/{diastolic} \u2764 {pulse} {action}",
            fallbackText = "{action}"
        ),
        editAffordance = EditAffordanceDefinition(
            icon = EditAffordanceIcon.MaterialEdit,
            tintHex = "#6B7280",
            sizeSp = 16
        ),
        visualConfig = visualConfig,
        autoCollection = AutoCollectionConfig(
            recordSlots = listOf(
                RecordSlotDefinition("morning", 5, 11),
                RecordSlotDefinition("day", 11, 17),
                RecordSlotDefinition("evening", 17, 22),
                RecordSlotDefinition("night", 22, 5)
            )
        ),
        integrations = PlatformIntegrationConfig(
            hardwareSensors = HealthContextCatalog.hardwareSensors,
            healthConnectMappings = listOf(
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.BLOOD_PRESSURE,
                    metricMappings = mapOf("systolic" to "systolic", "diastolic" to "diastolic"),
                    role = HealthConnectMappingRole.PRIMARY_METRIC
                ),
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.HEART_RATE,
                    metricMappings = mapOf("beatsPerMinute" to "pulse"),
                    role = HealthConnectMappingRole.PRIMARY_METRIC
                ),
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.STEPS,
                    metricMappings = emptyMap(),
                    role = HealthConnectMappingRole.CONTEXT_TAG,
                    rules = listOf(
                        SensorRule(
                            sensorId = "hc.steps.count",
                            transformType = TransformType.TAG,
                            threshold = 3000.0,
                            targetTag = "Active Walking"
                        )
                    )
                ),
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.EXERCISE,
                    metricMappings = emptyMap(),
                    role = HealthConnectMappingRole.CONTEXT_TAG,
                    rules = listOf(
                        SensorRule(
                            sensorId = "hc.exercise.durationMinutes",
                            transformType = TransformType.TAG,
                            threshold = 10.0,
                            targetTag = "Workout"
                        )
                    )
                ),
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.SLEEP,
                    metricMappings = emptyMap(),
                    role = HealthConnectMappingRole.CONTEXT_TAG,
                    rules = listOf(
                        SensorRule(
                            sensorId = "hc.sleep.durationHours",
                            transformType = TransformType.TAG,
                            threshold = 7.0,
                            targetTag = "Good Sleep"
                        )
                    )
                ),
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.WEIGHT,
                    metricMappings = emptyMap(),
                    role = HealthConnectMappingRole.CONTEXT_TAG,
                    rules = listOf(
                        SensorRule(
                            sensorId = "hc.weight.weight",
                            transformType = TransformType.TAG,
                            threshold = 90.0,
                            targetTag = "context.nutrition.weight_above_target"
                        )
                    )
                ),
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.BLOOD_GLUCOSE,
                    metricMappings = emptyMap(),
                    role = HealthConnectMappingRole.CONTEXT_TAG,
                    rules = listOf(
                        SensorRule(
                            sensorId = "hc.blood_glucose.level",
                            transformType = TransformType.TAG,
                            threshold = 7.8,
                            targetTag = "context.nutrition.glucose_high"
                        )
                    )
                )
            ),
            backgroundReadEnabled = true
        ),
        localization = ProgramLocalizationConfig(
            defaultLocaleTag = "en",
            supportedLocaleTags = setOf(
                "en",
                "ar",
                "de",
                "es",
                "fr",
                "hi",
                "id",
                "it",
                "ja",
                "ko",
                "nl",
                "pl",
                "pt",
                "ru",
                "tr",
                "uk",
                "zh-Hans-CN"
            ),
            appNameStringKey = "app_name",
            programNameStringKey = "program_blood_pressure_name",
            translatableStringKeys = setOf(
                "app_name",
                "program_blood_pressure_name",
                "blood_pressure",
                "medication",
                "taken",
                "missed",
                "measurement",
                "diary",
                "dose",
                "pulse",
                "metric_systolic",
                "metric_diastolic",
                "graph_empty_bp_title",
                "graph_empty_bp_message",
                "graph_empty_bp_action",
                "graph_empty_bp_image_desc",
                "graph_empty_bp_instruction_1",
                "graph_empty_bp_instruction_2",
                "graph_empty_bp_instruction_3",
                "group_healthy",
                "group_unhealthy",
                "group_symptoms",
                "group_other_medications",
                "group_custom",
                "group_time_of_day",
                "tag_walking",
                "tag_cardio",
                "tag_stretching",
                "tag_good_sleep",
                "tag_low_salt_diet",
                "tag_breathing_exercises",
                "tag_vegetables",
                "tag_alcohol",
                "tag_smoking",
                "tag_fast_food",
                "tag_salt",
                "tag_irregular_eating",
                "tag_energy_drinks",
                "tag_poor_sleep",
                "tag_long_sitting",
                "tag_headache",
                "tag_dizziness",
                "tag_fatigue",
                "tag_fast_heartbeat",
                "tag_coordination_problems",
                "tag_weakness",
                "tag_nausea",
                "tag_itchy_skin",
                "tag_dry_cough",
                "tag_swelling",
                "tag_aspirin",
                "tag_statin",
                "tag_beta_blocker",
                "tag_diuretic",
                "tag_calcium_channel_blocker",
                "tag_other_medication",
                "rule_adherence",
                "rule_averages",
                "rule_frequencies",
                "rule_missed_sys",
                "export_csv",
                "import_csv",
                "dashboard_avg_sys",
                "dashboard_avg_dia",
                "dashboard_avg_pulse",
                "dashboard_adherence",
                "dashboard_missed_medication",
                "dashboard_records",
                "ai_prompt_language_instruction",
                "ai_analysis_running",
                "ai_analysis_ready",
                "ai_notification_channel_analysis",
                "ai_notification_channel_motivation",
                "ai_notification_title_analysis",
                "ai_notification_title_health_insights"
            )
        ),
        premiumConfig = PremiumConfig(
            monthlyProductId = "subs_monthly_2usd",
            yearlyProductId = "subs_yearly_20usd",
            promoCodes = listOf(
                PromoCodeDefinition(
                    code = "PREMIUM-UNLIMITED",
                    reward = PromoReward.LIFETIME_PREMIUM
                ),
                PromoCodeDefinition(
                    code = "PREMIUM-90-DAYS",
                    reward = PromoReward.EXTENDED_PREMIUM,
                    duration = Duration.ofDays(90)
                )
            )
        )
    )

    val ui = ProgramUiDefinition(
        recordScreenBlocks = listOf(
            WidgetType.GraphWidget,
            WidgetType.DateTimeWidget,
            WidgetType.EventStatusWidget,
            WidgetType.PairedVerticalWheelInputWidget,
            WidgetType.SingleHorizontalWheelInputWidget,
            WidgetType.TagGroupsWidget,
            WidgetType.NoteWidget,
            WidgetType.SaveButtonWidget
        ),
        statisticsScreenBlocks = program.statisticsScreenBlocks,
        settingsSections = program.settingsSections,
        recordBlocks = listOf(
            RecordBlockDefinition(WidgetType.GraphWidget),
            RecordBlockDefinition(WidgetType.DateTimeWidget),
            RecordBlockDefinition(WidgetType.EventStatusWidget, "medication"),
            RecordBlockDefinition(WidgetType.PairedVerticalWheelInputWidget, "systolic,diastolic"),
            RecordBlockDefinition(WidgetType.SingleHorizontalWheelInputWidget, "pulse"),
            RecordBlockDefinition(WidgetType.TagGroupsWidget),
            RecordBlockDefinition(WidgetType.NoteWidget),
            RecordBlockDefinition(WidgetType.SaveButtonWidget)
        )
    )

    val analyticsConfig = ProgramAnalyticsSchema(
        metrics = listOf(
            MetricSpec("systolic", "SYS", "mmHg", role = StatisticRole.Primary, meaningfulDifference = 5.0, dangerousHigh = 180.0, labelKey = "metric_systolic"),
            MetricConfig("diastolic", "DIA", "mmHg", role = StatisticRole.Primary, meaningfulDifference = 3.0, labelKey = "metric_diastolic"),
            MetricSpec("pulse", "Pulse", "bpm", role = StatisticRole.Secondary, meaningfulDifference = 5.0, dangerousHigh = 130.0, labelKey = "pulse")
        ),
        actions = listOf(
            EventSpec("medication", statuses = listOf("taken", "missed"))
        ),
        tagGroups = listOf(
            DimensionSpec("healthy", "Healthy", "group_healthy"),
            DimensionSpec("unhealthy", "Unhealthy", "group_unhealthy"),
            DimensionSpec("symptoms", "Symptoms", "group_symptoms"),
            DimensionSpec("other_medications", "Other Medications", "group_other_medications"),
            DimensionSpec("custom", "Custom", "group_custom"),
            DimensionSpec("time_of_day", "Time of day", "group_time_of_day")
        ),
        rules = listOf(
            AverageMetricRule(metricKey = "systolic"),
            AverageMetricRule(metricKey = "diastolic"),
            AverageMetricRule(metricKey = "pulse"),
            CountActionStatusRule(actionKey = "medication", status = "missed"),
            AdherenceRule(actionKey = "medication", positiveStatus = "taken", negativeStatus = "missed"),
            FrequentTagRule(groupKey = "symptoms"),
            FrequentTagRule(groupKey = "healthy"),
            FrequentTagRule(groupKey = "unhealthy"),
            FrequentTagRule(groupKey = "other_medications"),
            FrequentTagCombinationRule(groups = listOf("unhealthy", "symptoms")),
            MetricByTagRule(metricKey = "systolic", tagGroupKey = "unhealthy"),
            MetricByActionStatusRule(metricKey = "systolic", actionKey = "medication", statuses = listOf("taken", "missed")),
            ExtremeValueRule(metricKey = "systolic", mode = "highest"),
            ExtremeValueRule(metricKey = "systolic", mode = "lowest"),
            ExtremeValueRule(metricKey = "pulse", mode = "highest")
        ),
        dashboardMetricOrder = listOf(
            "dashboard_adherence",
            "dashboard_avg_sys",
            "dashboard_avg_dia",
            "dashboard_avg_pulse",
            "dashboard_missed_medication",
            "dashboard_records"
        ),
        thresholds = AnalyticsThresholds(
            minRecordsForDashboard = 3,
            minRecordsForFindings = 5,
            minOccurrencesForTag = 3,
            minOccurrencesForCombination = 3,
            minGroupSizeForComparison = 2,
            minDifferencePercent = 8.0,
            maxFindingCards = 50
        )
    )
}
