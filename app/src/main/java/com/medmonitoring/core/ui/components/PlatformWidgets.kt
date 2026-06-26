package com.medmonitoring.core.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import android.graphics.Paint
import android.text.Layout
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.medmonitoring.core.domain.model.GraphEventMarkerDefinition
import com.medmonitoring.core.domain.model.GraphSeriesType
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.domain.model.GraphDefinition
import com.medmonitoring.core.domain.model.AxisScaleStrategy
import com.medmonitoring.app.R
import com.medmonitoring.app.ui.localizedActionLabel
import com.medmonitoring.app.ui.localizedImageContentDescription
import com.medmonitoring.app.ui.localizedInstructions
import com.medmonitoring.app.ui.localizedLabel
import com.medmonitoring.app.ui.localizedMessage
import com.medmonitoring.app.ui.localizedTitle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.Scroll
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalBox
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.Shape
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val ChipGap = 8.dp

@Composable
fun GraphWidget(
    records: List<UserRecord>?,
    definition: GraphDefinition,
    modifier: Modifier = Modifier,
    showEmptyState: Boolean = true,
    onSetReminder: () -> Unit = {}
) {
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 220.dp),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = records,
            animationSpec = tween(durationMillis = 400),
            label = "GraphContentCrossfade"
        ) { currentRecords ->
            when {
                currentRecords == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                    }
                }
                currentRecords.isEmpty() && showEmptyState -> {
                    EmptyStateBlock(definition, onSetReminder)
                }
                currentRecords.isEmpty() -> Unit
                else -> {
                    val chartRecords = remember(currentRecords) { currentRecords.sortedBy { it.timestamp } }
                    ChartContentBlock(chartRecords, definition)
                }
            }
        }
    }
}

