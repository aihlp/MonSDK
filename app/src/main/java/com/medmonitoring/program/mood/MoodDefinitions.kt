package com.medmonitoring.program.mood

import com.medmonitoring.core.analytics.AverageMetricRule
import com.medmonitoring.core.analytics.ExtremeValueRule
import com.medmonitoring.core.analytics.FrequentTagCombinationRule
import com.medmonitoring.core.analytics.FrequentTagRule
import com.medmonitoring.core.analytics.MetricByTagRule
import com.medmonitoring.core.config.HealthContextCatalog
import com.medmonitoring.core.domain.model.*
import com.medmonitoring.core.premium.PremiumConfig

object MoodDefinitions {
    private val moodPalette = MetricZonePalette(
        normalAccentHex = "#0F766E",
        normalSurfaceHex = "#ECFDF5",
        cautionAccentHex = "#D97706",
        cautionSurfaceHex = "#FFFBEB",
        dangerAccentHex = "#BE123C",
        dangerSurfaceHex = "#FFF1F2"
    )

    private val visualConfig = ProgramVisualConfig(
        theme = ThemeConfig(
            seedColorHex = "#0F766E",
            lightColors = StaticColorSchemeConfig(
                primary = "#0F766E",
                onPrimary = "#FFFFFF",
                primaryContainer = "#CCFBF1",
                secondary = "#7C3AED",
                onSecondary = "#FFFFFF",
                surface = "#FFFFFF",
                surfaceVariant = "#F8FAFC",
                onSurface = "#111827",
                onSurfaceVariant = "#64748B",
                outline = "#CBD5E1",
                outlineVariant = "#E2E8F0",
                error = "#BE123C",
                onError = "#FFFFFF"
            ),
            darkColors = StaticColorSchemeConfig(
                primary = "#5EEAD4",
                onPrimary = "#042F2E",
                primaryContainer = "#134E4A",
                secondary = "#C4B5FD",
                onSecondary = "#2E1065",
                surface = "#0F172A",
                surfaceVariant = "#1E293B",
                onSurface = "#E5E7EB",
                onSurfaceVariant = "#CBD5E1",
                outline = "#64748B",
                outlineVariant = "#334155",
                error = "#FDA4AF",
                onError = "#4C0519"
            ),
            useDynamicColor = false
        ),
        severityPalette = mapOf(
            SeverityLevel.Normal to SemanticColorConfig("#0F766E", "#ECFDF5", "#99F6E4"),
            SeverityLevel.Elevated to SemanticColorConfig("#D97706", "#FFFBEB", "#FDE68A"),
            SeverityLevel.High to SemanticColorConfig("#EA580C", "#FFF7ED", "#FED7AA"),
            SeverityLevel.Critical to SemanticColorConfig("#BE123C", "#FFF1F2", "#FECDD3"),
            SeverityLevel.Neutral to SemanticColorConfig("#64748B", "#F8FAFC", "#CBD5E1")
        ),
        tagPalettes = mapOf(
            TagColorRole.Healthy to SemanticColorConfig("#2F8F46", "#F0FDF4", "#BBF7D0"),
            TagColorRole.Unhealthy to SemanticColorConfig("#B42318", "#FFF1F2", "#FECDD3"),
            TagColorRole.Symptom to SemanticColorConfig("#6D28D9", "#F5F3FF", "#DDD6FE"),
            TagColorRole.Medication to SemanticColorConfig("#2563EB", "#EFF6FF", "#BFDBFE"),
            TagColorRole.Neutral to SemanticColorConfig("#64748B", "#F8FAFC", "#CBD5E1")
        ),
        components = ComponentStyleConfig(
            saveAction = SaveActionStyleConfig(gradientHex = listOf("#0F766E", "#7C3AED"))
        )
    )

