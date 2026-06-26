package com.medmonitoring.app

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medmonitoring.app.ui.RecordInputState
import com.medmonitoring.app.ui.localize
import com.medmonitoring.app.ui.localizeContextTag
import com.medmonitoring.app.ui.localizedLabel
import com.medmonitoring.app.ui.localizedResource
import com.medmonitoring.app.ui.localizedTag
import com.medmonitoring.app.ui.localizedTitle
import com.medmonitoring.core.domain.model.*
import com.medmonitoring.core.ui.theme.LocalProgramVisuals
import com.medmonitoring.core.ui.theme.toColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID


@Composable
internal fun HistoryCards(
    records: List<UserRecord>,
    program: UniversalProgramDefinition,
    uiDefinition: ProgramUiDefinition,
    onSave: (UserRecord) -> Unit,
    onDelete: (String) -> Unit
) {
    val visuals = LocalProgramVisuals.current
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
    var editingRecord by remember { mutableStateOf<UserRecord?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(stringResource(R.string.history), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        records.forEach { record ->
            var expanded by remember { mutableStateOf(false) }
            var tagsExpanded by remember(record.id) { mutableStateOf(false) }
            val historyStatus = historyMetricStatus(record, program.metricComponents)
            val displayTagGroups = record.displayTagGroups(program.tagGroups)
            val visibleTagGroups = if (tagsExpanded) displayTagGroups
            else displayTagGroups.take(visuals.components.healthCard.maxVisibleTagRows)
            val outlineColor = historyStatus.outlineColor()
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(
                    visuals.shapes.cardBorderWidthDp.dp,
                    outlineColor
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 2.dp)
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            program.metricComponents.forEach { metric ->
                                record.metricValue(metric.id)?.let { value ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            metric.localizedLabel().uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.6.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            value.toString(),
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontSize = 34.sp,
                                                lineHeight = 34.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFeatureSettings = "tnum"
                                            ),
                                            color = metricStatus(metric, value).accent(),
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            metric.unit,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                        Box {
                            IconButton(
                                onClick = { expanded = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.actions),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                shape = MaterialTheme.shapes.small
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_record)) },
                                    onClick = {
                                        expanded = false
                                        editingRecord = record
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.duplicate)) },
                                    onClick = {
                                        expanded = false
                                        editingRecord = record.copy(
                                            id = UUID.randomUUID().toString(),
                                            timestamp = Instant.now(),
                                            createdAt = Instant.now(),
                                            updatedAt = Instant.now()
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                                )
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { expanded = false; onDelete(record.id) }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                            }
                        }
                    }
                    Text(
                        record.historySubtitle(formatter, program.eventInputs),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (visibleTagGroups.isNotEmpty()) {
                        Column(
                            Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            visibleTagGroups.forEach { group ->
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    group.tags.forEach { tag ->
                                        CompactTagChip(tag.label, tag.color, tag.containerColor)
                                    }
                                }
                            }
                            if (displayTagGroups.size > visuals.components.healthCard.maxVisibleTagRows) {
                                TextButton(
                                    onClick = { tagsExpanded = !tagsExpanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        if (tagsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                    Text(
                                        if (tagsExpanded) {
                                            stringResource(R.string.hide)
                                        } else {
                                            stringResource(
                                                R.string.more_count,
                                                displayTagGroups.drop(visuals.components.healthCard.maxVisibleTagRows)
                                                    .sumOf { it.tags.size }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                    record.note?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
    editingRecord?.let { record ->
        EditRecordSheet(
            initial = record,
            program = program,
            uiDefinition = uiDefinition,
            onDismiss = { editingRecord = null },
            onSave = {
                onSave(it)
                editingRecord = null
            }
        )
    }
}

@Composable
private fun UserRecord.tagsWithRoles(groups: List<TagGroupDefinition>): List<Pair<String, Color>> {
    return groups.flatMap { group -> dimensionLabelsForGroup(group.id).map { it to group.configColor() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRecordSheet(
    initial: UserRecord,
    program: UniversalProgramDefinition,
    uiDefinition: ProgramUiDefinition,
    onDismiss: () -> Unit,
    onSave: (UserRecord) -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial.toInputState()) }
    val selected = remember(initial.id) {
        mutableStateMapOf<String, Set<String>>().apply {
            program.tagGroups.forEach { group ->
                this[group.id] = initial.dimensionLabelsForGroup(group.id)
                    .filterNot { it.startsWith("context.") }
                    .toSet()
            }
        }
    }
    val otherInputs = remember(initial.id) { mutableStateMapOf<String, String>() }
    val automaticTags = remember(initial.id) {
        initial.contextDimensionLabels()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sectionGap)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = program.editAffordance.tintHex.toComposeColor() ?: MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(AppSpacing.sm))
                Text(stringResource(R.string.edit_record))
            }

            ProgramRecordForm(
                state = draft,
                program = program,
                uiDefinition = uiDefinition,
                selectedTags = selected,
                otherInputs = otherInputs,
                customTags = emptyList(),
                timestampLabel = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(draft.timestamp),
                onTimestampChange = { draft = draft.copy(timestamp = it) },
                onEventStatusChange = { key, status ->
                    draft = draft.copy(eventStatuses = draft.eventStatuses + (key to status))
                },
                onEventTextChange = { key, text ->
                    draft = draft.copy(eventTexts = draft.eventTexts + (key to text))
                },
                onMetricChange = { metricId, value ->
                    draft = draft.copy(metricValues = draft.metricValues + (metricId to value))
                },
                onToggleTag = { groupId, tag ->
                    val groupSelected = selected[groupId].orEmpty()
                    selected[groupId] = if (tag in groupSelected) groupSelected - tag else groupSelected + tag
                },
                onSetOtherInput = { groupId, value -> otherInputs[groupId] = value },
                onConfirmOtherTag = { groupId ->
                    val value = otherInputs[groupId].orEmpty().trim()
                    if (value.isNotBlank()) {
                        selected[groupId] = selected[groupId].orEmpty() + value
                        otherInputs.remove(groupId)
                    }
                },
                onNoteChange = { draft = draft.copy(note = it) },
                includeGraph = false,
                bottomSpacer = 0.dp,
                fillAvailable = false,
                scrollable = false
            )

            Button(
                onClick = {
                    val events = program.eventInputs.mapNotNull { input ->
                        val status = draft.eventStatus(input.key).orEmpty()
                        if (status.isBlank()) return@mapNotNull null
                        val (name, amount, unit) = parseEventText(draft.eventText(input.key), input)
                        RecordEvent(input.key, name, status, amount, unit.ifBlank { null })
                    }
                    val dimensions = buildList {
                        program.tagGroups.forEach { group ->
                            selected[group.id].orEmpty().forEach { label ->
                                add(RecordDimension(group.id, label.toDimensionKey(), label))
                            }
                        }
                        automaticTags.forEach { label ->
                            add(RecordDimension("custom", label.toDimensionKey(), label))
                        }
                    }
                    val primaryEvent = events.firstOrNull()
                    onSave(
                        initial.copy(
                            timestamp = draft.timestamp,
                            events = events,
                            dimensions = dimensions,
                            medicationName = primaryEvent?.name.orEmpty(),
                            medicationStatus = primaryEvent?.status.toMedicationStatus(),
                            doseValue = primaryEvent?.amount,
                            doseUnit = primaryEvent?.unit.orEmpty(),
                            measurements = program.metricComponents.mapNotNull { metric ->
                                draft.metricValues[metric.id]?.let {
                                    Measurement(metric.id, it.toDouble(), metric.unit)
                                }
                            },
                            healthyTags = dimensions.labelsFor("healthy"),
                            unhealthyTags = dimensions.labelsFor("unhealthy"),
                            symptomTags = dimensions.labelsFor("symptoms"),
                            otherMedicationTags = dimensions.labelsFor("other_medications"),
                            sideEffectTags = dimensions.labelsFor("side_effects"),
                            customTags = dimensions.labelsFor("custom"),
                            note = draft.note,
                            updatedAt = Instant.now()
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.save)) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.height(AppSpacing.lg))
        }
    }
}

private fun UserRecord.toInputState() = RecordInputState(
    timestamp = timestamp,
    eventStatuses = inputEvents().associate { it.key to it.status },
    eventTexts = inputEvents().associate { event ->
        event.key to buildString {
            append(event.name)
            event.amount?.let { append(" ${it.toInt()}") }
            if (!event.unit.isNullOrBlank()) append(" ${event.unit}")
        }.trim()
    },
    metricValues = measurements.associate { it.key to it.value.toInt() },
    note = note.orEmpty()
)

private fun UserRecord.inputEvents(): List<RecordEvent> =
    events.ifEmpty {
        if (medicationStatus == MedicationStatus.NOT_RECORDED && medicationName.isBlank() && doseValue == null) {
            emptyList()
        } else {
            listOf(RecordEvent("medication", medicationName, medicationStatus.name, doseValue, doseUnit.ifBlank { null }))
        }
    }

private fun UserRecord.dimensionLabelsForGroup(groupId: String): List<String> {
    val explicit = dimensions.filter { it.group == groupId }.map { it.label }
    if (explicit.isNotEmpty()) return explicit
    return legacyTagsForGroup(groupId)
}

private fun UserRecord.contextDimensionLabels(): List<String> =
    dimensions
        .filter { it.label.startsWith("context.") || it.key.startsWith("context.") }
        .map { it.label }
        .ifEmpty { customTags.filter { it.startsWith("context.") } }

private fun UserRecord.legacyTagsForGroup(groupId: String): List<String> =
    when (groupId) {
        "healthy" -> healthyTags
        "unhealthy" -> unhealthyTags
        "symptoms" -> (symptomTags + sideEffectTags).distinct()
        "other_medications" -> otherMedicationTags
        "custom" -> customTags
        else -> emptyList()
    }

private fun parseEventText(
    text: String,
    definition: EventInputDefinition
): Triple<String, Double?, String> {
    val match = Regex("""^(.*?)(?:\s+(\d+(?:\.\d+)?))?\s*([A-Za-zµ]+)?$""")
        .matchEntire(text.trim())
    val name = match?.groupValues?.get(1)?.trim().orEmpty().ifBlank { definition.defaultName }
    val dose = match?.groupValues?.get(2)?.toDoubleOrNull() ?: definition.defaultAmount
    val unit = match?.groupValues?.get(3)?.ifBlank { definition.defaultUnit.orEmpty() } ?: definition.defaultUnit.orEmpty()
    return Triple(name, dose, unit)
}

@Composable
private fun EventInputDefinition.localizedEventLabel(): String = localizedResource(labelKey, label)

@Composable
private fun EventStatusDefinition.localizedEventStatusLabel(): String = localizedResource(labelKey, label)

private fun String?.toMedicationStatus(): MedicationStatus =
    when {
        this == null -> MedicationStatus.NOT_RECORDED
        equals("taken", ignoreCase = true) || equals("TAKEN", ignoreCase = true) -> MedicationStatus.TAKEN
        equals("missed", ignoreCase = true) || equals("MISSED", ignoreCase = true) -> MedicationStatus.MISSED
        else -> MedicationStatus.NOT_RECORDED
    }

private fun List<RecordDimension>.labelsFor(group: String): List<String> =
    filter { it.group == group }.map { it.label }

private fun String.toDimensionKey(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

private fun UserRecord.historySubtitle(
    formatter: DateTimeFormatter,
    eventInputs: List<EventInputDefinition>
): String {
    val event = events.firstOrNull { existing -> eventInputs.any { it.key == existing.key } }
        ?: events.firstOrNull()
    val eventText = event?.let {
        listOfNotNull(
            it.status.takeIf(String::isNotBlank),
            it.name.takeIf(String::isNotBlank),
            it.amount?.toInt()?.toString(),
            it.unit
        ).joinToString(" ")
    }
    return listOfNotNull(formatter.format(timestamp), eventText).joinToString(" · ")
}

private data class DisplayTag(
    val id: String,
    val label: String,
    val color: Color,
    val containerColor: Color
)
private data class DisplayTagGroup(val id: String, val tags: List<DisplayTag>)

@Composable
private fun UserRecord.displayTagGroups(groups: List<TagGroupDefinition>): List<DisplayTagGroup> {
    val manual = groups.mapNotNull { group ->
        val palette = LocalProgramVisuals.current.tagPalettes[group.colorRole]
        val tags = dimensionLabelsForGroup(group.id)
            .filterNot { it.startsWith("context.") }
            .map {
                DisplayTag(
                    it,
                    it.localize(),
                    group.configColor(),
                    palette?.containerHex?.toColor() ?: group.configColor().copy(alpha = 0.06f)
                )
            }
        tags.takeIf { it.isNotEmpty() }?.let { DisplayTagGroup(group.id, it) }
    }
    val automatic = contextDimensionLabels()
        .distinct()
        .map {
            DisplayTag(
                it,
                it.localizeContextTag(),
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant
            )
        }
        .takeIf { it.isNotEmpty() }
        ?.let { listOf(DisplayTagGroup("automatic_context", it)) }
        .orEmpty()
    return (manual + automatic).filter { it.tags.isNotEmpty() }
}

private fun UserRecord.metricValue(metricId: String): Int? =
    measurements.firstOrNull { it.key == metricId }?.value?.toInt()

private enum class HistoryMetricStatus { Normal, Caution, Danger }

private fun historyMetricStatus(
    record: UserRecord,
    metrics: List<MetricComponent>
): HistoryMetricStatus {
    val statuses = metrics.mapNotNull { metric ->
        record.metricValue(metric.id)?.let { metricStatus(metric, it) }
    }
    return when {
        HistoryMetricStatus.Danger in statuses -> HistoryMetricStatus.Danger
        HistoryMetricStatus.Caution in statuses -> HistoryMetricStatus.Caution
        else -> HistoryMetricStatus.Normal
    }
}

private fun metricStatus(metric: MetricComponent, value: Int): HistoryMetricStatus = when {
    metric.normalRange?.contains(value) == true -> HistoryMetricStatus.Normal
    metric.cautionRange?.contains(value) == true -> HistoryMetricStatus.Caution
    else -> HistoryMetricStatus.Danger
}

@Composable
private fun HistoryMetricStatus.accent(): Color =
    LocalProgramVisuals.current.severityPalette.getValue(severityLevel()).contentHex.toColor()

@Composable
private fun HistoryMetricStatus.outlineColor(): Color =
    LocalProgramVisuals.current.severityPalette.getValue(severityLevel()).outlineHex.toColor()

private fun HistoryMetricStatus.severityLevel(): SeverityLevel = when (this) {
    HistoryMetricStatus.Normal -> SeverityLevel.Normal
    HistoryMetricStatus.Caution -> SeverityLevel.Elevated
    HistoryMetricStatus.Danger -> SeverityLevel.Critical
}