@Composable
private fun EmptyStateBlock(definition: GraphDefinition, onSetReminder: () -> Unit) {
    val context = LocalContext.current
    val imageResourceId = remember(definition.emptyState.imageResourceName) {
        context.resources.getIdentifier(
            definition.emptyState.imageResourceName,
            "drawable",
            context.packageName
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (imageResourceId != 0) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Image(
                    painter = painterResource(imageResourceId),
                    contentDescription = definition.emptyState.localizedImageContentDescription(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = definition.emptyState.localizedTitle(),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(
            definition.emptyState.localizedMessage(),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        definition.emptyState.localizedInstructions().forEach { instruction ->
            Text(
                text = "• $instruction",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onSetReminder, modifier = Modifier.padding(top = 8.dp)) {
            Text(definition.emptyState.localizedActionLabel())
        }
    }
}

@Composable
private fun ChartContentBlock(chartRecords: List<UserRecord>, definition: GraphDefinition) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val fallbackSeriesColor = MaterialTheme.colorScheme.primary
            val seriesColors = remember(definition.series, fallbackSeriesColor) {
                definition.series.associateBy({ it.metricId }, { it.colorHex.toComposeColor() ?: fallbackSeriesColor })
            }
            if (definition.showLegend) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ChipGap),
                    verticalArrangement = Arrangement.spacedBy(ChipGap)
                ) {
                    definition.series.forEach { series ->
                        when (series.type) {
                            GraphSeriesType.Line -> ChartLegendItem(series.localizedLabel(), seriesColors.getValue(series.metricId), LegendShape.Line)
                            GraphSeriesType.Bar -> ChartLegendItem(series.localizedLabel(), seriesColors.getValue(series.metricId), LegendShape.Bar)
                            GraphSeriesType.PointOrLine -> ChartLegendItem(series.localizedLabel(), seriesColors.getValue(series.metricId), LegendShape.Dot)
                            GraphSeriesType.EventMarker -> {
                                val marker = definition.eventMarker(series.metricId)
                                ChartEventLegendItem(
                                    label = series.localizedLabel(),
                                    color = seriesColors.getValue(series.metricId),
                                    symbol = marker?.symbol.orEmpty()
                                )
                            }
                        }
                    }
                }
            }
            val modelProducer = remember { CartesianChartModelProducer() }
            val zoneId = ZoneId.systemDefault()
            val xFormatter = remember(chartRecords, definition.eventMarkers) {
                CartesianValueFormatter { _, value, _ ->
                    val index = value.toInt().coerceIn(chartRecords.indices)
                    val dateTime = DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale.getDefault())
                        .withZone(zoneId)
                        .format(chartRecords[index].timestamp)
                    val markers = definition.eventSymbols(chartRecords[index])
                    "$dateTime\n$markers"
                }
            }
            val graphRange = remember(chartRecords, definition) {
                val values = chartRecords.flatMap { record ->
                    definition.orderedMeasurementSeries.mapNotNull { series -> record.measurementValue(series.metricId) }
                }
                buildGraphRange(values, definition)
            }
            val xValues = remember(chartRecords) {
                chartRecords.indices.map { it.toDouble() }
            }
            val maxZoomRecords = remember(chartRecords, definition) {
                maxOf(definition.visibleRecordCount, chartRecords.size, 32).toFloat()
            }
            val chartScrollState = rememberVicoScrollState(
                scrollEnabled = definition.horizontalPastScroll,
                initialScroll = Scroll.Absolute.End
            )
            val chartZoomState = rememberVicoZoomState(
                zoomEnabled = true,
                initialZoom = Zoom.x(definition.visibleRecordCount.coerceAtLeast(1).toDouble()),
                minZoom = Zoom.Content,
                maxZoom = Zoom.fixed(maxZoomRecords)
            )
            val rangeProvider = remember<CartesianLayerRangeProvider>(graphRange, xValues) {
                CartesianLayerRangeProvider.fixed(
                    minX = xValues.minOrNull(),
                    maxX = xValues.maxOrNull(),
                    minY = graphRange.minY,
                    maxY = graphRange.maxY
                )
            }
            val valueLabels = definition.orderedMeasurementSeries.associate { series ->
                series.metricId to rememberValueBadge(color = seriesColors.getValue(series.metricId))
            }
            val axisLabel = rememberTextComponent(
                color = MaterialTheme.colorScheme.onSurface,
                textSize = 9.sp,
                textAlignment = Layout.Alignment.ALIGN_CENTER,
                lineCount = 2,
                truncateAt = TextUtils.TruncateAt.END
            )
            val safePointColor = definition.pointOverlay.safeColorHex.toComposeColor() ?: MaterialTheme.colorScheme.primary
            val dangerPointColor = definition.pointOverlay.dangerColorHex.toComposeColor() ?: MaterialTheme.colorScheme.error
            val rangePointProvider = remember(definition.pointOverlay) {
                RangeAwarePointProvider(
                    normalRange = definition.pointOverlay.normalRange,
                    normalPoint = LineCartesianLayer.point(
                        ShapeComponent(fill(safePointColor), CorneredShape.Pill),
                        12.dp
                    ),
                    outOfRangePoint = LineCartesianLayer.point(
                        ShapeComponent(fill(dangerPointColor), CorneredShape.Pill),
                        12.dp
                    )
                )
            }
            val lineProvider = LineCartesianLayer.LineProvider.series(
                *definition.orderedMeasurementSeries.map { series ->
                    val pointProvider = if (series.type == GraphSeriesType.PointOrLine && series.metricId == definition.pointOverlay.metricId) {
                        rangePointProvider
                    } else {
                        null
                    }
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(
                            fill(if (pointProvider == null) seriesColors.getValue(series.metricId) else Color.Transparent)
                        ),
                        stroke = LineCartesianLayer.LineStroke.Continuous(
                            if (pointProvider == null) definition.lineThicknessDp else 0f,
                            Paint.Cap.ROUND
                        ),
                        pointConnector = definition.pointConnector(),
                        pointProvider = pointProvider,
                        dataLabel = if (definition.showValueLabels) valueLabels[series.metricId] else null,
                        dataLabelPosition = Position.Vertical.Center
                    )
                }.toTypedArray()
            )
            LaunchedEffect(chartRecords) {
                modelProducer.runTransaction {
                    lineSeries {
                        definition.orderedMeasurementSeries.forEach { graphSeries ->
                            series(x = xValues, y = chartRecords.map { it.measurementValue(graphSeries.metricId) ?: 0.0 })
                        }
                    }
                }
            }
            val fallbackSafeZoneColor = MaterialTheme.colorScheme.primary
            val safeZoneDecorations = remember(definition.safeZones, fallbackSafeZoneColor) {
                definition.safeZones.map { zone ->
                    HorizontalBox(
                        y = { zone.range.first.toDouble()..zone.range.last.toDouble() },
                        box = ShapeComponent(
                            fill((zone.colorHex.toComposeColor() ?: fallbackSafeZoneColor).copy(alpha = zone.alpha))
                        )
                    )
                }
            }
            val axisGuideline = if (definition.showGrid) rememberAxisGuidelineComponent() else null
            val tapMarker = if (definition.showTapMarker) {
                rememberDefaultCartesianMarker(label = axisLabel)
            } else {
                null
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = lineProvider,
                            pointSpacing = definition.recordSlotWidthDp.dp,
                            rangeProvider = rangeProvider
                        ),
                        startAxis = if (definition.showAxisLabels) {
                            VerticalAxis.rememberStart(guideline = axisGuideline)
                        } else null,
                        bottomAxis = if (definition.showAxisLabels) {
                            HorizontalAxis.rememberBottom(
                                label = axisLabel,
                                guideline = axisGuideline,
                                valueFormatter = xFormatter,
                                itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 1 }, addExtremeLabelPadding = true) }
                            )
                        } else null,
                        marker = tapMarker,
                        decorations = safeZoneDecorations,
                    ),
                    modelProducer = modelProducer,
                    scrollState = chartScrollState,
                    zoomState = chartZoomState
                )
            }
        }
    }
}

