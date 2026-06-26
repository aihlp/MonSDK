package com.medmonitoring

import com.medmonitoring.core.config.ConfigRegistry
import com.medmonitoring.core.config.ConfigAudit
import com.medmonitoring.core.config.ConfigStrictMode
import com.medmonitoring.core.domain.model.*
import com.medmonitoring.program.bloodpressure.BloodPressureDefinitions
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ConfigCoverageTest {
    private val program = BloodPressureDefinitions.program
    private val ui = BloodPressureDefinitions.ui

    @Test
    fun configAuditHasNoErrors() {
        assertEquals(emptyList<String>(), ConfigAudit.validate(program, ui))
    }

    @Test
    fun everyUiWidgetExistsInRendererRegistry() {
        ui.recordScreenBlocks.forEach {
            assertTrue("Missing record renderer for $it", ConfigRegistry.recordWidgetRenderers.contains(it))
        }
        ui.statisticsScreenBlocks.forEach {
            assertTrue("Missing statistics renderer for $it", ConfigRegistry.statisticsWidgetRenderers.contains(it))
        }
    }

    @Test
    fun tagGroupsAreFullyDeclaredAndSupportOtherPolicy() {
        assertTrue(program.tagGroups.isNotEmpty())
        program.tagGroups.forEach { group ->
            assertTrue(group.id.isNotBlank())
            assertTrue(group.title.isNotBlank())
            assertNotNull(group.tags)
            assertTrue("Every MVP tag group must enable Other", group.otherEnabled)
        }
    }

    @Test
    fun analyticsSettingsDataActionsAndReminderTypesHaveRenderers() {
        program.analyticsRules.forEach {
            assertTrue("Missing analytics renderer for ${it.id}", ConfigRegistry.analyticsRuleRenderers.contains(it.id))
        }
        ui.settingsSections.forEach {
            assertTrue("Missing settings renderer for $it", ConfigRegistry.settingsSectionRenderers.contains(it))
        }
        program.dataActions.forEach {
            assertTrue("Missing data action renderer for ${it.type}", ConfigRegistry.dataActionRenderers.contains(it.type))
        }
        program.reminderTypes.forEach {
            assertTrue("Reminder type id is required", it.id.isNotBlank())
            assertTrue("Reminder type label is required", it.label.isNotBlank())
        }
    }

    @Test
    fun programDeclaresLocalizationSurfaceForUiAnalyticsAndAi() {
        val localization = program.localization

        assertEquals("en", localization.defaultLocaleTag)
        assertTrue(localization.supportedLocaleTags.containsAll(listOf("en", "ru", "es", "zh-Hans-CN")))
        assertTrue(localization.translatableStringKeys.contains(localization.appNameStringKey))
        assertTrue(localization.translatableStringKeys.contains(localization.programNameStringKey))
        assertTrue(localization.translatableStringKeys.contains("ai_prompt_language_instruction"))
        assertTrue(localization.translatableStringKeys.contains("dashboard_avg_sys"))
        assertTrue(localization.translatableStringKeys.contains("rule_adherence"))
        assertEquals("locale", localization.aiPromptLocaleField)
        assertEquals("locale", localization.analyticsLocaleField)
        assertEquals(emptyList<String>(), ConfigAudit.validate(program, ui))
    }

    @Test
    fun declaredLocalizationKeysExistInBaseAndRussianResources() {
        val requiredKeys = program.localization.translatableStringKeys
        val baseKeys = stringResourceNames(File("src/main/res/values/strings.xml"))
        val russianKeys = stringResourceNames(File("src/main/res/values-ru/strings.xml"))

        assertEquals(emptySet<String>(), requiredKeys - baseKeys)
        assertEquals(emptySet<String>(), requiredKeys - russianKeys)
    }

    @Test
    fun analyticsSchemaUsesLocalizationKeysForSdkReuse() {
        val registry = program.localization.translatableStringKeys
        BloodPressureDefinitions.analyticsConfig.metrics.forEach { metric ->
            assertTrue("Metric ${metric.key} must declare labelKey", !metric.labelKey.isNullOrBlank())
            assertTrue("Missing metric localization key ${metric.labelKey}", metric.labelKey in registry)
        }
        BloodPressureDefinitions.analyticsConfig.tagGroups.forEach { group ->
            assertTrue("Analytics group ${group.key} must declare labelKey", !group.labelKey.isNullOrBlank())
            assertTrue("Missing group localization key ${group.labelKey}", group.labelKey in registry)
        }
    }

    @Test
    fun missingLocalizationKeysFailConfigCoverage() {
        val invalidProgram = program.copy(
            localization = program.localization.copy(
                translatableStringKeys = program.localization.translatableStringKeys - program.localization.programNameStringKey
            )
        )

        assertTrue(
            ConfigAudit.validate(invalidProgram, ui)
                .any { it.contains("missing program name key") }
        )
    }

    @Test
    fun inputBlocksAndFactFieldsHaveRawEventMappings() {
        val inputBlocks = ui.recordScreenBlocks.filter {
            it in setOf(
                WidgetType.DateTimeWidget,
                WidgetType.EventStatusWidget,
                WidgetType.EventAmountWidget,
                WidgetType.PairedVerticalWheelInputWidget,
                WidgetType.SingleHorizontalWheelInputWidget,
                WidgetType.TagGroupsWidget,
                WidgetType.NoteWidget
            )
        }
        inputBlocks.forEach {
            assertTrue("Missing RawEvent mapper for $it", ConfigRegistry.inputBlockRawEventMappers.contains(it))
        }
        program.recordSchema.forEach {
            assertTrue("Missing Fact field mapping for $it", ConfigRegistry.factFieldMappings.contains(it))
        }
        assertEquals("Schema must cover all required record fields", RecordField.entries.toSet(), program.recordSchema.toSet())
    }

    @Test
    fun metricsComeFromProgramDefinition() {
        assertEquals(
            setOf(MetricType.BLOOD_PRESSURE, MetricType.PULSE, MetricType.MEDICATION_ADHERENCE),
            program.metrics.toSet()
        )
        assertTrue(program.metricComponents.map { it.id }.containsAll(listOf("systolic", "diastolic", "pulse", "dose")))
    }

    @Test
    fun strictModeFailsForUndeclaredElementsInDebug() {
        ConfigStrictMode.isDebug = true
        val uiWithoutDetails = ui.copy(statisticsScreenBlocks = listOf(WidgetType.AnalyticsSummaryWidget, WidgetType.HistoryWidget))
        assertThrows(IllegalStateException::class.java) {
            ConfigStrictMode.assertStatisticsWidgetAllowed(uiWithoutDetails, WidgetType.AnalyticsDetailsWidget)
        }
        val programWithoutSymptoms = program.copy(tagGroups = program.tagGroups.filterNot { it.id == "symptoms" })
        assertThrows(IllegalStateException::class.java) {
            ConfigStrictMode.assertTagGroupAllowed(programWithoutSymptoms, "symptoms")
        }
        val uiWithoutReminders = ui.copy(settingsSections = ui.settingsSections.filterNot { it == SettingsSectionType.RemindersSection })
        assertThrows(IllegalStateException::class.java) {
            ConfigStrictMode.assertSettingsSectionAllowed(uiWithoutReminders, SettingsSectionType.RemindersSection)
        }
    }

    @Test
    fun removingRemindersSectionRemovesSettingsRendererWithoutHardcode() {
        val uiWithoutReminders = ui.copy(settingsSections = ui.settingsSections.filterNot { it == SettingsSectionType.RemindersSection })

        assertFalse(uiWithoutReminders.settingsSections.contains(SettingsSectionType.RemindersSection))
        assertEquals(emptyList<String>(), ConfigAudit.validate(program, uiWithoutReminders))
        ConfigStrictMode.isDebug = true
        assertThrows(IllegalStateException::class.java) {
            ConfigStrictMode.assertSettingsSectionAllowed(uiWithoutReminders, SettingsSectionType.RemindersSection)
        }
    }

    @Test
    fun removingAnalyticsDetailsWidgetRemovesStatisticsRendererWithoutHardcode() {
        val uiWithoutDetails = ui.copy(statisticsScreenBlocks = ui.statisticsScreenBlocks.filterNot { it == WidgetType.AnalyticsDetailsWidget })

        assertFalse(uiWithoutDetails.statisticsScreenBlocks.contains(WidgetType.AnalyticsDetailsWidget))
        assertTrue(uiWithoutDetails.statisticsScreenBlocks.contains(WidgetType.AnalyticsSummaryWidget))
        assertEquals(emptyList<String>(), ConfigAudit.validate(program, uiWithoutDetails))
        ConfigStrictMode.isDebug = true
        assertThrows(IllegalStateException::class.java) {
            ConfigStrictMode.assertStatisticsWidgetAllowed(uiWithoutDetails, WidgetType.AnalyticsDetailsWidget)
        }
    }

    @Test
    fun removingSymptomsTagGroupRemovesRecordHistoryAndCustomTagCreationPath() {
        val programWithoutSymptoms = program.copy(tagGroups = program.tagGroups.filterNot { it.id == "symptoms" })

        assertFalse(programWithoutSymptoms.tagGroups.any { it.id == "symptoms" })
        assertEquals(emptyList<String>(), ConfigAudit.validate(programWithoutSymptoms, ui))
        ConfigStrictMode.isDebug = true
        assertThrows(IllegalStateException::class.java) {
            ConfigStrictMode.assertTagGroupAllowed(programWithoutSymptoms, "symptoms")
        }
    }

    @Test
    fun unknownWidgetFailsConfigCoverageAndIsHiddenInReleaseStrictMode() {
        val uiWithWrongStatisticsWidget = ui.copy(statisticsScreenBlocks = ui.statisticsScreenBlocks + WidgetType.SaveButtonWidget)

        assertTrue(ConfigAudit.validate(program, uiWithWrongStatisticsWidget).any { it.contains("Missing statistics renderer") })
        ConfigStrictMode.isDebug = true
        assertThrows(IllegalStateException::class.java) {
            ConfigStrictMode.assertStatisticsWidgetAllowed(ui, WidgetType.SaveButtonWidget)
        }
        ConfigStrictMode.isDebug = false
        val releaseLogs = mutableListOf<String>()
        ConfigStrictMode.localLogger = { releaseLogs += it }
        assertFalse(ConfigStrictMode.assertStatisticsWidgetAllowed(ui, WidgetType.SaveButtonWidget))
        assertTrue(releaseLogs.any { it.contains("not declared") })
        ConfigStrictMode.isDebug = true
        ConfigStrictMode.localLogger = {}
    }

    @Test
    fun invalidTagGroupFailsConfigCoverage() {
        val invalidProgram = program.copy(
            tagGroups = program.tagGroups + TagGroupDefinition(
                id = "fake",
                title = "",
                icon = "fake",
                colorRole = TagColorRole.Neutral,
                colorHex = "#6B7280",
                tags = emptyList(),
                otherEnabled = true
            )
        )

        val errors = ConfigAudit.validate(invalidProgram, ui)
        assertTrue(errors.any { it.contains("blank title") })
        assertTrue(errors.any { it.contains("no chips") })
    }

    @Test
    fun duplicateIdsAndInvalidMetricRangesFailConfigCoverage() {
        val metric = program.metricComponents.first()
        val invalidProgram = program.copy(
            metricComponents = program.metricComponents +
                metric.copy(normalRange = (metric.inputRange.first - 1)..metric.inputRange.last),
            tagGroups = program.tagGroups + program.tagGroups.first()
        )

        val errors = ConfigAudit.validate(invalidProgram, ui)
        assertTrue(errors.any { it.contains("Duplicate metric id") })
        assertTrue(errors.any { it.contains("Duplicate tag group id") })
        assertTrue(errors.any { it.contains("normal range is outside input range") })
    }

    @Test
    fun graphSeriesMustReferenceDeclaredGraphMetric() {
        val invalidProgram = program.copy(
            graphDefinition = program.graphDefinition.copy(
                series = program.graphDefinition.series +
                    GraphSeriesDefinition("missing", "Missing", GraphSeriesType.Line, "#000000")
            )
        )

        assertTrue(
            ConfigAudit.validate(invalidProgram, ui)
                .any { it.contains("Graph series references undeclared metric: missing") }
        )
    }

    @Test
    fun eventMarkerSeriesMustHaveProgramMarkerDefinition() {
        val invalidProgram = program.copy(
            graphDefinition = program.graphDefinition.copy(
                eventMarkers = emptyList()
            )
        )

        assertTrue(
            ConfigAudit.validate(invalidProgram, ui)
                .any { it.contains("Graph event marker series is missing marker definition: medicationStatus") }
        )
    }

    @Test
    fun eventMarkerDefinitionMustBeComplete() {
        val marker = program.graphDefinition.eventMarkers.first()
        val invalidProgram = program.copy(
            graphDefinition = program.graphDefinition.copy(
                eventMarkers = listOf(
                    marker.copy(
                        eventKey = "",
                        activeStatuses = emptySet(),
                        symbol = ""
                    )
                )
            )
        )

        val errors = ConfigAudit.validate(invalidProgram, ui)
        assertTrue(errors.any { it.contains("Graph event marker event key is blank") })
        assertTrue(errors.any { it.contains("Graph event marker statuses are incomplete") })
        assertTrue(errors.any { it.contains("Graph event marker symbol is blank") })
    }

    @Test
    fun visualConfigDefinesReusableThemeContract() {
        val visuals = program.visualConfig

        assertEquals("#1565C0", visuals.theme.seedColorHex)
        assertTrue(visuals.severityPalette.keys.containsAll(SeverityLevel.entries))
        assertTrue(visuals.tagPalettes.keys.containsAll(TagColorRole.entries))
        assertEquals(0, visuals.spacing.screenHorizontalDp % visuals.spacing.gridUnitDp)
        assertEquals(emptyList<String>(), ConfigAudit.validate(program, ui))
    }

    @Test
    fun invalidVisualConfigFailsAudit() {
        val invalid = program.copy(
            visualConfig = program.visualConfig.copy(
                theme = program.visualConfig.theme.copy(seedColorHex = "blue"),
                severityPalette = program.visualConfig.severityPalette - SeverityLevel.Critical
            )
        )

        val errors = ConfigAudit.validate(invalid, ui)
        assertTrue(errors.any { it.contains("invalid color") })
        assertTrue(errors.any { it.contains("all severity levels") })
    }

    @Test
    fun analyticsRuleWithoutRendererFailsConfigCoverage() {
        val invalidProgram = program.copy(
            analyticsRules = program.analyticsRules + AnalyticsRuleDefinition("fake_rule", "Fake Rule")
        )

        assertTrue(ConfigAudit.validate(invalidProgram, ui).any { it.contains("Missing analytics renderer: fake_rule") })
    }

    @Test
    fun userUiDoesNotContainForbiddenPlatformLabels() {
        val uiSource = File("src/main/java/com/medmonitoring/app/MainActivity.kt").readText()
        val forbidden = listOf(
            "Program Selector",
            "Disease Selector",
            "Diabetes",
            "Hypertension",
            "Pregnancy",
            "Multi-program",
            "SourceType",
            "MANUAL",
            "BLE",
            "HEALTH_CONNECT",
            "CSV_IMPORT",
            "Raw event",
            "RawEvent",
            "Fact id",
            "FactId",
            "Confidence"
        )
        forbidden.forEach {
            assertFalse("Forbidden UI label found: $it", uiSource.contains(it))
        }
    }

    private fun stringResourceNames(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
            .toSet()
    }
}
