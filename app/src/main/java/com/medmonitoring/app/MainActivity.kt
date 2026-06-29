package com.medmonitoring.app

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import com.medmonitoring.app.ui.MedViewModel
import com.medmonitoring.app.ui.RecordInputState
import com.medmonitoring.app.ui.localize
import com.medmonitoring.app.ui.localizeContextTag
import com.medmonitoring.app.ui.localizedDisplayName
import com.medmonitoring.app.ui.localizedLabel
import com.medmonitoring.app.ui.localizedResource
import com.medmonitoring.app.ui.localizedTag
import com.medmonitoring.app.ui.localizedTitle
import com.medmonitoring.core.config.ConfigAudit
import com.medmonitoring.core.config.ConfigStrictMode
import com.medmonitoring.core.domain.model.*
import com.medmonitoring.core.reminders.scheduleAlarm
import com.medmonitoring.core.storage.entity.ReminderEntity
import com.medmonitoring.core.ui.components.GraphWidget
import com.medmonitoring.core.ui.theme.ProgramTheme
import com.medmonitoring.core.ui.theme.LocalProgramVisuals
import com.medmonitoring.core.ui.theme.toColor
import com.medmonitoring.core.premium.AppFeature
import com.medmonitoring.core.premium.PremiumPolicy
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val chipGap = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val sectionGap = 20.dp
}


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MedViewModel by viewModels()
    private val openAiChatRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAiChatIfNeeded(intent)
        val background = viewModel.program.visualConfig.theme.lightColors.surfaceVariant
            .removePrefix("#")
            .toLong(16)
            .let { (0xFF000000 or it).toInt() }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(background, background),
            navigationBarStyle = SystemBarStyle.light(background, background)
        )
        ConfigStrictMode.isDebug = BuildConfig.DEBUG
        val auditErrors = ConfigAudit.validate(viewModel.program, viewModel.uiDefinition)
        if (BuildConfig.DEBUG && auditErrors.isNotEmpty()) error(auditErrors.joinToString("\n"))
        setContent {
            ProgramTheme(viewModel.program.visualConfig) {
                MonitoringApp(viewModel, openAiChatRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestAiChatIfNeeded(intent)
    }

    private fun requestAiChatIfNeeded(intent: Intent?) {
        if (intent?.opensAiChat() == true) {
            openAiChatRequests.value = openAiChatRequests.value + 1
        }
    }

    private fun Intent.opensAiChat(): Boolean {
        val explicitDestination = getStringExtra(EXTRA_OPEN_DESTINATION) == DESTINATION_AI_CHAT
        val deepLinkDestination = data == Uri.parse(AI_CHAT_DEEP_LINK)
        return explicitDestination || deepLinkDestination
    }

    companion object {
        const val EXTRA_OPEN_DESTINATION = "com.medmonitoring.app.OPEN_DESTINATION"
        const val DESTINATION_AI_CHAT = "ai_chat"
        const val AI_CHAT_DEEP_LINK = "medmonitoring://ai-chat"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitoringApp(
    viewModel: MedViewModel,
    openAiChatRequests: StateFlow<Int>
) {
    var tab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    val aiSettings by viewModel.aiSettings.collectAsState()
    val premiumStatus by viewModel.premiumRepository.status.collectAsState()
    val aiAvailable = aiSettings.enabled && PremiumPolicy.hasAccess(AppFeature.AI_ASSISTANT, premiumStatus)
    val openAiChatRequest by openAiChatRequests.collectAsState()
    val visuals = LocalProgramVisuals.current
    val context = LocalContext.current
    val appIconId = remember(visuals.components.appBar.iconResourceName) {
        context.resources.getIdentifier(
            visuals.components.appBar.iconResourceName,
            "drawable",
            context.packageName
        )
    }
    LaunchedEffect(aiAvailable) {
        if (!aiAvailable && tab == 2) tab = 0
    }
    LaunchedEffect(openAiChatRequest, aiAvailable) {
        if (openAiChatRequest > 0 && aiAvailable) tab = 2
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (appIconId != 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(visuals.components.appBar.iconRadiusDp.dp)
                            ) {
                                Icon(
                                    painter = painterResource(appIconId),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = viewModel.program.localizedDisplayName(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            , colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ))
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    modifier = Modifier.height(visuals.components.tabs.heightDp.dp),
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.AddCircle, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.tab_record), style = MaterialTheme.typography.labelMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    modifier = Modifier.height(visuals.components.tabs.heightDp.dp),
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.BarChart, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.tab_statistics), style = MaterialTheme.typography.labelMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                )
                Tab(
                    selected = tab == 2,
                    onClick = { if (aiAvailable) tab = 2 },
                    enabled = aiAvailable,
                    modifier = Modifier.height(visuals.components.tabs.heightDp.dp),
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp))
                            Text("AI", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                )
            }
            when (tab) {
                0 -> RecordScreen(viewModel)
                1 -> StatisticsScreen(viewModel)
                else -> AiInsightsScreen(viewModel)
            }
        }
    }
    if (showSettings) SettingsSheet(
        viewModel = viewModel,
        onDismiss = { showSettings = false },
        onOpenAi = { configure ->
            if (aiAvailable) {
                viewModel.openAiAssistant(configure)
                tab = 2
            }
            showSettings = false
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordScreen(viewModel: MedViewModel) {
    val records by viewModel.records.collectAsState()
    val collectionState by viewModel.collectionSettings.state.collectAsState()
    val customTags by viewModel.customTags.collectAsState()
    val unitPreferences by viewModel.unitPreferences.state.collectAsState()
    val unitContext = UnitContext(viewModel.program, viewModel.uiDefinition, unitPreferences, viewModel::selectUnit)
    val state = viewModel.input.value
    val context = LocalContext.current
    val recordSavedMessage = stringResource(R.string.record_saved)
    val recordSaveFailedMessage = stringResource(R.string.record_save_failed)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (ConfigStrictMode.assertRecordWidgetAllowed(viewModel.uiDefinition, WidgetType.SaveButtonWidget)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.saveRecord { result ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (result.isSuccess) recordSavedMessage
                                    else result.exceptionOrNull()?.message ?: recordSaveFailedMessage
                                )
                            }
                        }
                    },
                    text = { Text(state.saveLabel(viewModel.program.saveActionDefinition, unitContext)) },
                    icon = {}
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        ProgramRecordForm(
            modifier = Modifier.padding(padding),
            state = state,
            program = viewModel.program,
            uiDefinition = viewModel.uiDefinition,
            records = records,
            showGraphEmptyState = !collectionState.hasEverHadRecords,
            selectedTags = viewModel.selectedTags,
            otherInputs = viewModel.otherInputs,
            customTags = customTags.map { it.groupId to it.label },
            timestampLabel = viewModel.formattedTimestamp(),
            onTimestampChange = viewModel::setTimestamp,
            onEventStatusChange = viewModel::setEventStatus,
            onEventTextChange = viewModel::setEventText,
            onMetricChange = viewModel::setMetricValue,
            onToggleTag = viewModel::toggleTag,
            onSetOtherInput = viewModel::setOtherInput,
            onConfirmOtherTag = viewModel::confirmOtherTag,
            onNoteChange = viewModel::setNote,
            unitPreferences = viewModel.unitPreferences.state.collectAsState().value,
            onSelectUnit = viewModel::selectUnit,
            includeGraph = true,
            bottomSpacer = 96.dp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramRecordForm(
    state: RecordInputState,
    program: UniversalProgramDefinition,
    uiDefinition: ProgramUiDefinition,
    selectedTags: Map<String, Set<String>>,
    otherInputs: Map<String, String>,
    customTags: List<Pair<String, String>>,
    timestampLabel: String,
    onTimestampChange: (Instant) -> Unit,
    onEventStatusChange: (String, String) -> Unit,
    onEventTextChange: (String, String) -> Unit,
    onMetricChange: (String, Double) -> Unit,
    onToggleTag: (String, String) -> Unit,
    onSetOtherInput: (String, String) -> Unit,
    onConfirmOtherTag: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    unitPreferences: Map<String, String> = emptyMap(),
    onSelectUnit: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    records: List<UserRecord>? = null,
    showGraphEmptyState: Boolean = false,
    includeGraph: Boolean = true,
    bottomSpacer: Dp = AppSpacing.lg,
    fillAvailable: Boolean = true,
    scrollable: Boolean = true
) {
    val visuals = LocalProgramVisuals.current
    val formModifier = if (fillAvailable) {
        modifier.fillMaxSize()
    } else {
        modifier.fillMaxWidth()
    }
    val contentModifier = if (scrollable) {
        formModifier.verticalScroll(rememberScrollState())
    } else {
        formModifier
    }
    Column(
        contentModifier
            .padding(vertical = AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(visuals.spacing.sectionGapDp.dp)
    ) {
        uiDefinition.recordBlocks.filterNot { it.type == WidgetType.SaveButtonWidget }.forEach { block ->
            if (!ConfigStrictMode.assertRecordWidgetAllowed(uiDefinition, block.type)) return@forEach
            when (block.type) {
                WidgetType.GraphWidget -> if (includeGraph) {
                    GraphWidget(
                        records = records,
                        definition = program.graphDefinition,
                        showEmptyState = showGraphEmptyState,
                        program = program,
                        ui = uiDefinition,
                        unitPreferences = unitPreferences
                    )
                }
                WidgetType.DateTimeWidget -> PaddedRecordBlock {
                    DateTimeRow(state.timestamp, timestampLabel, onTimestampChange)
                }
                WidgetType.EventStatusWidget -> PaddedRecordBlock {
                    EventStatusBlock(program.eventInputs.forBlock(block), state, onEventStatusChange, onEventTextChange)
                }
                WidgetType.EventAmountWidget -> Unit
                WidgetType.PairedVerticalWheelInputWidget -> PairedVerticalWheelInputBlock(
                    state,
                    program.metricComponents.forBlock(block),
                    onMetricChange,
                    UnitContext(program, uiDefinition, unitPreferences, onSelectUnit)
                )
                WidgetType.SingleHorizontalWheelInputWidget -> SingleHorizontalWheelInputBlock(
                    state,
                    program.metricComponents.forBlock(block),
                    onMetricChange,
                    UnitContext(program, uiDefinition, unitPreferences, onSelectUnit)
                )
                WidgetType.ScaleSliderInputWidget -> ScaleSliderInputBlock(
                    state,
                    program.metricComponents.forBlock(block),
                    onMetricChange,
                    UnitContext(program, uiDefinition, unitPreferences, onSelectUnit)
                )
                WidgetType.ComputedMetricInputWidget -> ComputedMetricInputBlock(
                    state,
                    program.metricComponents,
                    block.configId,
                    onMetricChange,
                    UnitContext(program, uiDefinition, unitPreferences, onSelectUnit)
                )
                WidgetType.TagGroupsWidget -> PaddedRecordBlock {
                    TagGroupsBlock(program.tagGroups, selectedTags, otherInputs, customTags, onToggleTag, onSetOtherInput, onConfirmOtherTag)
                }
                WidgetType.NoteWidget -> PaddedRecordBlock { NoteBlock(state, onNoteChange) }
                else -> Unit
            }
        }
        Spacer(Modifier.height(bottomSpacer))
    }
}

@Composable
private fun PaddedRecordBlock(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = AppSpacing.md)) { content() }
}

@Composable
private fun RecordInputState.saveLabel(definition: SaveActionDefinition, units: UnitContext): String {
    val action = stringResource(R.string.save)
    if (metricValues.isEmpty()) {
        return definition.fallbackText
            .replace("{action}", action)
            .replace("SAVE", action)
    }
    // Render values and units from the global unit state — never hardcode units in the pattern.
    // Patterns use {metricId} for the display value and {unit:metricId} for the selected unit label.
    var text = definition.previewPattern
    units.program.metricComponents.forEach { metric ->
        val canonical = metricValues[metric.id]
        val display = units.display(metric, canonical ?: 0.0)
        text = text.replace("{unit:${metric.id}}", display.unit)
        if (canonical != null) {
            text = text.replace("{${metric.id}}", units.display(metric, canonical).valueText)
        }
    }
    return text
        .replace("{action}", action)
        .replace("SAVE", action)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(
    timestamp: Instant,
    timestampLabel: String,
    onTimestampChange: (Instant) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }
    val zoneId = ZoneId.systemDefault()
    val current = timestamp.atZone(zoneId)
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = current.toInstant().toEpochMilli())
    val timePickerState = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
    Section {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.record_date_format, timestampLabel),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.edit_date_time),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis ?: current.toInstant().toEpochMilli()
                    pendingDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
                    showDatePicker = false
                    showTimePicker = true
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = pendingDate ?: current.toLocalDate()
                    val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    onTimestampChange(ZonedDateTime.of(date, time, zoneId).toInstant())
                    showTimePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventStatusBlock(
    definition: EventInputDefinition?,
    state: RecordInputState,
    onStatusChange: (String, String) -> Unit,
    onEventTextChange: (String, String) -> Unit
) {
    definition ?: return
    var showEditor by remember { mutableStateOf(false) }
    val eventText = state.eventText(definition.key)
    val currentStatus = state.eventStatus(definition.key)
    Section {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEditor = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${definition.localizedEventLabel()}: $eventText".uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.Edit,
                    contentDescription = definition.localizedEventLabel(),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                definition.statuses.forEach { status ->
                    EventStatusToggle(
                        label = status.localizedEventStatusLabel().uppercase(),
                        selected = currentStatus.equals(status.status, ignoreCase = true),
                        positive = status.positive,
                        onClick = { onStatusChange(definition.key, status.status) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
    if (showEditor) {
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            delay(250)
            focusRequester.requestFocus()
            delay(100)
            keyboardController?.show()
        }
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(definition.localizedEventLabel()) },
            text = {
                OutlinedTextField(
                    value = eventText,
                    onValueChange = { onEventTextChange(definition.key, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    shape = MaterialTheme.shapes.small,
                    singleLine = true
                )
            },
            confirmButton = { TextButton(onClick = { showEditor = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun EventStatusToggle(
    label: String,
    selected: Boolean,
    positive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalProgramVisuals.current
    val semantic = visuals.severityPalette.getValue(
        if (positive) SeverityLevel.Normal else SeverityLevel.Critical
    )
    val selectedBackground = semantic.containerHex.toColor()
    val selectedBorder = semantic.outlineHex.toColor()
    val selectedContent = semantic.contentHex.toColor()
    val borderColor = if (selected) selectedBorder else MaterialTheme.colorScheme.outline
    val contentColor = if (selected) selectedContent else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(visuals.shapes.buttonRadiusDp.dp),
        color = if (selected) selectedBackground else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    if (positive) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun List<EventInputDefinition>.forBlock(block: RecordBlockDefinition): EventInputDefinition? {
    val id = block.configId.takeIf { it != block.type.name } ?: firstOrNull()?.key
    return firstOrNull { it.key == id } ?: firstOrNull()
}

/**
 * Carries the global unit selection into input blocks. Values are canonical; this converts them to
 * the user's selected display unit and back, and exposes the unit toggle as the single control point.
 */
internal data class UnitContext(
    val program: UniversalProgramDefinition,
    val ui: ProgramUiDefinition,
    val preferences: Map<String, String>,
    val onSelectUnit: (String, String) -> Unit
) {
    fun display(metric: MetricComponent, canonical: Double): DisplayMetricValue =
        MetricUnitFormatter.displayValue(metric, canonical, program, ui, preferences)

    fun configuredUnitModes(metricId: String): List<InputUnitMode> =
        with(MetricUnitFormatter) { ui.inputConfigForMetric(metricId).unitModes }

    fun selectedUnitId(metricId: String): String = preferences[metricId].orEmpty()

    /** Steps the canonical value by [steps] display-steps and clamps to the metric's canonical range. */
    fun step(metric: MetricComponent, canonical: Double, steps: Int): Double {
        val current = display(metric, canonical)
        return fromDisplay(metric, current.displayValue + steps * current.step)
    }

    fun fromDisplay(metric: MetricComponent, displayValue: Double): Double =
        MetricUnitFormatter.fromDisplay(metric.id, displayValue, program, ui, preferences)
            .coerceIn(metric.inputRange.first.toDouble(), metric.inputRange.last.toDouble())
}

internal fun Double.formatMetric(decimalPlaces: Int): String =
    if (decimalPlaces <= 0) roundToInt().toString() else String.format(Locale.US, "%.${decimalPlaces}f", this)

@Composable
private fun UnitToggle(metricId: String, units: UnitContext) {
    val modes = units.configuredUnitModes(metricId)
    if (modes.size < 2) return
    val selectedId = units.selectedUnitId(metricId)
    Row(
        Modifier.fillMaxWidth().padding(top = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        modes.forEachIndexed { index, mode ->
            val selected = mode.id == selectedId || (selectedId.isBlank() && index == 0)
            FilterChip(
                selected = selected,
                onClick = { units.onSelectUnit(metricId, mode.id) },
                label = { Text(mode.label) }
            )
        }
    }
}

@Composable
internal fun PairedVerticalWheelInputBlock(
    state: RecordInputState,
    components: List<MetricComponent>,
    onChange: (String, Double) -> Unit,
    units: UnitContext
) {
    val first = components.getOrNull(0) ?: return
    val second = components.getOrNull(1) ?: return
    val firstValue = state.metricValue(first.id) ?: first.defaultValue?.toDouble()
    val secondValue = state.metricValue(second.id) ?: second.defaultValue?.toDouble()
    val title = "${first.localizedLabel()} / ${second.localizedLabel()}"
    MeasurementSection(
        title,
        combinedMetricStatus(first.metricStatus(firstValue), second.metricStatus(secondValue))
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            MetricWheelPicker(first, firstValue, units, { onChange(first.id, it) }, Modifier.weight(1f))
            MetricWheelPicker(second, secondValue, units, { onChange(second.id, it) }, Modifier.weight(1f))
        }
        UnitToggle(first.id, units)
        UnitToggle(second.id, units)
    }
}

@Composable
internal fun SingleHorizontalWheelInputBlock(
    state: RecordInputState,
    components: List<MetricComponent>,
    onChange: (String, Double) -> Unit,
    units: UnitContext
) {
    val metric = components.firstOrNull() ?: return
    val metricValue = state.metricValue(metric.id) ?: metric.defaultValue?.toDouble()
        ?: metric.inputRange.first.toDouble()
    MeasurementSection(metric.localizedLabel(), metric.metricStatus(metricValue)) {
        HorizontalMetricWheelPicker(metric, metricValue, units, { onChange(metric.id, it) }, Modifier.fillMaxWidth())
        UnitToggle(metric.id, units)
    }
}

private fun List<MetricComponent>.forBlock(block: RecordBlockDefinition): List<MetricComponent> {
    val ids = block.configId
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() && it != block.type.name }
    return ids.mapNotNull { id -> firstOrNull { it.id == id } }
}

@Composable
internal fun ScaleSliderInputBlock(
    state: RecordInputState,
    components: List<MetricComponent>,
    onChange: (String, Double) -> Unit,
    @Suppress("UNUSED_PARAMETER") units: UnitContext
) {
    val metric = components.firstOrNull() ?: return
    val range = metric.inputRange
    val current = (state.metricValue(metric.id)?.roundToInt() ?: metric.defaultValue ?: range.first)
        .coerceIn(range)
    val status = metric.metricStatus(current.toDouble())
    val accent = status.accent()
    val steps = (range.last - range.first - 1).coerceAtLeast(0)
    MeasurementSection(metric.localizedLabel(), status) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Text(
                current.toString(),
                color = accent,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.widthIn(min = 48.dp),
                textAlign = TextAlign.Center
            )
            Slider(
                value = current.toFloat(),
                onValueChange = { onChange(metric.id, it.roundToInt().coerceIn(range).toDouble()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    activeTickColor = accent.copy(alpha = 0.4f),
                    inactiveTickColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                "${range.last}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/**
 * Config-driven complex input: a tracked source metric (e.g. weight) is entered via a wheel, a
 * derived metric (e.g. BMI) is auto-calculated and shown read-only, and supporting sources (e.g.
 * height) are shown with an edit affordance. Falls back to a plain wheel when the metric has no
 * computed configuration, so simpler programs need no special handling.
 */
@Composable
internal fun ComputedMetricInputBlock(
    state: RecordInputState,
    allMetrics: List<MetricComponent>,
    computedMetricId: String,
    onChange: (String, Double) -> Unit,
    units: UnitContext
) {
    val computed = allMetrics.firstOrNull { it.id == computedMetricId } ?: return
    val definition = computed.computedFrom
    val sources = definition?.sourceMetricIds.orEmpty().mapNotNull { id -> allMetrics.firstOrNull { it.id == id } }
    if (definition == null || sources.isEmpty()) {
        // Graceful degradation: behave like a standard single-metric wheel.
        SingleHorizontalWheelInputBlock(state, listOf(computed), onChange, units)
        return
    }
    val trackedSource = sources.firstOrNull { it.isTracked }
    val supportingSources = sources.filter { !it.isTracked }
    val sourceValues = sources.associate { metric ->
        metric.id to (state.metricValue(metric.id) ?: metric.defaultValue?.toDouble() ?: metric.inputRange.first.toDouble())
    }
    val computedValue = ComputedMetricCalculator.compute(definition, sourceValues)
    val status = computed.metricStatus(computedValue)
    MeasurementSection(computed.localizedLabel(), status) {
        trackedSource?.let { source ->
            HorizontalMetricWheelPicker(
                source, state.metricValue(source.id), units, { onChange(source.id, it) }, Modifier.fillMaxWidth()
            )
            UnitToggle(source.id, units)
        }
        Spacer(Modifier.height(AppSpacing.sm))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                "${computed.localizedLabel()}: ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                computedValue?.formatMetric(1) ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = status.accent()
            )
            Text(
                " ${computed.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        supportingSources.forEach { source ->
            SupportingMetricRow(source, state.metricValue(source.id), units) { onChange(source.id, it) }
        }
    }
}

@Composable
private fun SupportingMetricRow(
    metric: MetricComponent,
    value: Double?,
    units: UnitContext,
    onChange: (Double) -> Unit
) {
    val canonical = value ?: metric.defaultValue?.toDouble() ?: metric.inputRange.first.toDouble()
    val display = units.display(metric, canonical)
    var showEditor by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(top = AppSpacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${metric.localizedLabel()}: ${display.valueText} ${display.unit}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = { showEditor = true }, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Edit,
                contentDescription = metric.localizedLabel(),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    UnitToggle(metric.id, units)
    if (showEditor) {
        MetricValueEditor(
            definition = metric,
            canonical = canonical,
            units = units,
            onDismiss = { showEditor = false },
            onConfirm = {
                onChange(it)
                showEditor = false
            }
        )
    }
}

private enum class MetricStatus { Normal, Caution, Danger }

private fun MetricComponent.metricStatus(value: Double?): MetricStatus {
    val intValue = value?.roundToInt()
    return metricStatusInt(intValue)
}

private fun MetricComponent.metricStatusInt(value: Int?): MetricStatus = when {
    value == null || normalRange?.contains(value) == true -> MetricStatus.Normal
    cautionRange?.contains(value) == true -> MetricStatus.Caution
    else -> MetricStatus.Danger
}

private fun combinedMetricStatus(vararg statuses: MetricStatus): MetricStatus = when {
    MetricStatus.Danger in statuses -> MetricStatus.Danger
    MetricStatus.Caution in statuses -> MetricStatus.Caution
    else -> MetricStatus.Normal
}

@Composable
private fun MeasurementSection(
    title: String,
    status: MetricStatus,
    content: @Composable ColumnScope.() -> Unit
) {
    val visuals = LocalProgramVisuals.current
    val accent = status.accent()
    val surface = status.containerColor()
    val outline = status.outlineColor()
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(title.uppercase(), color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Column(
            Modifier.fillMaxWidth()
                .background(surface, MaterialTheme.shapes.medium)
                .border(visuals.shapes.cardBorderWidthDp.dp, outline, MaterialTheme.shapes.medium)
                .padding(visuals.spacing.cardPaddingDp.dp),
            content = content
        )
    }
}

@Composable
private fun MetricWheelPicker(
    definition: MetricComponent,
    value: Double?,
    units: UnitContext,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val lo = definition.inputRange.first.toDouble()
    val hi = definition.inputRange.last.toDouble()
    val canonical = (value ?: lo).coerceIn(lo, hi)
    val display = units.display(definition, canonical)
    val up = units.step(definition, canonical, 1)
    val down = units.step(definition, canonical, -1)
    val accent = definition.metricStatus(canonical).accent()
    var showEditor by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            definition.localizedLabel().uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(17.dp))
        Text(
            units.display(definition, up).valueText,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.clickable { onChange(up) }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            display.valueText,
            color = accent,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showEditor = true },
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(Modifier.width(84.dp), color = accent.copy(alpha = 0.5f))
        Spacer(Modifier.height(4.dp))
        Text(
            units.display(definition, down).valueText,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.clickable { onChange(down) }
        )
        Spacer(Modifier.height(9.dp))
        Text(
            display.unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (showEditor) {
        MetricValueEditor(
            definition = definition,
            canonical = canonical,
            units = units,
            onDismiss = { showEditor = false },
            onConfirm = {
                onChange(it)
                showEditor = false
            }
        )
    }
}

@Composable
private fun HorizontalMetricWheelPicker(
    definition: MetricComponent,
    value: Double?,
    units: UnitContext,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val lo = definition.inputRange.first.toDouble()
    val hi = definition.inputRange.last.toDouble()
    val canonical = (value ?: lo).coerceIn(lo, hi)
    val display = units.display(definition, canonical)
    val accent = definition.metricStatus(canonical).accent()
    var showEditor by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            definition.localizedLabel().uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppSpacing.lg))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            (-2..2).forEach { offset ->
                val itemCanonical = if (offset == 0) canonical else units.step(definition, canonical, offset)
                val itemText = if (offset == 0) display.valueText else units.display(definition, itemCanonical).valueText
                Text(
                    itemText,
                    color = if (offset == 0) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    style = if (offset == 0) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineSmall,
                    fontWeight = if (offset == 0) FontWeight.ExtraBold else FontWeight.Normal,
                    modifier = Modifier.clickable {
                        if (offset == 0) showEditor = true else onChange(itemCanonical)
                    }
                )
            }
        }
        HorizontalDivider(Modifier.width(96.dp), color = accent.copy(alpha = 0.5f))
        Text(
            display.unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (showEditor) {
        MetricValueEditor(
            definition = definition,
            canonical = canonical,
            units = units,
            onDismiss = { showEditor = false },
            onConfirm = {
                onChange(it)
                showEditor = false
            }
        )
    }
}

@Composable
private fun MetricValueEditor(
    definition: MetricComponent,
    canonical: Double,
    units: UnitContext,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val display = units.display(definition, canonical)
    val bound1 = MetricUnitFormatter.toDisplay(definition.id, definition.inputRange.first.toDouble(), units.program, units.ui, units.preferences)
    val bound2 = MetricUnitFormatter.toDisplay(definition.id, definition.inputRange.last.toDouble(), units.program, units.ui, units.preferences)
    val lo = minOf(bound1, bound2)
    val hi = maxOf(bound1, bound2)
    var text by remember(display.unitModeId, display.valueText) { mutableStateOf(display.valueText) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val parsed = text.replace(',', '.').toDoubleOrNull()
    val valid = parsed != null && parsed >= lo && parsed <= hi
    LaunchedEffect(Unit) {
        delay(250)
        focusRequester.requestFocus()
        delay(100)
        keyboardController?.show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(definition.localizedLabel()) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() || it == '.' || it == ',' } },
                modifier = Modifier.focusRequester(focusRequester),
                label = { Text(display.unit) },
                supportingText = { Text("${lo.formatMetric(display.decimalPlaces)}–${hi.formatMetric(display.decimalPlaces)} ${display.unit}") },
                isError = text.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (display.decimalPlaces > 0) KeyboardType.Decimal else KeyboardType.Number
                ),
                shape = MaterialTheme.shapes.small,
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { parsed?.let { onConfirm(units.fromDisplay(definition, it)) } }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun MetricStatus.accent(): Color =
    LocalProgramVisuals.current.severityPalette.getValue(severityLevel()).contentHex.toColor()

@Composable
private fun MetricStatus.outlineColor(): Color =
    LocalProgramVisuals.current.severityPalette.getValue(severityLevel()).outlineHex.toColor()

@Composable
private fun MetricStatus.containerColor(): Color {
    val semantic = LocalProgramVisuals.current.severityPalette.getValue(sectionSeverityLevel())
    val accent = semantic.contentHex.toColor()
    val lightContainer = semantic.containerHex.toColor()
    return if (isSystemInDarkTheme()) {
        lerp(MaterialTheme.colorScheme.surface, accent, 0.22f)
    } else {
        lightContainer
    }
}

private fun MetricStatus.severityLevel(): SeverityLevel = when (this) {
    MetricStatus.Normal -> SeverityLevel.Normal
    MetricStatus.Caution -> SeverityLevel.Elevated
    MetricStatus.Danger -> SeverityLevel.Critical
}

private fun MetricStatus.sectionSeverityLevel(): SeverityLevel = when (this) {
    MetricStatus.Normal -> SeverityLevel.Normal
    MetricStatus.Caution -> SeverityLevel.Neutral
    MetricStatus.Danger -> SeverityLevel.Critical
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroupsBlock(
    tagGroups: List<TagGroupDefinition>,
    selectedTags: Map<String, Set<String>>,
    otherInputs: Map<String, String>,
    customTags: List<Pair<String, String>>,
    onToggleTag: (String, String) -> Unit,
    onSetOtherInput: (String, String) -> Unit,
    onConfirmOtherTag: (String) -> Unit
) {
    // TagGroupsBlock is hosted by PaddedRecordBlock's Box. Its groups therefore must be
    // explicitly stacked; otherwise Box places every emitted group at the same origin.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
    tagGroups.forEach { group ->
        val selected = selectedTags[group.id].orEmpty()
        val extra = customTags.filter { it.first == group.id }.map { it.second }
        val tags = (group.tags + extra).distinct()
        val color = group.configColor()
        val groupTitle = group.localizedTitle()
        Section(title = groupTitle.uppercase(), titleColor = color) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.chipGap),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.chipGap)
                ) {
                    tags.forEach { tag ->
                        SelectableTagChip(
                            label = group.localizedTag(tag),
                            color = color,
                            selected = selected.contains(tag),
                            onClick = { onToggleTag(group.id, tag) }
                        )
                    }
                    if (group.otherEnabled) {
                        SelectableTagChip(
                            label = stringResource(R.string.other),
                            color = color,
                            selected = false,
                            onClick = { onSetOtherInput(group.id, otherInputs[group.id] ?: " ") }
                        )
                    }
                }
                if (otherInputs.containsKey(group.id)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = otherInputs[group.id].orEmpty(),
                            onValueChange = { onSetOtherInput(group.id, it) },
                            label = { Text(stringResource(R.string.custom_tag, groupTitle.lowercase())) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            singleLine = true
                        )
                        FilledTonalButton(onClick = { onConfirmOtherTag(group.id) }) { Text(stringResource(R.string.add)) }
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun NoteBlock(state: RecordInputState, onChange: (String) -> Unit) {
    Section(title = stringResource(R.string.note)) {
        OutlinedTextField(
            value = state.note,
            onValueChange = onChange,
            placeholder = { Text(stringResource(R.string.add_note)) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            minLines = 2
        )
    }
}

@Composable
internal fun Section(
    title: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        if (title != null) {
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        content()
    }
}

@Composable
internal fun MedicationStatus.localizedLabel(): String {
    return when (this) {
        MedicationStatus.TAKEN -> stringResource(R.string.taken)
        MedicationStatus.MISSED -> stringResource(R.string.missed)
        MedicationStatus.NOT_RECORDED -> "-"
    }
}

@Composable
private fun EventInputDefinition.localizedEventLabel(): String = localizedResource(labelKey, label)

@Composable
private fun EventStatusDefinition.localizedEventStatusLabel(): String = localizedResource(labelKey, label)

@Composable
internal fun TagGroupDefinition.configColor(): Color {
    return LocalProgramVisuals.current.tagPalettes[colorRole]?.contentHex?.toColor()
        ?: colorHex.toComposeColor()
        ?: MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun String.toComposeColor(): Color? {
    val hex = removePrefix("#")
    val value = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> null
    }
}

@Composable
internal fun CompactTagChip(
    label: String,
    color: Color,
    containerColor: Color = color.copy(alpha = 0.06f)
) {
    val visuals = LocalProgramVisuals.current
    val labelColor = if (color.luminance() < 0.35f) color else color.copy(
        red = color.red * 0.55f,
        green = color.green * 0.55f,
        blue = color.blue * 0.55f
    )
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier
            .heightIn(min = visuals.spacing.chipHeightDp.dp)
            .border(visuals.shapes.cardBorderWidthDp.dp, color.copy(alpha = 0.31f), MaterialTheme.shapes.extraSmall)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = visuals.spacing.chipHorizontalPaddingDp.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun SelectableTagChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val visuals = LocalProgramVisuals.current
    val animationSpec = tween<Color>(
        durationMillis = visuals.components.animation.selectionDurationMs,
        easing = FastOutSlowInEasing
    )
    val selectedBase = color.copy(alpha = 0.06f)
    val base by animateColorAsState(
        if (selected) selectedBase else Color.Transparent,
        animationSpec,
        label = "tag background"
    )
    val border by animateColorAsState(
        if (selected) color.copy(alpha = 0.31f) else MaterialTheme.colorScheme.outline,
        animationSpec,
        label = "tag border"
    )
    val labelColor by animateColorAsState(
        if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec,
        label = "tag text"
    )
    Surface(
        color = base,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier
            .heightIn(min = visuals.spacing.chipHeightDp.dp)
            .border(visuals.shapes.cardBorderWidthDp.dp, border, MaterialTheme.shapes.extraSmall)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = visuals.spacing.chipHorizontalPaddingDp.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TagLine(label: String, tags: List<String>) {
    if (tags.isNotEmpty()) Text("$label: ${tags.joinToString(", ")}")
}

