package com.medmonitoring.core.config

import com.medmonitoring.core.domain.model.*

object ConfigRegistry {
    val recordWidgetRenderers = setOf(
        WidgetType.GraphWidget,
        WidgetType.DateTimeWidget,
        WidgetType.EventStatusWidget,
        WidgetType.EventAmountWidget,
        WidgetType.PairedVerticalWheelInputWidget,
        WidgetType.SingleHorizontalWheelInputWidget,
        WidgetType.ScaleSliderInputWidget,
        WidgetType.TagGroupsWidget,
        WidgetType.NoteWidget,
        WidgetType.SaveButtonWidget
    )

    val statisticsWidgetRenderers = setOf(
        WidgetType.AnalyticsSummaryWidget,
        WidgetType.AnalyticsDetailsWidget,
        WidgetType.HistoryWidget
    )

    val settingsSectionRenderers = SettingsSectionType.entries.toSet()

    val analyticsRuleRenderers = setOf("adherence", "averages", "frequencies", "missed_sys")

    val inputBlockRawEventMappers = setOf(
        WidgetType.DateTimeWidget,
        WidgetType.EventStatusWidget,
        WidgetType.EventAmountWidget,
        WidgetType.PairedVerticalWheelInputWidget,
        WidgetType.SingleHorizontalWheelInputWidget,
        WidgetType.ScaleSliderInputWidget,
        WidgetType.TagGroupsWidget,
        WidgetType.NoteWidget
    )

    val factFieldMappings = RecordField.entries.toSet()

    val dataActionRenderers = DataActionType.entries.toSet()
}

object ConfigStrictMode {
    var isDebug: Boolean = true
    var localLogger: (String) -> Unit = {}

    fun assertRecordWidgetAllowed(ui: ProgramUiDefinition, widget: WidgetType): Boolean {
        return assertAllowed(ui.recordScreenBlocks.contains(widget), "Record widget is not declared: $widget")
    }

    fun assertStatisticsWidgetAllowed(ui: ProgramUiDefinition, widget: WidgetType): Boolean {
        return assertAllowed(ui.statisticsScreenBlocks.contains(widget), "Statistics widget is not declared: $widget")
    }

    fun assertSettingsSectionAllowed(ui: ProgramUiDefinition, section: SettingsSectionType): Boolean {
        return assertAllowed(ui.settingsSections.contains(section), "Settings section is not declared: $section")
    }

    fun assertTagGroupAllowed(program: UniversalProgramDefinition, groupId: String): Boolean {
        return assertAllowed(program.tagGroups.any { it.id == groupId }, "Tag group is not declared: $groupId")
    }

    fun assertAnalyticsRuleAllowed(program: UniversalProgramDefinition, ruleId: String): Boolean {
        return assertAllowed(program.analyticsRules.any { it.id == ruleId }, "Analytics rule is not declared: $ruleId")
    }

    fun assertDataActionAllowed(program: UniversalProgramDefinition, action: DataActionType): Boolean {
        return assertAllowed(program.dataActions.any { it.type == action }, "Data action is not declared: $action")
    }

    private fun assertAllowed(allowed: Boolean, message: String): Boolean {
        if (allowed) return true
        if (isDebug) error(message)
        localLogger(message)
        return false
    }
}
