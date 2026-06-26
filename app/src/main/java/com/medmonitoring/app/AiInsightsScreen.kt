package com.medmonitoring.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.app.AlarmManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import com.medmonitoring.app.ui.MedViewModel
import com.medmonitoring.app.ui.localizedResource
import com.medmonitoring.core.ai.AiGoalStatus
import com.medmonitoring.core.ai.AiMenuAction
import com.medmonitoring.core.ai.AiSliderItem
import com.medmonitoring.core.domain.model.ReminderTypeDefinition
import com.medmonitoring.core.domain.model.SeverityLevel
import com.medmonitoring.core.reminders.scheduleAlarm
import com.medmonitoring.core.storage.entity.AiChatMessageEntity
import com.medmonitoring.core.storage.entity.GoalEntity
import com.medmonitoring.core.storage.entity.ReminderEntity
import com.medmonitoring.core.ui.theme.LocalProgramVisuals
import com.medmonitoring.core.ui.theme.toColor
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun AiInsightsScreen(viewModel: MedViewModel) {
    val messages by viewModel.aiChatMessages.collectAsState()
    val programState by viewModel.aiProgramState.collectAsState()
    val checklist by viewModel.aiChecklist.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val settings by viewModel.aiSettings.collectAsState()
    val goalPanelExpanded by viewModel.goalPanelExpanded.collectAsState()
    val aiChatBusy by viewModel.aiChatBusy.collectAsState()
    val context = LocalContext.current
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val reminderTypes = viewModel.program.reminderTypes
    val defaultReminderType = reminderTypes.firstOrNull()
    val defaultReminderLabel = viewModel.program.eventInputs.firstOrNull()?.let { input ->
        localizedResource(input.labelKey, input.defaultName.ifBlank { input.label })
    } ?: defaultReminderType?.localizedLabel().orEmpty()
    val noDataTitle = stringResource(R.string.ai_no_data_title)
    val noDataBody = stringResource(R.string.ai_no_data_body)
    val todayFocusTitle = stringResource(R.string.ai_today_focus_title)
    val todayFocusBody = stringResource(R.string.ai_today_focus_body)
    val mainFocusTitle = stringResource(R.string.ai_main_focus_title)
    val mainFocusBody = stringResource(R.string.ai_main_focus_body)
    val slider = remember(programState?.sliderJson, noDataTitle, noDataBody, todayFocusTitle, todayFocusBody, mainFocusTitle, mainFocusBody) {
        programState?.sliderJson?.let { json ->
            runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(json)
                    .jsonArray
                    .map { item ->
                        val obj = item.jsonObject
                        AiSliderItem(
                            type = obj["type"]?.jsonPrimitive?.content.orEmpty(),
                            title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                            text = obj["text"]?.jsonPrimitive?.content.orEmpty()
                        )
                    }
            }.getOrNull()
        }.orEmpty().ifEmpty {
            listOf(
                AiSliderItem("progress", noDataTitle, noDataBody),
                AiSliderItem("motivation", todayFocusTitle, todayFocusBody),
                AiSliderItem("focus", mainFocusTitle, mainFocusBody)
            )
        }
    }
    var input by remember { mutableStateOf("") }
    var reminderDraft by remember { mutableStateOf<ReminderEntity?>(null) }
    val listState = rememberLazyListState()
    fun send() {
        if (input.isNotBlank() && !aiChatBusy) {
            viewModel.onSendAiMessage(input)
            input = ""
        }
    }

    if (!settings.enabled) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.ai_disabled_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.ai_disabled_body))
        }
        return
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex + if (checklist.isNotEmpty()) 1 else 0)
        }
    }

    val panelColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val visuals = LocalProgramVisuals.current
    val darkTheme = isSystemInDarkTheme()
    val programPrimary = visuals.theme.lightColors.primary.toColor()
    val userInputColor = if (darkTheme) {
        lerp(programPrimary, MaterialTheme.colorScheme.surface, 0.28f)
    } else {
        visuals.theme.lightColors.primaryContainer.toColor()
    }
    val userInputContentColor = if (darkTheme) {
        visuals.theme.lightColors.onPrimary.toColor()
    } else {
        programPrimary
    }
    val visibleGoals = remember(goals) { goals.filter { it.isVisibleInTodayPanel() } }
    val activeGoals = remember(visibleGoals) { visibleGoals.filter { it.status in checklistGoalStatuses } }
    val achievedToday = remember(activeGoals) { activeGoals.count { it.status == AiGoalStatus.ACHIEVED } }
    val recommendationCount = remember(visibleGoals) { visibleGoals.count { it.status == AiGoalStatus.RECOMMENDED } }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = panelColor) {
            Column {
                GoalPanelHeader(
                    expanded = goalPanelExpanded,
                    achieved = achievedToday,
                    total = activeGoals.size,
                    recommendations = recommendationCount,
                    onToggle = { viewModel.setGoalPanelExpanded(!goalPanelExpanded) }
                )
                if (goalPanelExpanded) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (visibleGoals.isNotEmpty()) {
                            items(visibleGoals) { goal ->
                                GoalManagementCard(
                                    goal = goal,
                                    modifier = Modifier.width(320.dp),
                                    onToggle = viewModel::onChecklistItemToggle,
                                    onDelete = viewModel::deleteAiGoal,
                                    onAccept = viewModel::acceptGoal,
                                    onReject = viewModel::rejectGoal
                                )
                            }
                        } else {
                            items(slider) { slide ->
                                StateSlide(slide)
                            }
                        }
                    }
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message ->
                ChatMessage(
                    message = message,
                    goals = goals,
                    onStartOnboarding = viewModel::startAiOnboarding,
                    onStartMenuAction = { action, messageId ->
                        viewModel.startAiMenuAction(
                            action = action,
                            messageId = messageId,
                            onOpenReminder = {
                                reminderDraft = ReminderEntity(
                                    id = UUID.randomUUID().toString(),
                                    type = defaultReminderType?.id.orEmpty(),
                                    label = defaultReminderLabel,
                                    hour = 19,
                                    minute = 30,
                                    repeat = "Daily",
                                    enabled = true
                                )
                            }
                        )
                    },
                    onDeleteMessage = viewModel::dismissAiMenuAction,
                    onAcceptGoal = viewModel::acceptGoal,
                    onRejectGoal = viewModel::rejectGoal,
                    onToggleGoal = viewModel::onChecklistItemToggle,
                    onDeleteGoal = viewModel::deleteAiGoal
                )
            }
        }
        ChatInputPanel(
                input = input,
                onInputChange = { input = it },
                onSend = { send() },
                onMenuAction = viewModel::requestAiMenuAction,
                enabled = !aiChatBusy,
                panelColor = panelColor,
                inputColor = userInputColor,
                inputContentColor = userInputContentColor
            )
    }
    reminderDraft?.let { reminder ->
        ChatReminderEditorDialog(
            initial = reminder,
            reminderTypes = reminderTypes,
            onDismiss = { reminderDraft = null },
            onSave = {
                viewModel.upsertReminder(it)
                if (it.enabled) scheduleAlarm(context, alarmManager, it)
                reminderDraft = null
            }
        )
    }
}

