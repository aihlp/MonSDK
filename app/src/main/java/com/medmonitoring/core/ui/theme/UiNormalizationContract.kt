package com.medmonitoring.core.ui.theme

import com.medmonitoring.core.domain.model.ProgramVisualConfig

object UiNormalizationContract {
    const val GridUnitDp = 4
    const val MinTouchTargetDp = 48
    const val MinChipHeightDp = 32
    const val ScreenHorizontalDp = 16
    const val ScreenBottomDp = 80
    const val AppBarHeightDp = 64
    const val TabHeightDp = 48
    const val FindingsMinWidthFraction = 0.5f
    const val FindingsMaxWidthFraction = 1.0f
    const val LargeFontScale = AdaptiveTypographyPolicy.LargeFontScale
    const val SecondaryLargeScale = AdaptiveTypographyPolicy.SecondaryLargeScale
    const val TertiaryLargeScale = AdaptiveTypographyPolicy.TertiaryLargeScale

    val CanonicalTypographySp = mapOf(
        "screenTitle" to 20,
        "sectionHeader" to 12,
        "statValue" to 30,
        "summaryValue" to 20,
        "stepperValue" to 28,
        "tagLabel" to 11,
        "meta" to 9
    )

    fun normalizedDiffs(visuals: ProgramVisualConfig): List<String> {
        val errors = mutableListOf<String>()
        val spacing = visuals.spacing
        val typography = visuals.typography
        val components = visuals.components

        if (visuals.theme.useDynamicColor) {
            errors += "Dynamic color must be disabled for cross-app UI identity"
        }
        if (spacing.gridUnitDp != GridUnitDp) {
            errors += "Visual grid unit must be ${GridUnitDp}dp"
        }
        if (spacing.screenHorizontalDp != ScreenHorizontalDp) {
            errors += "Screen horizontal padding must be ${ScreenHorizontalDp}dp"
        }
        if (spacing.screenBottomDp < ScreenBottomDp) {
            errors += "Screen bottom padding must be at least ${ScreenBottomDp}dp"
        }
        if (spacing.chipHeightDp < MinChipHeightDp) {
            errors += "Chip height must be at least ${MinChipHeightDp}dp"
        }
        if (components.appBar.heightDp != AppBarHeightDp) {
            errors += "App bar height must be ${AppBarHeightDp}dp"
        }
        if (components.tabs.heightDp < TabHeightDp) {
            errors += "Tab height must be at least ${TabHeightDp}dp"
        }
        if (components.appBar.iconSizeDp < MinTouchTargetDp) {
            errors += "App bar icon touch target must be at least ${MinTouchTargetDp}dp"
        }
        if (visuals.shapes.buttonRadiusDp < MinTouchTargetDp / 2) {
            errors += "Button radius must preserve Material 3 pill affordance"
        }
        if (!typography.tabularNumbers) {
            errors += "Numeric typography must use tabular numbers"
        }
        val actualTypography = mapOf(
            "screenTitle" to typography.screenTitleSp,
            "sectionHeader" to typography.sectionHeaderSp,
            "statValue" to typography.statValueSp,
            "summaryValue" to typography.summaryValueSp,
            "stepperValue" to typography.stepperValueSp,
            "tagLabel" to typography.tagLabelSp,
            "meta" to typography.metaSp
        )
        CanonicalTypographySp.forEach { (name, size) ->
            if (actualTypography[name] != size) {
                errors += "Typography token $name must be ${size}sp"
            }
        }
        return errors
    }
}