private fun GraphDefinition.pointConnector(): LineCartesianLayer.PointConnector =
    if (lineCurvature > 0f) {
        LineCartesianLayer.PointConnector.cubic(lineCurvature.coerceIn(0.01f, 1f))
    } else {
        LineCartesianLayer.PointConnector.Sharp
    }

private fun GraphDefinition.eventMarker(seriesId: String): GraphEventMarkerDefinition? =
    eventMarkers.firstOrNull { it.seriesId == seriesId }

private val GraphDefinition.measurementSeries
    get() = series.filterNot { it.type == GraphSeriesType.EventMarker }

private val GraphDefinition.orderedMeasurementSeries
    get() = measurementSeries.sortedBy { series ->
        val isPointSeries = series.type == GraphSeriesType.PointOrLine
        if (pointSeriesInBackground) !isPointSeries else isPointSeries
    }

private fun GraphDefinition.eventSymbols(record: UserRecord): String =
    eventMarkers
        .filter { marker -> record.hasActiveEvent(marker) }
        .joinToString(separator = " ") { it.symbol }

private fun UserRecord.hasActiveEvent(marker: GraphEventMarkerDefinition): Boolean =
    events.any { event ->
        event.key == marker.eventKey &&
            marker.activeStatuses.any { status -> status.equals(event.status, ignoreCase = true) }
    }

