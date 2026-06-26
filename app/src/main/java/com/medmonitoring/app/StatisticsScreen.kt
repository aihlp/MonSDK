package com.medmonitoring.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalConfiguration
import com.medmonitoring.app.ui.localize
import com.medmonitoring.app.ui.localizeDashboardLabel
import com.medmonitoring.app.ui.localizedLabel
import com.medmonitoring.app.ui.localizedBasis
import com.medmonitoring.app.ui.localizedMessage
import com.medmonitoring.app.ui.localizedTitle
import com.medmonitoring.app.ui.MedViewModel
import com.medmonitoring.core.config.ConfigStrictMode
import com.medmonitoring.core.domain.model.*
import com.medmonitoring.core.ui.theme.LocalProgramVisuals
import com.medmonitoring.core.ui.theme.toColor

@Composable
internal fun StatisticsScreen(viewModel: MedViewModel) {
    val records by viewModel.records.collectAsState()
    val analytics by viewModel.analytics.collectAsState()
    val blocks = viewModel.uiDefinition.statisticsScreenBlocks
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (blocks.contains(WidgetType.AnalyticsSummaryWidget) &&
            ConfigStrictMode.assertStatisticsWidgetAllowed(viewModel.uiDefinition, WidgetType.AnalyticsSummaryWidget)
        ) {
            KeyMetricsSection(analytics.dashboardMetrics)
        }
        if (blocks.contains(WidgetType.AnalyticsDetailsWidget) &&
            ConfigStrictMode.assertStatisticsWidgetAllowed(viewModel.uiDefinition, WidgetType.AnalyticsDetailsWidget)
        ) {
            FindingsCarousel(analytics.findings)
        }
        if (blocks.contains(WidgetType.HistoryWidget) &&
            ConfigStrictMode.assertStatisticsWidgetAllowed(viewModel.uiDefinition, WidgetType.HistoryWidget)
        ) {
            HistoryCards(
                records.orEmpty(),
                viewModel.program,
                viewModel.uiDefinition,
                viewModel::upsertRecord,
                viewModel::deleteRecord
            )
        }
    }
}

@Composable
private fun KeyMetricsSection(metrics: List<StatisticMetric>) {
    val visuals = LocalProgramVisuals.current
    val cardWidth = metrics.maxOfOrNull { metric ->
        maxOf(metric.label.length, metric.value.length + (metric.unit?.length ?: 0))
    }?.let { (72 + it * 4).coerceIn(96, 156).dp } ?: 96.dp
    Column(Modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(visuals.components.summary.itemGapDp.dp),
        ) {
            items(metrics) { metric ->
                MetricTile(metric, visuals, cardWidth)
            }
        }
    }
}

@Composable
private fun MetricTile(metric: StatisticMetric, visuals: ProgramVisualConfig, cardWidth: Dp) {
    val progress = if (metric.role == StatisticRole.Percent) metric.value.toFloatOrNull()?.div(100f) else null
    val severity = when {
        progress == null -> SeverityLevel.Normal
        progress >= 0.8f -> SeverityLevel.Normal
        progress >= 0.6f -> SeverityLevel.Elevated
        else -> SeverityLevel.Critical
    }
    val accent = visuals.severityPalette.getValue(severity).contentHex.toColor()
    Card(
        modifier = Modifier.width(cardWidth).height(92.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(visuals.shapes.cardBorderWidthDp.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(
                    metric.value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent,
                    fontWeight = FontWeight.ExtraBold
                )
                metric.unit?.let { Text(" $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = accent)
            } else {
                Spacer(Modifier.height(4.dp))
            }
            Text(
                metric.localizedLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun FindingsCarousel(findings: List<FindingCardModel>) {
    if (findings.isEmpty()) return
    val visuals = LocalProgramVisuals.current
    val cardWidth = (LocalConfiguration.current.screenWidthDp * visuals.components.findings.widthFraction).dp
    val maxTextLines = findings.maxOf { finding ->
        estimatedLines(finding.title, 28) +
            estimatedLines(finding.message, 34) +
            estimatedLines(finding.basis, 38)
    }
    val cardHeight = (190 + maxTextLines * 18 + if (findings.any { it.metrics.size > 1 }) 32 else 0)
        .coerceIn(250, 390).dp
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.findings), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.insights_swipe, findings.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(visuals.components.findings.cardGapDp.dp),
            contentPadding = PaddingValues(horizontal = visuals.spacing.screenHorizontalDp.dp)
        ) {
            items(findings) { finding ->
                FindingCard(finding, cardWidth, cardHeight)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindingCard(finding: FindingCardModel, cardWidth: Dp, cardHeight: Dp) {
    val visuals = LocalProgramVisuals.current
    val semantic = visuals.severityPalette.getValue(finding.severity.toSeverityLevel())
    val container = semantic.containerHex.toColor()
    val accent = semantic.contentHex.toColor()
    val outline = semantic.outlineHex.toColor()
    val bodyColor = semantic.contentHex.toColor()
    val title = finding.localizedTitle()
    val message = finding.localizedMessage()
    val basis = finding.localizedBasis()
    val severityLabel = when (finding.severity) {
        FindingSeverity.Positive -> stringResource(R.string.good)
        FindingSeverity.Risk -> stringResource(R.string.risk)
        FindingSeverity.Neutral -> stringResource(R.string.info)
    }
    Card(
        modifier = Modifier.width(cardWidth).height(cardHeight),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(visuals.shapes.cardBorderWidthDp.dp, outline)
    ) {
        Column(
            Modifier.fillMaxSize().padding(visuals.components.findings.cardPaddingDp.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            AssistChip(
                onClick = {},
                label = { Text(severityLabel) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = accent,
                    labelColor = finding.severity.onChipColor()
                )
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = bodyColor,
                fontWeight = FontWeight.SemiBold
            )
            finding.metrics.firstOrNull()?.let {
                val value = it.unit?.let { unit -> "${it.value} $unit" } ?: it.value
                Text(value, style = MaterialTheme.typography.displaySmall, color = accent, fontWeight = FontWeight.ExtraBold)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = bodyColor, fontWeight = FontWeight.SemiBold)
            Text(basis, style = MaterialTheme.typography.bodySmall, color = bodyColor.copy(alpha = 0.74f))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.chipGap),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.chipGap)
            ) {
                finding.metrics.drop(1).take(3).forEach { metric ->
                    val value = metric.unit?.let { "${metric.value} $it" } ?: metric.value
                    CompactTagChip("${metric.localizedLabel()}: $value", finding.severity.metricChipColor())
                }
            }
        }
    }
}

private fun estimatedLines(text: String, charactersPerLine: Int): Int =
    ((text.length.coerceAtLeast(1) + charactersPerLine - 1) / charactersPerLine).coerceAtLeast(1)

private fun FindingSeverity.toSeverityLevel(): SeverityLevel = when (this) {
    FindingSeverity.Positive -> SeverityLevel.Normal
    FindingSeverity.Risk -> SeverityLevel.Critical
    FindingSeverity.Neutral -> SeverityLevel.Neutral
}

@Composable
private fun FindingSeverity.metricChipColor(): Color =
    LocalProgramVisuals.current.severityPalette.getValue(toSeverityLevel()).contentHex.toColor()

@Composable
private fun FindingSeverity.onChipColor(): Color = when (this) {
    FindingSeverity.Positive -> MaterialTheme.colorScheme.onPrimary
    FindingSeverity.Risk -> MaterialTheme.colorScheme.onError
    FindingSeverity.Neutral -> MaterialTheme.colorScheme.onSurface
}