@Composable
private fun ChatInputPanel(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onMenuAction: (String) -> Unit,
    enabled: Boolean,
    panelColor: androidx.compose.ui.graphics.Color,
    inputColor: androidx.compose.ui.graphics.Color,
    inputContentColor: androidx.compose.ui.graphics.Color
) {
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navigationBottom = WindowInsets.navigationBars.getBottom(density)
    val imeVisible = imeBottom > 0
    val imeOffset = with(density) { (imeBottom - navigationBottom).coerceAtLeast(0).toDp() }
    val canSend = enabled && input.isNotBlank()
    Surface(
        color = panelColor,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = -imeOffset)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    top = 8.dp,
                    end = 12.dp,
                    bottom = 8.dp
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    shape = MaterialTheme.shapes.medium,
                    placeholder = { Text(stringResource(R.string.ai_type_message)) },
                    minLines = 1,
                    maxLines = 2,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = inputColor,
                        unfocusedContainerColor = inputColor,
                        disabledContainerColor = inputColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedPlaceholderColor = inputContentColor.copy(alpha = 0.72f),
                        unfocusedPlaceholderColor = inputContentColor.copy(alpha = 0.72f),
                        disabledPlaceholderColor = inputContentColor.copy(alpha = 0.52f),
                        focusedTextColor = inputContentColor,
                        unfocusedTextColor = inputContentColor,
                        disabledTextColor = inputContentColor.copy(alpha = 0.72f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() })
                )
                FilledIconButton(
                    enabled = canSend,
                    onClick = onSend,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = inputContentColor,
                        contentColor = inputColor,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.ai_send))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(enabled = enabled, onClick = { onMenuAction(AiMenuAction.BASIC_ANALYSIS) }) {
                    Text(stringResource(R.string.ai_basic_analysis))
                }
                OutlinedButton(enabled = enabled, onClick = { onMenuAction(AiMenuAction.AI_ANALYSIS) }) {
                    Text(stringResource(R.string.ai_model_analysis))
                }
                OutlinedButton(enabled = enabled, onClick = { onMenuAction(AiMenuAction.CONFIGURE_AI) }) {
                    Text(stringResource(R.string.ai_configure))
                }
                OutlinedButton(enabled = enabled, onClick = { onMenuAction(AiMenuAction.SET_REMINDER) }) {
                    Text(stringResource(R.string.ai_menu_set_reminder_short))
                }
                OutlinedButton(enabled = enabled, onClick = { onMenuAction(AiMenuAction.RECOMMEND_GOAL) }) {
                    Text(stringResource(R.string.ai_menu_recommend_goal_short))
                }
                OutlinedButton(enabled = enabled, onClick = { onMenuAction(AiMenuAction.CLEAR_HISTORY) }) {
                    Text(stringResource(R.string.ai_menu_clear_history_short))
                }
            }
        }
    }
}

