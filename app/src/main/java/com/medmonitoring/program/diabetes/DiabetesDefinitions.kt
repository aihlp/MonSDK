package com.medmonitoring.program.diabetes

import com.medmonitoring.core.analytics.AverageMetricRule
import com.medmonitoring.core.analytics.ExtremeValueRule
import com.medmonitoring.core.analytics.FrequentTagRule
import com.medmonitoring.core.analytics.MetricByTagCombinationRule
import com.medmonitoring.core.analytics.MetricByTagRule
import com.medmonitoring.core.config.HealthContextCatalog
import com.medmonitoring.core.domain.model.*
import com.medmonitoring.core.premium.PremiumConfig

object DiabetesDefinitions {
    private val glucosePalette = MetricZonePalette(
        normalAccentHex = "#15803D",
        normalSurfaceHex = "#F0FDF4",
        cautionAccentHex = "#D97706",
        cautionSurfaceHex = "#FFFBEB",
        dangerAccentHex = "#DC2626",
        dangerSurfaceHex = "#FEF2F2"
    )

    private val visualConfig = ProgramVisualConfig(
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
            ),
            darkColors = StaticColorSchemeConfig(
                primary = "#5EEAD4",
                onPrimary = "#042F2E",
                primaryContainer = "#134E4A",
                secondary = "#93C5FD",
                onSecondary = "#172554",
                surface = "#0F172A",
                surfaceVariant = "#1E293B",
                onSurface = "#E5E7EB",
                onSurfaceVariant = "#CBD5E1",
                outline = "#64748B",
                outlineVariant = "#334155",
                error = "#FCA5A5",
                onError = "#450A0A"
            ),
            useDynamicColor = false
        ),
        severityPalette = mapOf(
            SeverityLevel.Normal to SemanticColorConfig("#15803D", "#F0FDF4", "#BBF7D0"),
            SeverityLevel.Elevated to SemanticColorConfig("#D97706", "#FFFBEB", "#FDE68A"),
            SeverityLevel.High to SemanticColorConfig("#EA580C", "#FFF7ED", "#FED7AA"),
            SeverityLevel.Critical to SemanticColorConfig("#DC2626", "#FEF2F2", "#FECACA"),
            SeverityLevel.Neutral to SemanticColorConfig("#64748B", "#F8FAFC", "#CBD5E1")
        ),
        tagPalettes = mapOf(
            TagColorRole.Healthy to SemanticColorConfig("#15803D", "#F0FDF4", "#BBF7D0"),
            TagColorRole.Unhealthy to SemanticColorConfig("#DC2626", "#FEF2F2", "#FECACA"),
            TagColorRole.Symptom to SemanticColorConfig("#7C3AED", "#F5F3FF", "#DDD6FE"),
            TagColorRole.Medication to SemanticColorConfig("#2563EB", "#EFF6FF", "#BFDBFE"),
            TagColorRole.Neutral to SemanticColorConfig("#64748B", "#F8FAFC", "#CBD5E1")
        ),
        components = ComponentStyleConfig(
            saveAction = SaveActionStyleConfig(
                gradientHex = listOf("#0F766E", "#14B8A6")
            )
        )
    )

    val program = UniversalProgramDefinition(
        programId = "diabetes-glucose-monitor",
        displayName = "Glucose Monitoring",
        displayNameKey = "program_glucose_name",
        medicationName = "Insulin",
        defaultDose = 0.0,
        metrics = emptyList(),
        metricComponents = listOf(
            MetricComponent(
                id = "glucose",
                label = "Glucose",
                unit = "mmol/L",
                inputRange = 2..25,
                normalRange = 4..7,
                cautionRange = 8..13,
                inputStyle = MetricInputStyle.HorizontalWheel,
                palette = glucosePalette,
                aggregationStrategy = AggregationStrategy.Average,
                defaultValue = 6,
                labelKey = "metric_glucose"
            ),
            MetricComponent(
                id = "weight",
                label = "Weight",
                unit = "kg",
                inputRange = 30..250,
                inputStyle = MetricInputStyle.HorizontalWheel,
                palette = glucosePalette,
                aggregationStrategy = AggregationStrategy.Average,
                defaultValue = 80,
                labelKey = "metric_weight"
            ),
            MetricComponent(
                id = "height",
                label = "Height",
                unit = "cm",
                inputRange = 100..230,
                inputStyle = MetricInputStyle.NumericField,
                palette = glucosePalette,
                defaultValue = 170,
                isRequired = false,
                labelKey = "metric_height",
                // Supporting input: used to compute BMI, kept in the model but not tracked or shown
                // as a main value. It still appears (editable) inside the BMI input block.
                isTracked = false,
                isVisible = false
            ),
            MetricComponent(
                id = "bmi",
                label = "BMI",
                unit = "kg/m2",
                inputRange = 10..60,
                normalRange = 18..25,
                cautionRange = 25..30,
                inputStyle = MetricInputStyle.NumericField,
                palette = glucosePalette,
                aggregationStrategy = AggregationStrategy.Average,
                defaultValue = null,
                isRequired = false,
                labelKey = "metric_bmi",
                computedFrom = ComputedMetricDefinition(
                    formula = ComputedMetricFormula.BMI,
                    sourceMetricIds = listOf("weight", "height"),
                    sourceInputConfigs = mapOf(
                        "height" to InputBlockConfig(
                            step = 1.0,
                            decimalPlaces = 0,
                            unitModes = listOf(
                                InputUnitMode(
                                    id = "cm",
                                    label = "cm",
                                    unit = "cm",
                                    toCanonicalFactor = 1.0
                                ),
                                InputUnitMode(
                                    id = "in",
                                    label = "in",
                                    unit = "in",
                                    toCanonicalFactor = 2.54
                                )
                            )
                        )
                    )
                )
            )
        ),
        recordSchema = RecordField.entries,
        tagGroups = listOf(
            TagGroupDefinition(
                id = "healthy",
                title = "Healthy",
                icon = "activity",
                colorRole = TagColorRole.Healthy,
                colorHex = "#15803D",
                tags = listOf("Walking", "Cardio", "Good Sleep", "Low Carb", "Vegetables", "Regular Meal", "Hydration"),
                otherEnabled = true,
                titleKey = "group_healthy",
                tagKeys = mapOf(
                    "Walking" to "tag_walking",
                    "Cardio" to "tag_cardio",
                    "Good Sleep" to "tag_good_sleep",
                    "Low Carb" to "tag_low_carb",
                    "Vegetables" to "tag_vegetables",
                    "Regular Meal" to "tag_regular_meal",
                    "Hydration" to "tag_hydration"
                )
            ),
            TagGroupDefinition(
                id = "unhealthy",
                title = "Unhealthy",
                icon = "warning",
                colorRole = TagColorRole.Unhealthy,
                colorHex = "#DC2626",
                tags = listOf("High Carb", "Sweets", "Late Meal", "Alcohol", "Poor Sleep", "Stress", "Long Sitting"),
                otherEnabled = true,
                titleKey = "group_unhealthy",
                tagKeys = mapOf(
                    "High Carb" to "tag_high_carb",
                    "Sweets" to "tag_sweets",
                    "Late Meal" to "tag_late_meal",
                    "Alcohol" to "tag_alcohol",
                    "Poor Sleep" to "tag_poor_sleep",
                    "Stress" to "tag_stress",
                    "Long Sitting" to "tag_long_sitting"
                )
            ),
            TagGroupDefinition(
                id = "meal_context",
                title = "Meal context",
                icon = "nutrition",
                colorRole = TagColorRole.Healthy,
                colorHex = "#15803D",
                tags = listOf("Fasting", "Before meal", "After meal", "Bedtime", "Random check"),
                otherEnabled = true,
                titleKey = "group_meal_context",
                tagKeys = mapOf(
                    "Fasting" to "tag_fasting",
                    "Before meal" to "tag_before_meal",
                    "After meal" to "tag_after_meal",
                    "Bedtime" to "tag_bedtime",
                    "Random check" to "tag_random_check"
                )
            ),
            TagGroupDefinition(
                id = "symptoms",
                title = "Symptoms",
                icon = "symptoms",
                colorRole = TagColorRole.Symptom,
                colorHex = "#7C3AED",
                tags = listOf("Thirst", "Shaking", "Fatigue", "Sweating", "Blurred vision"),
                otherEnabled = true,
                titleKey = "group_symptoms",
                tagKeys = mapOf(
                    "Thirst" to "tag_thirst",
                    "Shaking" to "tag_shaking",
                    "Fatigue" to "tag_fatigue",
                    "Sweating" to "tag_sweating",
                    "Blurred vision" to "tag_blurred_vision"
                )
            ),
            TagGroupDefinition(
                id = "other_medications",
                title = "Other Medications",
                icon = "medication",
                colorRole = TagColorRole.Medication,
                colorHex = "#2563EB",
                tags = listOf("Insulin", "Metformin", "Sulfonylurea", "GLP-1", "SGLT2", "Steroid", "Other Medication"),
                otherEnabled = true,
                titleKey = "group_other_medications",
                tagKeys = mapOf(
                    "Insulin" to "tag_insulin",
                    "Metformin" to "tag_metformin",
                    "Sulfonylurea" to "tag_sulfonylurea",
                    "GLP-1" to "tag_glp_1",
                    "SGLT2" to "tag_sglt2",
                    "Steroid" to "tag_steroid",
                    "Other Medication" to "tag_other_medication"
                )
            ),
            TagGroupDefinition(
                id = "custom",
                title = "Custom",
                icon = "custom",
                colorRole = TagColorRole.Neutral,
                colorHex = "#64748B",
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
                defaultName = "Insulin",
                defaultAmount = 0.0,
                defaultUnit = "units",
                statuses = listOf(
                    EventStatusDefinition("taken", "Taken", "taken", positive = true),
                    EventStatusDefinition("missed", "Missed", "missed")
                )
            )
        ),
        recordScreenBlocks = listOf(
            WidgetType.GraphWidget,
            WidgetType.DateTimeWidget,
            WidgetType.EventStatusWidget,
            WidgetType.SingleHorizontalWheelInputWidget,
            WidgetType.ComputedMetricInputWidget,
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
            AnalyticsRuleDefinition("averages", "Average glucose", "rule_averages"),
            AnalyticsRuleDefinition("frequencies", "Frequent contexts", "rule_frequencies")
        ),
        reminderTypes = listOf(
            ReminderTypeDefinition("measurement", "Measurement", "measurement"),
            ReminderTypeDefinition("medication", "Medication", "medication"),
            ReminderTypeDefinition("meal", "Meal check", "meal_check")
        ),
        dataActions = listOf(
            DataActionDefinition(DataActionType.ExportCsv, "Export CSV", "export_csv"),
            DataActionDefinition(DataActionType.ImportCsv, "Import CSV", "import_csv")
        ),
        graphDefinition = GraphDefinition(
            metrics = listOf("glucose"),
            series = listOf(
                GraphSeriesDefinition("glucose", "Glucose", GraphSeriesType.Line, "#0F766E", "metric_glucose"),
                GraphSeriesDefinition("medicationStatus", "Medication", GraphSeriesType.EventMarker, "#2563EB", "medication")
            ),
            yAxisStrategy = AxisScaleStrategy.PaddedDataRange,
            minPadding = 2,
            maxPadding = 2,
            visibleRecordCount = 7,
            horizontalPastScroll = true,
            showGrid = true,
            showAxisLabels = true,
            showLegend = true,
            showTapMarker = true,
            safeZones = listOf(GraphSafeZoneDefinition("glucose", 4..7, "#15803D", 0.12f)),
            pointOverlay = GraphPointOverlayDefinition("glucose", "●", 4..7, "#15803D", "#DC2626"),
            eventMarkers = listOf(
                GraphEventMarkerDefinition("medicationStatus", "medication", setOf("taken"), "I")
            ),
            emptyState = EmptyStateDefinition(
                title = "No glucose data",
                message = "Add glucose readings with meal context to see trends.",
                actionLabel = "Add reading",
                imageResourceName = "app_mark",
                imageContentDescription = "Glucose monitoring",
                instructions = listOf("Record glucose.", "Add meal or measurement context."),
                titleKey = "ai_no_data_title",
                messageKey = "ai_no_data_body",
                actionLabelKey = "save_record",
                imageContentDescriptionKey = "program_glucose_name",
                instructionKeys = listOf("metric_glucose", "group_meal_context")
            )
        ),
        saveActionDefinition = SaveActionDefinition("{glucose} {unit:glucose} · {weight} {unit:weight}", "Save glucose reading"),
        editAffordance = EditAffordanceDefinition(EditAffordanceIcon.MaterialEdit, "#64748B", 16),
        visualConfig = visualConfig,
        autoCollection = AutoCollectionConfig(
            recordSlots = listOf(
                RecordSlotDefinition("fasting", 5, 10),
                RecordSlotDefinition("day", 10, 18),
                RecordSlotDefinition("evening", 18, 23),
                RecordSlotDefinition("night", 23, 5)
            )
        ),
        integrations = PlatformIntegrationConfig(
            hardwareSensors = HealthContextCatalog.hardwareSensors,
            healthConnectMappings = listOf(
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.BLOOD_GLUCOSE,
                    metricMappings = mapOf("level" to "glucose"),
                    role = HealthConnectMappingRole.PRIMARY_METRIC
                ),
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.WEIGHT,
                    metricMappings = mapOf("weight" to "weight"),
                    role = HealthConnectMappingRole.PRIMARY_METRIC
                ),
                // Cross-context: blood pressure is read for sugar patients as a CONTEXT signal only.
                // The existing tag matrix transforms high readings into a "High BP" context tag.
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.BLOOD_PRESSURE,
                    metricMappings = emptyMap(),
                    role = HealthConnectMappingRole.CONTEXT_TAG,
                    rules = listOf(
                        SensorRule(
                            sensorId = "hc.blood_pressure.systolic",
                            transformType = TransformType.TAG,
                            threshold = 140.0,
                            targetTag = "High BP"
                        ),
                        SensorRule(
                            sensorId = "hc.blood_pressure.diastolic",
                            transformType = TransformType.TAG,
                            threshold = 90.0,
                            targetTag = "High BP"
                        )
                    )
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
                )
            ),
            backgroundReadEnabled = true
        ),
        localization = ProgramLocalizationConfig(
            appNameStringKey = "app_name",
            programNameStringKey = "program_glucose_name",
            translatableStringKeys = translatableKeys()
        ),
        aiPromptRoles = AiPromptRoleConfig(
            periodAnalysisRole = "You are a glucose monitoring data analyst.",
            chatRole = "You are a calm health assistant with glucose, weight, BMI, medication, and context-tag data."
        ),
        aiOnboardingQuestions = listOf(
            AiOnboardingQuestionConfig("main_goal", "What is your main glucose-control goal?", "ai_question_diabetes_main_goal", contextKey = "goal"),
            AiOnboardingQuestionConfig("age", "Enter your age.", "ai_question_age", AiOnboardingAnswerType.Number, contextKey = "age"),
            AiOnboardingQuestionConfig("diabetes_treatment_plan", "What treatment plan, insulin, or diabetes medication do you use?", "ai_question_diabetes_treatment", contextKey = "treatmentPlan"),
            AiOnboardingQuestionConfig("glucose_measurement_schedule", "When do you usually measure glucose: fasting, before meals, after meals, bedtime, or when symptomatic?", "ai_question_diabetes_measurement_schedule", contextKey = "measurementSchedule"),
            AiOnboardingQuestionConfig("hypo_hyper_symptoms", "Which low or high glucose symptoms should the assistant pay special attention to?", "ai_question_diabetes_symptoms", contextKey = "symptomsToWatch"),
            AiOnboardingQuestionConfig("food_activity_triggers", "Which food, alcohol, stress, sleep, or activity triggers do you want to track?", "ai_question_diabetes_triggers", contextKey = "trackedTriggers"),
            AiOnboardingQuestionConfig("limitations", "Are there safety limits or clinician instructions the assistant should respect?", "ai_question_diabetes_limitations", contextKey = "limitations")
        ),
        premiumConfig = PremiumConfig("glucose_monthly", "glucose_yearly")
    )

    val ui = ProgramUiDefinition(
        recordScreenBlocks = program.recordScreenBlocks,
        statisticsScreenBlocks = program.statisticsScreenBlocks,
        settingsSections = program.settingsSections,
        recordBlocks = listOf(
            RecordBlockDefinition(WidgetType.GraphWidget),
            RecordBlockDefinition(WidgetType.DateTimeWidget),
            RecordBlockDefinition(WidgetType.EventStatusWidget, "medication"),
            RecordBlockDefinition(
                type = WidgetType.SingleHorizontalWheelInputWidget,
                configId = "glucose",
                inputConfig = InputBlockConfig(
                    step = 0.1,
                    decimalPlaces = 1,
                    unitModes = listOf(
                        InputUnitMode(
                            id = "mmol_l",
                            label = "mmol/L",
                            unit = "mmol/L",
                            toCanonicalFactor = 1.0,
                            detectionMaxInclusive = 39.9,
                            step = 0.1,
                            decimalPlaces = 1
                        ),
                        InputUnitMode(
                            id = "mg_dl",
                            label = "mg/dL",
                            unit = "mg/dL",
                            toCanonicalFactor = 1.0 / 18.0182,
                            detectionMinInclusive = 40.0,
                            step = 1.0,
                            decimalPlaces = 0
                        )
                    )
                )
            ),
            // Complex body-composition block: weight wheel (tracked) auto-calculates BMI (tracked,
            // read-only) and shows height as an editable supporting field. Units are per-metric.
            RecordBlockDefinition(
                type = WidgetType.ComputedMetricInputWidget,
                configId = "bmi",
                inputConfigs = mapOf(
                    "weight" to InputBlockConfig(
                        step = 1.0,
                        decimalPlaces = 0,
                        unitModes = listOf(
                            InputUnitMode(id = "kg", label = "kg", unit = "kg", toCanonicalFactor = 1.0),
                            InputUnitMode(id = "lb", label = "lb", unit = "lb", toCanonicalFactor = 0.45359237),
                            InputUnitMode(id = "st", label = "st", unit = "st", toCanonicalFactor = 6.35029318)
                        )
                    ),
                    "height" to InputBlockConfig(
                        step = 1.0,
                        decimalPlaces = 0,
                        unitModes = listOf(
                            InputUnitMode(id = "cm", label = "cm", unit = "cm", toCanonicalFactor = 1.0),
                            InputUnitMode(id = "in", label = "in", unit = "in", toCanonicalFactor = 2.54)
                        )
                    )
                )
            ),
            RecordBlockDefinition(WidgetType.TagGroupsWidget),
            RecordBlockDefinition(WidgetType.NoteWidget),
            RecordBlockDefinition(WidgetType.SaveButtonWidget)
        )
    )

    val analyticsConfig = ProgramAnalyticsSchema(
        metrics = listOf(
            MetricConfig(
                key = "glucose",
                label = "Glucose",
                unit = "mmol/L",
                role = StatisticRole.Primary,
                meaningfulDifference = 1.0,
                dangerousHigh = 13.0,
                dangerousLow = 3.5,
                labelKey = "metric_glucose"
            ),
            MetricConfig(
                key = "weight",
                label = "Weight",
                unit = "kg",
                role = StatisticRole.Primary,
                meaningfulDifference = 1.0,
                labelKey = "metric_weight"
            ),
            MetricConfig(
                key = "bmi",
                label = "BMI",
                unit = "kg/m2",
                role = StatisticRole.Primary,
                meaningfulDifference = 1.0,
                dangerousHigh = 30.0,
                dangerousLow = 18.5,
                labelKey = "metric_bmi"
            )
        ),
        actions = listOf(ActionConfig("medication", listOf("taken", "missed"))),
        tagGroups = listOf(
            TagGroupConfig("healthy", "Healthy", "group_healthy", DimensionRole.Helpful),
            TagGroupConfig("unhealthy", "Unhealthy", "group_unhealthy", DimensionRole.Harmful),
            TagGroupConfig("meal_context", "Meal context", "group_meal_context", DimensionRole.Context),
            TagGroupConfig("symptoms", "Symptoms", "group_symptoms", DimensionRole.Symptom),
            TagGroupConfig("other_medications", "Other Medications", "group_other_medications", DimensionRole.Medication),
            TagGroupConfig("custom", "Custom", "group_custom", DimensionRole.Custom),
            TagGroupConfig("time_of_day", "Time of day", "group_time_of_day", DimensionRole.System)
        ),
        rules = listOf(
            AverageMetricRule("glucose"),
            AverageMetricRule("weight"),
            AverageMetricRule("bmi"),
            ExtremeValueRule("glucose", "highest"),
            ExtremeValueRule("glucose", "lowest"),
            FrequentTagRule("healthy"),
            FrequentTagRule("unhealthy"),
            FrequentTagRule("meal_context"),
            FrequentTagRule("symptoms"),
            FrequentTagRule("other_medications"),
            MetricByTagRule("glucose", "healthy"),
            MetricByTagRule("glucose", "unhealthy"),
            MetricByTagRule("glucose", "meal_context"),
            MetricByTagRule("glucose", "symptoms"),
            MetricByTagRule("glucose", "other_medications"),
            MetricByTagCombinationRule("glucose", listOf("unhealthy", "time_of_day"))
        ),
        dashboardMetricOrder = listOf("dashboard_avg_glucose", "dashboard_avg_weight", "dashboard_avg_bmi", "dashboard_records"),
        thresholds = AnalyticsThresholds(
            minRecordsForDashboard = 3,
            minRecordsForFindings = 5,
            minOccurrencesForTag = 3,
            minOccurrencesForCombination = 3,
            minGroupSizeForComparison = 3,
            minDifferencePercent = 10.0
        )
    )

    private fun translatableKeys() = setOf(
        "app_name",
        "program_glucose_name",
        "metric_glucose",
        "metric_weight",
        "metric_height",
        "metric_bmi",
        "group_healthy",
        "tag_walking",
        "tag_cardio",
        "tag_good_sleep",
        "tag_low_carb",
        "tag_vegetables",
        "tag_regular_meal",
        "tag_hydration",
        "group_unhealthy",
        "tag_high_carb",
        "tag_sweets",
        "tag_late_meal",
        "tag_alcohol",
        "tag_poor_sleep",
        "tag_stress",
        "tag_long_sitting",
        "group_meal_context",
        "tag_fasting",
        "tag_before_meal",
        "tag_after_meal",
        "tag_bedtime",
        "tag_random_check",
        "group_symptoms",
        "tag_thirst",
        "tag_shaking",
        "tag_fatigue",
        "tag_sweating",
        "tag_blurred_vision",
        "group_other_medications",
        "tag_insulin",
        "tag_metformin",
        "tag_sulfonylurea",
        "tag_glp_1",
        "tag_sglt2",
        "tag_steroid",
        "tag_other_medication",
        "group_custom",
        "group_time_of_day",
        "medication",
        "taken",
        "missed",
        "measurement",
        "meal_check",
        "rule_averages",
        "rule_frequencies",
        "dashboard_avg_glucose",
        "dashboard_avg_weight",
        "dashboard_avg_bmi",
        "dashboard_records",
        "export_csv",
        "import_csv",
        "ai_no_data_title",
        "ai_no_data_body",
        "ai_question_age",
        "ai_question_diabetes_main_goal",
        "ai_question_diabetes_treatment",
        "ai_question_diabetes_measurement_schedule",
        "ai_question_diabetes_symptoms",
        "ai_question_diabetes_triggers",
        "ai_question_diabetes_limitations",
        "save_record"
    )
}
