package com.medmonitoring.core.config

import com.medmonitoring.core.domain.model.ProgramUiDefinition
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.domain.model.GraphSeriesType
import com.medmonitoring.core.domain.model.SeverityLevel
import com.medmonitoring.core.domain.model.RecordField
import com.medmonitoring.core.domain.model.WidgetType

object ConfigAudit {
    fun validate(program: UniversalProgramDefinition, ui: ProgramUiDefinition): List<String> {
        val errors = mutableListOf<String>()
        if (program.programId.isBlank()) errors += "Program id is blank"
        if (program.displayName.isBlank()) errors += "Program display name is blank"
        errors += validateLocalizationConfig(program)
        errors += validateVisualConfig(program)

        errors += duplicateErrors("metric", program.metricComponents.map { it.id })
        errors += duplicateErrors("tag group", program.tagGroups.map { it.id })
        errors += duplicateErrors("event input", program.eventInputs.map { it.key })
        errors += duplicateErrors("analytics rule", program.analyticsRules.map { it.id })
        errors += duplicateErrors("reminder type", program.reminderTypes.map { it.id })

        program.metricComponents.forEach { metric ->
            if (metric.id.isBlank()) errors += "Metric has blank id"
            if (metric.label.isBlank()) errors += "Metric has blank label: ${metric.id}"
            if (metric.unit.isBlank()) errors += "Metric has blank unit: ${metric.id}"
            if (metric.inputRange.isEmpty()) errors += "Metric has empty input range: ${metric.id}"
            metric.normalRange?.let {
                if (it.isEmpty() || it.first < metric.inputRange.first || it.last > metric.inputRange.last) {
                    errors += "Metric normal range is outside input range: ${metric.id}"
                }
            }
            metric.cautionRange?.let {
                if (it.isEmpty() || it.first < metric.inputRange.first || it.last > metric.inputRange.last) {
                    errors += "Metric caution range is outside input range: ${metric.id}"
                }
            }
        }
        ui.recordScreenBlocks.forEach {
            if (!ConfigRegistry.recordWidgetRenderers.contains(it)) errors += "Missing record renderer: $it"
        }
        ui.statisticsScreenBlocks.forEach {
            if (!ConfigRegistry.statisticsWidgetRenderers.contains(it)) errors += "Missing statistics renderer: $it"
        }
        ui.settingsSections.forEach {
            if (!ConfigRegistry.settingsSectionRenderers.contains(it)) errors += "Missing settings section renderer: $it"
        }
        program.tagGroups.forEach {
            if (it.id.isBlank()) errors += "Tag group has blank id"
            if (it.title.isBlank()) errors += "Tag group has blank title: ${it.id}"
            if (!it.colorHex.matches(Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$"))) errors += "Tag group has invalid color: ${it.id}"
            if (it.tags.isEmpty() && it.id != "custom") errors += "Tag group has no chips: ${it.id}"
        }
        ui.recordBlocks.forEach {
            if (!ui.recordScreenBlocks.contains(it.type)) errors += "Record block ${it.type} is not declared in recordScreenBlocks"
        }
        if (program.graphDefinition.metrics.any { it.isBlank() }) errors += "Graph metric id is blank"
        if (program.graphDefinition.series.any { it.metricId.isBlank() || it.label.isBlank() }) errors += "Graph series is incomplete"
        val graphMetricIds = program.graphDefinition.metrics.toSet()
        val eventMarkerIds = program.graphDefinition.eventMarkers.map { it.seriesId }.toSet()
        program.graphDefinition.series.forEach {
            when (it.type) {
                GraphSeriesType.EventMarker -> {
                    if (it.metricId !in eventMarkerIds) {
                        errors += "Graph event marker series is missing marker definition: ${it.metricId}"
                    }
                }
                else -> {
                    if (it.metricId !in graphMetricIds) {
                        errors += "Graph series references undeclared metric: ${it.metricId}"
                    }
                }
            }
        }
        program.graphDefinition.eventMarkers.forEach {
            if (it.seriesId.isBlank()) errors += "Graph event marker series id is blank"
            if (it.eventKey.isBlank()) errors += "Graph event marker event key is blank: ${it.seriesId}"
            if (it.activeStatuses.isEmpty() || it.activeStatuses.any(String::isBlank)) {
                errors += "Graph event marker statuses are incomplete: ${it.seriesId}"
            }
            if (it.symbol.isBlank()) errors += "Graph event marker symbol is blank: ${it.seriesId}"
            if (it.seriesId !in program.graphDefinition.series.map { series -> series.metricId }) {
                errors += "Graph event marker references missing series: ${it.seriesId}"
            }
            if (program.eventInputs.none { input -> input.key == it.eventKey }) {
                errors += "Graph event marker references undeclared event input: ${it.eventKey}"
            }
        }
        program.eventInputs.forEach {
            if (it.key.isBlank()) errors += "Event input key is blank"
            if (it.label.isBlank()) errors += "Event input label is blank: ${it.key}"
            if (it.statuses.isEmpty()) errors += "Event input has no statuses: ${it.key}"
            if (it.statuses.any { status -> status.status.isBlank() || status.label.isBlank() }) {
                errors += "Event input has incomplete statuses: ${it.key}"
            }
        }
        if (program.saveActionDefinition.previewPattern.isBlank()) errors += "Save preview pattern is blank"
        if (program.saveActionDefinition.fallbackText.isBlank()) errors += "Save fallback text is blank"
        program.analyticsRules.forEach {
            if (!ConfigRegistry.analyticsRuleRenderers.contains(it.id)) errors += "Missing analytics renderer: ${it.id}"
        }
        program.dataActions.forEach {
            if (!ConfigRegistry.dataActionRenderers.contains(it.type)) errors += "Missing data action renderer: ${it.type}"
        }
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
            if (!ConfigRegistry.inputBlockRawEventMappers.contains(it)) errors += "Missing RawEvent mapper: $it"
        }
        if (program.recordSchema.toSet() != RecordField.entries.toSet()) {
            errors += "Record schema does not cover required fields"
        }
        program.recordSchema.forEach {
            if (!ConfigRegistry.factFieldMappings.contains(it)) errors += "Missing fact field mapping: $it"
        }
        return errors
    }

    private fun duplicateErrors(kind: String, ids: List<String>): List<String> =
        ids.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
            .map { "Duplicate $kind id: $it" }

    private fun validateVisualConfig(program: UniversalProgramDefinition): List<String> {
        val errors = mutableListOf<String>()
        val visuals = program.visualConfig
        val requiredSeverity = setOf(
            SeverityLevel.Normal,
            SeverityLevel.Elevated,
            SeverityLevel.High,
            SeverityLevel.Critical,
            SeverityLevel.Neutral
        )
        if (!visuals.severityPalette.keys.containsAll(requiredSeverity)) {
            errors += "Visual config does not define all severity levels"
        }
        val colors = buildList {
            add(visuals.theme.seedColorHex)
            with(visuals.theme.lightColors) {
                addAll(
                    listOf(
                        primary, onPrimary, primaryContainer, secondary, onSecondary,
                        surface, surfaceVariant, onSurface, onSurfaceVariant,
                        outline, outlineVariant, error, onError
                    )
                )
            }
            visuals.severityPalette.values.forEach {
                addAll(listOf(it.contentHex, it.containerHex, it.outlineHex))
            }
            visuals.tagPalettes.values.forEach {
                addAll(listOf(it.contentHex, it.containerHex, it.outlineHex))
            }
            addAll(visuals.components.appBar.iconGradientHex)
            addAll(visuals.components.saveAction.gradientHex)
        }
        colors.filterNot(::isColorHex).forEach { errors += "Visual config has invalid color: $it" }
        val spacing = visuals.spacing
        listOf(
            spacing.screenHorizontalDp,
            spacing.screenTopDp,
            spacing.screenBottomDp,
            spacing.sectionGapDp,
            spacing.cardPaddingDp,
            spacing.cardGapDp
        ).filter { it % spacing.gridUnitDp != 0 }
            .forEach { errors += "Visual spacing is outside ${spacing.gridUnitDp}dp grid: $it" }
        if (visuals.components.findings.widthFraction !in 0.5f..1f) {
            errors += "Findings width fraction must be between 0.5 and 1.0"
        }
        return errors
    }

    private fun isColorHex(value: String): Boolean =
        value.matches(Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$"))

    private fun validateLocalizationConfig(program: UniversalProgramDefinition): List<String> {
        val localization = program.localization
        val errors = mutableListOf<String>()
        if (localization.defaultLocaleTag.isBlank()) errors += "Localization default locale is blank"
        if (localization.supportedLocaleTags.isEmpty()) errors += "Localization supported locales are empty"
        if (localization.defaultLocaleTag !in localization.supportedLocaleTags) {
            errors += "Localization default locale is not supported: ${localization.defaultLocaleTag}"
        }
        if (localization.appNameStringKey.isBlank()) errors += "Localization app name key is blank"
        if (localization.programNameStringKey.isBlank()) errors += "Localization program name key is blank"
        if (localization.translatableStringKeys.isEmpty()) errors += "Localization string key registry is empty"
        if (localization.appNameStringKey !in localization.translatableStringKeys) {
            errors += "Localization string registry missing app name key: ${localization.appNameStringKey}"
        }
        if (localization.programNameStringKey !in localization.translatableStringKeys) {
            errors += "Localization string registry missing program name key: ${localization.programNameStringKey}"
        }
        program.displayNameKey?.let { key ->
            if (key !in localization.translatableStringKeys) errors += "Localization string registry missing display name key: $key"
        }
        program.metricComponents.forEach { metric ->
            metric.labelKey?.let { key ->
                if (key !in localization.translatableStringKeys) errors += "Localization string registry missing metric label key: ${metric.id} -> $key"
            }
            metric.unitKey?.let { key ->
                if (key !in localization.translatableStringKeys) errors += "Localization string registry missing metric unit key: ${metric.id} -> $key"
            }
        }
        program.tagGroups.forEach { group ->
            group.titleKey?.let { key ->
                if (key !in localization.translatableStringKeys) errors += "Localization string registry missing tag group title key: ${group.id} -> $key"
            }
            group.tagKeys.forEach { (tag, key) ->
                if (key !in localization.translatableStringKeys) errors += "Localization string registry missing tag key: ${group.id}/$tag -> $key"
            }
            group.tags.filterNot { it in group.tagKeys || group.id == "custom" }.forEach { tag ->
                errors += "Tag is missing localization key: ${group.id}/$tag"
            }
        }
        program.graphDefinition.series.forEach { series ->
            series.labelKey?.let { key ->
                if (key !in localization.translatableStringKeys) errors += "Localization string registry missing graph series key: ${series.metricId} -> $key"
            }
        }
        program.eventInputs.forEach { eventInput ->
            eventInput.labelKey?.let { key ->
                if (key !in localization.translatableStringKeys) errors += "Localization string registry missing event input key: ${eventInput.key} -> $key"
            }
            eventInput.statuses.forEach { status ->
                status.labelKey?.let { key ->
                    if (key !in localization.translatableStringKeys) errors += "Localization string registry missing event status key: ${eventInput.key}/${status.status} -> $key"
                }
            }
        }
        with(program.graphDefinition.emptyState) {
            listOfNotNull(titleKey, messageKey, actionLabelKey, imageContentDescriptionKey)
                .forEach { key ->
                    if (key !in localization.translatableStringKeys) errors += "Localization string registry missing graph empty state key: $key"
                }
            instructionKeys.forEach { key ->
                if (key !in localization.translatableStringKeys) errors += "Localization string registry missing graph instruction key: $key"
            }
        }
        program.reminderTypes.forEach { reminder ->
            reminder.labelKey?.let { key ->
                if (key !in localization.translatableStringKeys) errors += "Localization string registry missing reminder type key: ${reminder.id} -> $key"
            }
        }
        program.dataActions.forEach { action ->
            action.labelKey?.let { key ->
                if (key !in localization.translatableStringKeys) errors += "Localization string registry missing data action key: ${action.type} -> $key"
            }
        }
        if (localization.aiPromptLocaleField.isBlank()) errors += "Localization AI prompt locale field is blank"
        if (localization.analyticsLocaleField.isBlank()) errors += "Localization analytics locale field is blank"
        return errors
    }
}