@Composable
private fun StateSlide(slide: AiSliderItem) {
    val neutral = LocalProgramVisuals.current.severityPalette.getValue(SeverityLevel.Neutral)
    Card(
        modifier = Modifier.width(300.dp).height(112.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(slide.title, style = MaterialTheme.typography.titleSmall, color = neutral.contentHex.toColor(), fontWeight = FontWeight.Bold)
            Text(slide.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GoalPanelHeader(
    expanded: Boolean,
    achieved: Int,
    total: Int,
    recommendations: Int,
    onToggle: () -> Unit
) {
    val palette = LocalProgramVisuals.current.severityPalette
    val normal = palette.getValue(SeverityLevel.Normal).contentHex.toColor()
    val neutral = palette.getValue(SeverityLevel.Neutral).contentHex.toColor()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.ai_today), fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = normal)
                Text("$achieved/$total", style = MaterialTheme.typography.labelLarge)
            }
            if (recommendations > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(18.dp), tint = neutral)
                    Text(recommendations.toString(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        IconButton(onClick = onToggle, modifier = Modifier.size(44.dp)) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.ai_hide_goals_panel) else stringResource(R.string.ai_show_goals_panel)
            )
        }
    }
}

@Composable
private fun GoalsChecklistCard(
    title: String,
    goals: List<GoalEntity>,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val palette = LocalProgramVisuals.current.severityPalette
    val normal = palette.getValue(SeverityLevel.Normal).contentHex.toColor()
    val critical = palette.getValue(SeverityLevel.Critical).contentHex.toColor()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            goals.forEach { goal ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable { onToggle(goal.id) }
                        .padding(start = 2.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        goal.title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (goal.status == AiGoalStatus.ACHIEVED) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    GoalActionButton(
                        label = if (goal.status == AiGoalStatus.ACHIEVED) stringResource(R.string.ai_achieved) else stringResource(R.string.ai_achieve),
                        icon = Icons.Default.CheckCircle,
                        contentColor = if (goal.status == AiGoalStatus.ACHIEVED) {
                            normal
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        onClick = { onToggle(goal.id) }
                    )
                    GoalActionButton(
                        label = stringResource(R.string.delete),
                        icon = Icons.Default.DeleteForever,
                        contentColor = critical,
                        onClick = { onDelete(goal.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessage(
    message: AiChatMessageEntity,
    goals: List<GoalEntity>,
    onStartOnboarding: () -> Unit,
    onStartMenuAction: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onAcceptGoal: (String) -> Unit,
    onRejectGoal: (String) -> Unit,
    onToggleGoal: (String) -> Unit,
    onDeleteGoal: (String) -> Unit
) {
    val recommendationGoal = remember(message.payloadJson, goals) {
        message.recommendationGoalId()?.let { id -> goals.firstOrNull { it.id == id } }
    }
    val menuAction = remember(message.payloadJson) { message.menuAction() }
    if (message.type == "recommendation" && recommendationGoal?.isVisibleInTodayPanel() == true) {
        GoalManagementCard(
            goal = recommendationGoal,
            modifier = Modifier.fillMaxWidth(),
            onToggle = onToggleGoal,
            onDelete = onDeleteGoal,
            onAccept = onAcceptGoal,
            onReject = onRejectGoal
        )
        return
    }
    if (message.type == "recommendation" && recommendationGoal != null) return

    val isUser = message.role == "user"
    val visuals = LocalProgramVisuals.current
    val darkTheme = isSystemInDarkTheme()
    val programPrimary = visuals.theme.lightColors.primary.toColor()
    val userBubbleColor = if (darkTheme) {
        lerp(programPrimary, MaterialTheme.colorScheme.surface, 0.28f)
    } else {
        visuals.theme.lightColors.primaryContainer.toColor()
    }
    val userBubbleContentColor = if (darkTheme) {
        visuals.theme.lightColors.onPrimary.toColor()
    } else {
        programPrimary
    }
    val bubbleColor = if (isUser) userBubbleColor else Color.Transparent
    val bubbleBorder = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    val bodyColor = if (isUser) userBubbleContentColor else MaterialTheme.colorScheme.onSurface
    val labelColor = if (isUser) userBubbleContentColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            border = bubbleBorder,
            modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 0.92f)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (isUser) stringResource(R.string.ai_you) else message.type.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor
                )
                Text(message.text, style = MaterialTheme.typography.bodyMedium, color = bodyColor)
                if (message.type == "onboarding_start") {
                    Button(onClick = onStartOnboarding) {
                        Text(stringResource(R.string.ai_start_setup))
                    }
                }
                if (message.type == "menu_action" && menuAction != null) {
                    val palette = LocalProgramVisuals.current.severityPalette
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GoalActionButton(
                            label = stringResource(R.string.ai_start),
                            icon = Icons.Default.CheckCircle,
                            contentColor = palette.getValue(SeverityLevel.Normal).contentHex.toColor(),
                            onClick = { onStartMenuAction(menuAction, message.id) }
                        )
                        GoalActionButton(
                            label = stringResource(R.string.delete),
                            icon = Icons.Default.DeleteForever,
                            contentColor = palette.getValue(SeverityLevel.Critical).contentHex.toColor(),
                            onClick = { onDeleteMessage(message.id) }
                        )
                    }
                }
                Text(
                    message.createdAt.formatTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatReminderEditorDialog(
    initial: ReminderEntity,
    reminderTypes: List<ReminderTypeDefinition>,
    onDismiss: () -> Unit,
    onSave: (ReminderEntity) -> Unit
) {
    var type by remember(initial.id) { mutableStateOf(initial.type) }
    var label by remember(initial.id) { mutableStateOf(initial.label) }
    var repeat by remember(initial.id) { mutableStateOf(initial.repeat) }
    var hour by remember(initial.id) { mutableStateOf(initial.hour) }
    var minute by remember(initial.id) { mutableStateOf(initial.minute) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showRepeatMenu by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminder)) },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(type = type, label = label, hour = hour, minute = minute, repeat = repeat))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { showTypeMenu = true }) { Text(reminderTypeLabel(type, reminderTypes)) }
                    DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                        reminderTypes.forEach { reminderType ->
                            DropdownMenuItem(
                                text = { Text(reminderType.localizedLabel()) },
                                onClick = {
                                    type = reminderType.id
                                    showTypeMenu = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.label)) }
                )
                OutlinedButton(onClick = { showTimePicker = true }) {
                    Text("%02d:%02d".format(hour, minute))
                }
                Box {
                    OutlinedButton(onClick = { showRepeatMenu = true }) { Text(repeat.localizedRepeat()) }
                    DropdownMenu(expanded = showRepeatMenu, onDismissRequest = { showRepeatMenu = false }) {
                        listOf("Once", "Daily", "Weekdays", "Custom days").forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value.localizedRepeat()) },
                                onClick = {
                                    repeat = value
                                    showRepeatMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    )
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = timePickerState.hour
                    minute = timePickerState.minute
                    showTimePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
            text = { TimePicker(timePickerState) }
        )
    }
}

