package com.medmonitoring.core.domain.model

data class ProgramVisualConfig(
    val theme: ThemeConfig,
    val spacing: SpacingConfig = SpacingConfig(),
    val shapes: ShapeConfig = ShapeConfig(),
    val typography: TypographyConfig = TypographyConfig(),
    val severityPalette: Map<SeverityLevel, SemanticColorConfig>,
    val tagPalettes: Map<TagColorRole, SemanticColorConfig>,
    val components: ComponentStyleConfig = ComponentStyleConfig()
)

data class ThemeConfig(
    val seedColorHex: String,
    val lightColors: StaticColorSchemeConfig,
    val useDynamicColor: Boolean = true,
    /** Optional explicit dark color scheme; when null a generated dark scheme is used. */
    val darkColors: StaticColorSchemeConfig? = null
)

data class StaticColorSchemeConfig(
    val primary: String,
    val onPrimary: String,
    val primaryContainer: String,
    val secondary: String,
    val onSecondary: String,
    val surface: String,
    val surfaceVariant: String,
    val onSurface: String,
    val onSurfaceVariant: String,
    val outline: String,
    val outlineVariant: String,
    val error: String,
    val onError: String
)

enum class SeverityLevel {
    Normal,
    Elevated,
    High,
    Critical,
    Neutral
}

data class SemanticColorConfig(
    val contentHex: String,
    val containerHex: String,
    val outlineHex: String
)

data class SpacingConfig(
    val gridUnitDp: Int = 4,
    val screenHorizontalDp: Int = 16,
    val screenTopDp: Int = 16,
    val screenBottomDp: Int = 80,
    val sectionGapDp: Int = 20,
    val cardPaddingDp: Int = 16,
    val cardGapDp: Int = 12,
    val chipHeightDp: Int = 24,
    val chipHorizontalPaddingDp: Int = 10,
    val chipGapDp: Int = 6,
    val statColumnGapDp: Int = 28
)

data class ShapeConfig(
    val chipRadiusDp: Int = 7,
    val smallRadiusDp: Int = 12,
    val cardRadiusDp: Int = 24,
    val sheetRadiusDp: Int = 28,
    val dialogRadiusDp: Int = 28,
    val buttonRadiusDp: Int = 50,
    val menuRadiusDp: Int = 14,
    val cardBorderWidthDp: Float = 1.5f
)

data class TypographyConfig(
    val tabularNumbers: Boolean = true,
    val screenTitleSp: Int = 22,
    val sectionHeaderSp: Int = 16,
    val statValueSp: Int = 31,
    val summaryValueSp: Int = 22,
    val stepperValueSp: Int = 32,
    val tagLabelSp: Int = 12,
    val metaSp: Int = 12
)

data class ComponentStyleConfig(
    val appBar: AppBarStyleConfig = AppBarStyleConfig(),
    val tabs: TabStyleConfig = TabStyleConfig(),
    val healthCard: HealthCardStyleConfig = HealthCardStyleConfig(),
    val findings: FindingsStyleConfig = FindingsStyleConfig(),
    val summary: SummaryStyleConfig = SummaryStyleConfig(),
    val saveAction: SaveActionStyleConfig = SaveActionStyleConfig(),
    val animation: AnimationStyleConfig = AnimationStyleConfig()
)

data class AppBarStyleConfig(
    val heightDp: Int = 64,
    val iconSizeDp: Int = 38,
    val iconRadiusDp: Int = 10,
    val iconResourceName: String = "",
    val iconGradientHex: List<String> = emptyList(),
    val iconContent: String = "",
    val subtitle: String = ""
)

data class TabStyleConfig(
    val heightDp: Int = 48,
    val indicatorHeightDp: Int = 2
)

data class HealthCardStyleConfig(
    val maxVisibleTagRows: Int = 5,
    val pressedElevationDp: Int = 2,
    val statLabelUppercase: Boolean = true
)

data class FindingsStyleConfig(
    val widthFraction: Float = 0.76f,
    val cardPaddingDp: Int = 20,
    val cardGapDp: Int = 12
)

data class SummaryStyleConfig(
    val itemMinWidthDp: Int = 80,
    val adherenceMinWidthDp: Int = 90,
    val itemGapDp: Int = 8
)

data class SaveActionStyleConfig(
    val gradientHex: List<String> = emptyList(),
    val cornerRadiusDp: Int = 16,
    val elevationDp: Int = 6
)

data class AnimationStyleConfig(
    val selectionDurationMs: Int = 150,
    val cardElevationDurationMs: Int = 200,
    val fabAppearanceDurationMs: Int = 300
)
