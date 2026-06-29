package com.medmonitoring.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medmonitoring.core.ui.theme.LocalProgramVisuals

@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        maxLines = 3,
        overflow = TextOverflow.Clip
    )
}

@Composable
internal fun MetricValueText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    compact: Boolean = false
) {
    Text(
        text = text,
        modifier = modifier,
        style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip
    )
}

@Composable
internal fun UnitText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Clip
    )
}

@Composable
internal fun MetaText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 4,
        overflow = TextOverflow.Clip
    )
}

@Composable
internal fun PaddedSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val visuals = LocalProgramVisuals.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = visuals.spacing.screenHorizontalDp.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        content = content
    )
}

@Composable
internal fun FullWidthSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        content = content
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AdaptiveActionRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        content = content
    )
}

@Composable
internal fun ResponsiveFabContent(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(max = 520.dp)
            .padding(horizontal = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Clip
        )
    }
}