@Composable
private fun GoalManagementCard(
    goal: GoalEntity,
    modifier: Modifier = Modifier,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit
) {
    val isRecommendation = goal.status == AiGoalStatus.RECOMMENDED
    val isAchieved = goal.status == AiGoalStatus.ACHIEVED
    val palette = LocalProgramVisuals.current.severityPalette
    val neutral = palette.getValue(SeverityLevel.Neutral)
    val normal = palette.getValue(SeverityLevel.Normal)
    val critical = palette.getValue(SeverityLevel.Critical)
    val container = if (isRecommendation) {
        semanticContainer(neutral.contentHex.toColor(), neutral.containerHex.toColor())
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val recommendationText = if (isRecommendation) {
        semanticContent(neutral.contentHex.toColor())
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val labelColor = if (isRecommendation) recommendationText else MaterialTheme.colorScheme.onSurfaceVariant
    val addColor = normal.contentHex.toColor()
    val deleteColor = critical.contentHex.toColor()
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = modifier
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (isRecommendation) stringResource(R.string.ai_label_recommendation) else stringResource(R.string.ai_label_goals),
                style = MaterialTheme.typography.labelMedium,
                color = labelColor
            )
            Text(
                goal.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isRecommendation) recommendationText else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            if (goal.description.isNotBlank()) {
                Text(
                    goal.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isRecommendation) recommendationText else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isRecommendation) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GoalActionButton(
                        label = stringResource(R.string.ai_add_to_goals),
                        icon = Icons.Default.AddTask,
                        contentColor = addColor,
                        onClick = { onAccept(goal.id) },
                    )
                    GoalActionButton(
                        label = stringResource(R.string.delete),
                        icon = Icons.Default.DeleteForever,
                        contentColor = deleteColor,
                        onClick = { onReject(goal.id) },
                    )
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GoalActionButton(
                        label = if (isAchieved) stringResource(R.string.ai_achieved) else stringResource(R.string.ai_achieve),
                        icon = Icons.Default.CheckCircle,
                        contentColor = if (isAchieved) addColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onToggle(goal.id) }
                    )
                    GoalActionButton(
                        label = stringResource(R.string.delete),
                        icon = Icons.Default.DeleteForever,
                        contentColor = deleteColor,
                        onClick = { onDelete(goal.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(
            label,
            Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun String.label(): String = when (this) {
    "finding" -> stringResource(R.string.ai_label_finding)
    "recommendation" -> stringResource(R.string.ai_label_recommendation)
    "question" -> stringResource(R.string.ai_label_ai)
    "status" -> stringResource(R.string.ai_label_status)
    "checklist" -> stringResource(R.string.ai_label_goals)
    else -> stringResource(R.string.ai_label_ai)
}

@Composable
private fun reminderTypeLabel(value: String, reminderTypes: List<ReminderTypeDefinition>): String =
    reminderTypes.firstOrNull { it.id == value }?.localizedLabel() ?: value

@Composable
private fun ReminderTypeDefinition.localizedLabel(): String = localizedResource(labelKey, label)

@Composable
private fun String.localizedRepeat(): String = when (this) {
    "Once" -> stringResource(R.string.once)
    "Daily" -> stringResource(R.string.daily)
    "Weekdays" -> stringResource(R.string.weekdays)
    "Custom days" -> stringResource(R.string.custom_days)
    else -> this
}

private fun Long.formatTime(): String {
    return DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(this))
}

private fun AiChatMessageEntity.recommendationGoalId(): String? {
    return runCatching {
        payloadJson
            ?.let { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }
            ?.get("goalId")
            ?.jsonPrimitive
            ?.content
    }.getOrNull()
}

private fun AiChatMessageEntity.menuAction(): String? {
    return runCatching {
        payloadJson
            ?.let { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }
            ?.takeIf { it["kind"]?.jsonPrimitive?.content == "menu_action" }
            ?.get("action")
            ?.jsonPrimitive
            ?.content
            ?.takeIf { it in AiMenuAction.all }
    }.getOrNull()
}

private fun GoalEntity.isVisibleInTodayPanel(): Boolean {
    return when (status) {
        AiGoalStatus.RECOMMENDED,
        AiGoalStatus.ACCEPTED,
        AiGoalStatus.SCHEDULED -> true
        AiGoalStatus.ACHIEVED -> completedAt?.isToday() == true
        else -> false
    }
}

private fun Long.isToday(): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(this).atZone(zone).toLocalDate() == Instant.now().atZone(zone).toLocalDate()
}

@Composable
private fun semanticContainer(accent: Color, lightContainer: Color): Color {
    return if (isSystemInDarkTheme()) {
        lerp(MaterialTheme.colorScheme.surface, accent, 0.22f)
    } else {
        lightContainer
    }
}

@Composable
private fun semanticContent(accent: Color): Color {
    return if (isSystemInDarkTheme()) {
        lerp(Color.White, accent, 0.25f)
    } else {
        accent
    }
}

private val checklistGoalStatuses = setOf(
    AiGoalStatus.ACCEPTED,
    AiGoalStatus.SCHEDULED,
    AiGoalStatus.ACHIEVED
)

private val managedGoalStatuses = checklistGoalStatuses + AiGoalStatus.RECOMMENDED