    val program = UniversalProgramDefinition(
        programId = "mood-energy-monitor",
        displayName = "Mood Monitoring",
        displayNameKey = "program_mood_name",
        medicationName = "",
        defaultDose = 0.0,
        metrics = emptyList(),
        metricComponents = listOf(
            MetricComponent("mood", "Mood", "🌀/10", 1..10, 6..10, 4..5, MetricInputStyle.ScaleSlider, moodPalette, AggregationStrategy.Average, 7, labelKey = "metric_mood"),
            MetricComponent("energy", "Energy", "⚡/10", 1..10, 6..10, 4..5, MetricInputStyle.ScaleSlider, moodPalette, AggregationStrategy.Average, 6, labelKey = "metric_energy")
        ),
        recordSchema = RecordField.entries,
        tagGroups = tagGroups(),
        recordScreenBlocks = listOf(
            WidgetType.GraphWidget,
            WidgetType.DateTimeWidget,
            WidgetType.ScaleSliderInputWidget,
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
            SettingsSectionType.RemindersSection,
            SettingsSectionType.DataExportImportSection,
            SettingsSectionType.HealthConnectSection,
            SettingsSectionType.SensorLaboratorySection,
            SettingsSectionType.AboutSection
        ),
        analyticsRules = listOf(
            AnalyticsRuleDefinition("averages", "Average mood and energy", "rule_mood_averages"),
            AnalyticsRuleDefinition("frequencies", "Frequent emotions and factors", "rule_mood_frequencies")
        ),
        reminderTypes = listOf(
            ReminderTypeDefinition("check_in", "Mood check-in", "mood_check_in"),
            ReminderTypeDefinition("diary", "Diary", "diary")
        ),
        dataActions = listOf(
            DataActionDefinition(DataActionType.ExportCsv, "Export CSV", "export_csv"),
            DataActionDefinition(DataActionType.ImportCsv, "Import CSV", "import_csv")
        ),
        graphDefinition = GraphDefinition(
            metrics = listOf("mood", "energy"),
            series = listOf(
                GraphSeriesDefinition("mood", "Mood", GraphSeriesType.Line, "#0F766E", "metric_mood"),
                GraphSeriesDefinition("energy", "Energy", GraphSeriesType.PointOrLine, "#7C3AED", "metric_energy")
            ),
            yAxisStrategy = AxisScaleStrategy.FixedRange,
            minPadding = 1,
            maxPadding = 1,
            visibleRecordCount = 7,
            horizontalPastScroll = true,
            showGrid = true,
            showAxisLabels = true,
            showLegend = true,
            showTapMarker = true,
            safeZones = listOf(GraphSafeZoneDefinition("mood", 6..10, "#0F766E", 0.10f)),
            pointOverlay = GraphPointOverlayDefinition("energy", "●", 6..10, "#7C3AED", "#BE123C"),
            emptyState = EmptyStateDefinition(
                title = "Track mood and energy",
                message = "Add mood, energy, emotions, symptoms, and context to reveal patterns.",
                actionLabel = "Add check-in",
                imageResourceName = "app_mark",
                imageContentDescription = "Mood monitoring",
                instructions = listOf("Move each scale from 1 to 10.", "Add helpful, harmful, emotion, symptom, or medication tags."),
                titleKey = "graph_empty_mood_title",
                messageKey = "graph_empty_mood_message",
                actionLabelKey = "graph_empty_mood_action",
                imageContentDescriptionKey = "program_mood_name",
                instructionKeys = listOf("graph_empty_mood_instruction_1", "graph_empty_mood_instruction_2")
            )
        ),
        saveActionDefinition = SaveActionDefinition("{mood} 🌀/10 · {energy} ⚡/10", "{action}"),
        editAffordance = EditAffordanceDefinition(EditAffordanceIcon.MaterialEdit, "#64748B", 16),
        visualConfig = visualConfig,
        autoCollection = AutoCollectionConfig(
            recordSlots = listOf(
                RecordSlotDefinition("morning", 5, 11),
                RecordSlotDefinition("day", 11, 17),
                RecordSlotDefinition("evening", 17, 23),
                RecordSlotDefinition("night", 23, 5)
            )
        ),
        integrations = PlatformIntegrationConfig(
            hardwareSensors = HealthContextCatalog.hardwareSensors,
            healthConnectMappings = listOf(
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.STEPS,
                    metricMappings = emptyMap(),
                    role = HealthConnectMappingRole.CONTEXT_TAG,
                    rules = listOf(SensorRule("hc.steps.count", TransformType.TAG, threshold = 3000.0, targetTag = "Active Walking"))
                ),
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.EXERCISE,
                    metricMappings = emptyMap(),
                    role = HealthConnectMappingRole.CONTEXT_TAG,
                    rules = listOf(SensorRule("hc.exercise.durationMinutes", TransformType.TAG, threshold = 10.0, targetTag = "Workout"))
                ),
                HealthConnectMapping(
                    recordType = HealthConnectRecordType.SLEEP,
                    metricMappings = emptyMap(),
                    role = HealthConnectMappingRole.CONTEXT_TAG,
                    rules = listOf(SensorRule("hc.sleep.durationHours", TransformType.TAG, threshold = 7.0, targetTag = "Good Sleep"))
                )
            ),
            backgroundReadEnabled = true
        ),
        localization = ProgramLocalizationConfig(
            appNameStringKey = "app_name",
            programNameStringKey = "program_mood_name",
            translatableStringKeys = translatableKeys()
        ),
        aiPromptRoles = AiPromptRoleConfig(
            periodAnalysisRole = "You are a mood and energy monitoring data analyst.",
            chatRole = "You are a calm health assistant with mood, energy, emotion, symptom, medication, and context-tag data."
        ),
        aiOnboardingQuestions = listOf(
            AiOnboardingQuestionConfig("main_goal", "What is your main mood-tracking goal?", "ai_question_mood_main_goal", contextKey = "goal"),
            AiOnboardingQuestionConfig("age", "Enter your age.", "ai_question_age", AiOnboardingAnswerType.Number, contextKey = "age"),
            AiOnboardingQuestionConfig("gender", "Enter your gender.", "ai_question_gender", contextKey = "gender"),
            AiOnboardingQuestionConfig("cycle_tracking", "Should cycle context be tracked when available?", "ai_question_cycle_tracking", AiOnboardingAnswerType.YesNo, contextKey = "cycleTracking"),
            AiOnboardingQuestionConfig("treatment", "Do you use medication, therapy, supplements, or another routine that affects mood?", "ai_question_mood_treatment", contextKey = "treatmentPlan"),
            AiOnboardingQuestionConfig("triggers", "Which stress, sleep, food, activity, symptoms, or emotion triggers do you want to track?", "ai_question_mood_triggers", contextKey = "trackedTriggers")
        ),
        premiumConfig = PremiumConfig("mood_monthly", "mood_yearly")
    )

    val ui = ProgramUiDefinition(
        recordScreenBlocks = program.recordScreenBlocks,
        statisticsScreenBlocks = program.statisticsScreenBlocks,
        settingsSections = program.settingsSections,
        recordBlocks = listOf(
            RecordBlockDefinition(WidgetType.GraphWidget),
            RecordBlockDefinition(WidgetType.DateTimeWidget),
            RecordBlockDefinition(WidgetType.ScaleSliderInputWidget, "mood"),
            RecordBlockDefinition(WidgetType.ScaleSliderInputWidget, "energy"),
            RecordBlockDefinition(WidgetType.TagGroupsWidget),
            RecordBlockDefinition(WidgetType.NoteWidget),
            RecordBlockDefinition(WidgetType.SaveButtonWidget)
        )
    )

    val analyticsConfig = ProgramAnalyticsSchema(
        metrics = listOf(
            MetricConfig("mood", "Mood", "🌀/10", StatisticRole.Primary, meaningfulDifference = 1.0, dangerousLow = 3.0, labelKey = "metric_mood"),
            MetricConfig("energy", "Energy", "⚡/10", StatisticRole.Primary, meaningfulDifference = 1.0, dangerousLow = 3.0, labelKey = "metric_energy")
        ),
        actions = emptyList(),
        tagGroups = listOf(
            TagGroupConfig("positive_emotions", "Positive emotions", "group_positive_emotions", DimensionRole.Context),
            TagGroupConfig("negative_emotions", "Negative emotions", "group_negative_emotions", DimensionRole.Context),
            TagGroupConfig("symptoms", "Symptoms", "group_symptoms", DimensionRole.Symptom),
            TagGroupConfig("healthy", "Helpful factors", "group_healthy", DimensionRole.Helpful),
            TagGroupConfig("unhealthy", "Risk factors", "group_unhealthy", DimensionRole.Harmful),
            TagGroupConfig("other_medications", "Other Medications", "group_other_medications", DimensionRole.Medication),
            TagGroupConfig("custom", "Custom", "group_custom", DimensionRole.Custom),
            TagGroupConfig("time_of_day", "Time of day", "group_time_of_day", DimensionRole.System)
        ),
        rules = listOf(
            AverageMetricRule("mood"),
            AverageMetricRule("energy"),
            ExtremeValueRule("mood", "lowest"),
            ExtremeValueRule("mood", "highest"),
            ExtremeValueRule("energy", "lowest"),
            FrequentTagRule("positive_emotions"),
            FrequentTagRule("negative_emotions"),
            FrequentTagRule("symptoms"),
            FrequentTagRule("healthy"),
            FrequentTagRule("unhealthy"),
            FrequentTagRule("other_medications"),
            FrequentTagCombinationRule(listOf("negative_emotions", "symptoms")),
            MetricByTagRule("mood", "positive_emotions"),
            MetricByTagRule("mood", "negative_emotions"),
            MetricByTagRule("mood", "symptoms"),
            MetricByTagRule("mood", "healthy"),
            MetricByTagRule("mood", "unhealthy"),
            MetricByTagRule("energy", "healthy"),
            MetricByTagRule("energy", "unhealthy"),
            MetricByTagRule("energy", "symptoms")
        ),
        dashboardMetricOrder = listOf("dashboard_avg_mood", "dashboard_avg_energy", "dashboard_records")
    )

    private fun tagGroups() = listOf(
        TagGroupDefinition("positive_emotions", "Positive emotions", "activity", TagColorRole.Healthy, "#2F8F46", listOf("Calm", "Joy", "Hope", "Gratitude", "Interest", "Confidence"), true, "group_positive_emotions", mapOf("Calm" to "tag_calm", "Joy" to "tag_joy", "Hope" to "tag_hope", "Gratitude" to "tag_gratitude", "Interest" to "tag_interest", "Confidence" to "tag_confidence")),
        TagGroupDefinition("negative_emotions", "Negative emotions", "warning", TagColorRole.Unhealthy, "#C2410C", listOf("Anxiety", "Sadness", "Irritability", "Anger", "Guilt", "Loneliness"), true, "group_negative_emotions", mapOf("Anxiety" to "tag_anxiety", "Sadness" to "tag_sadness", "Irritability" to "tag_irritability", "Anger" to "tag_anger", "Guilt" to "tag_guilt", "Loneliness" to "tag_loneliness")),
        TagGroupDefinition("symptoms", "Symptoms", "symptoms", TagColorRole.Symptom, "#6D28D9", listOf("Fatigue", "Headache", "Nausea", "Insomnia", "Tension", "Panic symptoms"), true, "group_symptoms", mapOf("Fatigue" to "tag_fatigue", "Headache" to "tag_headache", "Nausea" to "tag_nausea", "Insomnia" to "tag_insomnia", "Tension" to "tag_tension", "Panic symptoms" to "tag_panic_symptoms")),
        TagGroupDefinition("healthy", "Helpful factors", "activity", TagColorRole.Healthy, "#0F766E", listOf("Therapy", "Journaling", "Meditation", "Rest", "Creative activity", "Social Support", "Sunlight"), true, "group_healthy", mapOf("Therapy" to "tag_therapy", "Journaling" to "tag_journaling", "Meditation" to "tag_meditation", "Rest" to "tag_rest", "Creative activity" to "tag_creative_activity", "Social Support" to "tag_social_support", "Sunlight" to "tag_sunlight")),
        TagGroupDefinition("unhealthy", "Risk factors", "warning", TagColorRole.Unhealthy, "#B42318", listOf("Conflict", "Overwork", "Poor Sleep", "Alcohol", "Caffeine", "Isolation", "Doomscrolling"), true, "group_unhealthy", mapOf("Conflict" to "tag_conflict", "Overwork" to "tag_overwork", "Poor Sleep" to "tag_poor_sleep", "Alcohol" to "tag_alcohol", "Caffeine" to "tag_caffeine", "Isolation" to "tag_isolation", "Doomscrolling" to "tag_doomscrolling")),
        TagGroupDefinition("other_medications", "Other Medications", "medication", TagColorRole.Medication, "#1D4ED8", listOf("Antidepressant", "Anxiolytic", "Sleep aid", "Pain reliever", "Hormonal medication", "Supplement", "Other Medication"), true, "group_other_medications", mapOf("Antidepressant" to "tag_antidepressant", "Anxiolytic" to "tag_anxiolytic", "Sleep aid" to "tag_sleep_aid", "Pain reliever" to "tag_pain_reliever", "Hormonal medication" to "tag_hormonal_medication", "Supplement" to "tag_supplement", "Other Medication" to "tag_other_medication")),
        TagGroupDefinition("custom", "Custom", "custom", TagColorRole.Neutral, "#64748B", emptyList(), true, "group_custom")
    )

    private fun translatableKeys() = setOf(
        "app_name", "program_mood_name", "metric_mood", "metric_energy",
        "graph_empty_mood_title", "graph_empty_mood_message", "graph_empty_mood_action",
        "graph_empty_mood_instruction_1", "graph_empty_mood_instruction_2",
        "group_healthy", "group_unhealthy", "group_positive_emotions", "group_negative_emotions",
        "group_symptoms", "group_other_medications", "group_custom", "group_time_of_day",
        "tag_therapy", "tag_journaling", "tag_meditation", "tag_rest", "tag_creative_activity", "tag_sunlight", "tag_social_support",
        "tag_alcohol", "tag_poor_sleep", "tag_overwork", "tag_conflict", "tag_caffeine", "tag_isolation", "tag_doomscrolling",
        "tag_calm", "tag_joy", "tag_hope", "tag_gratitude", "tag_interest", "tag_confidence",
        "tag_anxiety", "tag_sadness", "tag_irritability", "tag_anger", "tag_guilt", "tag_loneliness",
        "tag_fatigue", "tag_headache", "tag_nausea", "tag_insomnia", "tag_tension", "tag_panic_symptoms",
        "tag_antidepressant", "tag_anxiolytic", "tag_sleep_aid", "tag_pain_reliever", "tag_hormonal_medication", "tag_supplement", "tag_other_medication",
        "rule_mood_averages", "rule_mood_frequencies", "dashboard_avg_mood", "dashboard_avg_energy", "dashboard_records",
        "mood_check_in", "diary", "export_csv", "import_csv",
        "ai_question_mood_main_goal", "ai_question_cycle_tracking", "ai_question_mood_treatment", "ai_question_mood_triggers",
        "ai_question_age", "ai_question_gender"
    )
}