@Composable
private fun ChartEventLegendItem(label: String, color: Color, symbol: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = symbol,
            color = color,
            style = MaterialTheme.typography.labelMedium
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ChartLegendItem(label: String, color: Color, shape: LegendShape) {
    val markColor = MaterialTheme.colorScheme.onPrimary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(14.dp)) {
            when (shape) {
                LegendShape.Line -> drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 4f)
                LegendShape.Bar -> drawRect(color, topLeft = Offset(size.width * 0.35f, size.height * 0.15f), size = androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height * 0.7f))
                LegendShape.Dot -> drawCircle(color, radius = size.minDimension / 3f)
                LegendShape.Check -> {
                    drawCircle(color, radius = size.minDimension / 2.5f)
                    drawLine(markColor, Offset(size.width * 0.3f, size.height * 0.55f), Offset(size.width * 0.45f, size.height * 0.7f), strokeWidth = 2f)
                    drawLine(markColor, Offset(size.width * 0.45f, size.height * 0.7f), Offset(size.width * 0.72f, size.height * 0.32f), strokeWidth = 2f)
                }
                LegendShape.Cross -> {
                    drawCircle(color, radius = size.minDimension / 2.5f)
                    drawLine(markColor, Offset(size.width * 0.32f, size.height * 0.32f), Offset(size.width * 0.68f, size.height * 0.68f), strokeWidth = 2f)
                    drawLine(markColor, Offset(size.width * 0.68f, size.height * 0.32f), Offset(size.width * 0.32f, size.height * 0.68f), strokeWidth = 2f)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun rememberValueBadge(color: Color) = rememberTextComponent(
    color = Color.White,
    textSize = 9.sp,
    textAlignment = Layout.Alignment.ALIGN_CENTER,
    padding = insets(horizontal = 4.dp, vertical = 1.dp),
    background = rememberShapeComponent(
        fill = fill(color),
        shape = CorneredShape.Pill
    )
)

private enum class LegendShape { Line, Bar, Dot, Check, Cross }

private data class GraphRange(val minY: Double, val maxY: Double)

private class RangeAwarePointProvider(
    private val normalRange: IntRange,
    private val normalPoint: LineCartesianLayer.Point,
    private val outOfRangePoint: LineCartesianLayer.Point
) : LineCartesianLayer.PointProvider {
    override fun getPoint(
        entry: LineCartesianLayerModel.Entry,
        seriesIndex: Int,
        extraStore: ExtraStore
    ): LineCartesianLayer.Point = if (entry.y.toInt() in normalRange) normalPoint else outOfRangePoint

    override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point = outOfRangePoint
}

private val HeartShape = Shape { _, path, left, top, right, bottom ->
    val width = right - left
    val height = bottom - top
    path.moveTo(left + width * 0.5f, bottom)
    path.cubicTo(
        left + width * 0.42f, bottom - height * 0.12f,
        left, top + height * 0.55f,
        left, top + height * 0.28f
    )
    path.cubicTo(
        left, top,
        left + width * 0.34f, top,
        left + width * 0.5f, top + height * 0.22f
    )
    path.cubicTo(
        left + width * 0.66f, top,
        right, top,
        right, top + height * 0.28f
    )
    path.cubicTo(
        right, top + height * 0.55f,
        left + width * 0.58f, bottom - height * 0.12f,
        left + width * 0.5f, bottom
    )
    path.close()
}

private fun buildGraphRange(values: List<Double>, definition: GraphDefinition): GraphRange {
    if (values.isEmpty()) return GraphRange(0.0, 1.0)
    return when (definition.yAxisStrategy) {
        AxisScaleStrategy.FromZero -> GraphRange(
            minY = 0.0,
            maxY = values.maxOrNull()?.plus(definition.maxPadding) ?: 1.0
        )
        AxisScaleStrategy.FixedRange -> GraphRange(
            minY = values.minOrNull()?.minus(definition.minPadding) ?: 0.0,
            maxY = values.maxOrNull()?.plus(definition.maxPadding) ?: 1.0
        )
        AxisScaleStrategy.DataRange,
        AxisScaleStrategy.PaddedDataRange,
        AxisScaleStrategy.PerMetricAxis,
        AxisScaleStrategy.LogScale -> {
            val min = values.minOrNull() ?: 0.0
            val max = values.maxOrNull() ?: min + 1.0
            val paddedMin = (min - definition.minPadding).coerceAtLeast(0.0)
            val paddedMax = max + definition.maxPadding
            GraphRange(minY = paddedMin, maxY = paddedMax)
        }
    }
}

private fun UserRecord.measurementValue(metricId: String): Double? =
    measurements.firstOrNull { it.key == metricId }?.value

private fun String.toComposeColor(): Color? {
    val value = removePrefix("#").toLongOrNull(16) ?: return null
    return when (removePrefix("#").length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> null
    }
}

@Composable
fun NumberStepper(label: String, value: Int?, range: IntRange, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            maxLines = 2
        )
        FilledTonalIconButton(
            onClick = { onChange(((value ?: range.first) - 1).coerceIn(range)) },
            modifier = Modifier.size(48.dp)
        ) {
            Text("-", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(
            value = value?.toString().orEmpty(),
            onValueChange = { onChange(it.toIntOrNull()?.coerceIn(range)) },
            modifier = Modifier.width(104.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        FilledTonalIconButton(
            onClick = { onChange(((value ?: range.first) + 1).coerceIn(range)) },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label")
        }
    }
}

@Composable
fun CompactNumberStepper(label: String, value: Int?, range: IntRange, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalIconButton(
                onClick = { onChange(((value ?: range.first) - 1).coerceIn(range)) },
                modifier = Modifier.size(48.dp)
            ) {
                Text("-", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = value?.toString().orEmpty(),
                onValueChange = { onChange(it.toIntOrNull()?.coerceIn(range)) },
                modifier = Modifier.width(78.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            FilledTonalIconButton(
                onClick = { onChange(((value ?: range.first) + 1).coerceIn(range)) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase $label")
            }
        }
    }
}
