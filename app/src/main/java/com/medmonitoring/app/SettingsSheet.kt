package com.medmonitoring.app

import android.app.AlarmManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medmonitoring.app.ui.MedViewModel
import com.medmonitoring.app.ui.localizedResource
import com.medmonitoring.core.config.ConfigStrictMode
import com.medmonitoring.core.domain.model.DataActionType
import com.medmonitoring.core.domain.model.ReminderTypeDefinition
import com.medmonitoring.core.domain.model.SettingsSectionType
import com.medmonitoring.core.reminders.scheduleAlarm
import com.medmonitoring.core.storage.entity.ReminderEntity
import java.util.UUID
import android.app.Activity
import android.content.ContextWrapper
import com.medmonitoring.core.premium.PremiumStatus
import com.medmonitoring.core.premium.PromoCodeResult
import com.medmonitoring.core.premium.AppFeature
import com.medmonitoring.core.premium.PremiumPolicy
import com.medmonitoring.core.ai.AiModelRegistry
import com.medmonitoring.core.ai.AiSettingsContract

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(viewModel: MedViewModel, onDismiss: () -> Unit, onOpenAi: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val reminders by viewModel.reminders.collectAsState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        ) {
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            PremiumSettings(viewModel)
            HorizontalDivider()
            AiAssistantSettings(viewModel, onOpenAi)
            HorizontalDivider()
            viewModel.uiDefinition.settingsSections.forEach { section ->
                if (ConfigStrictMode.assertSettingsSectionAllowed(viewModel.uiDefinition, section)) {
                    SettingsSection(section, viewModel, context, reminders)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AiAssistantSettings(viewModel: MedViewModel, onOpenAi: (Boolean) -> Unit) {
    val settings by viewModel.aiSettings.collectAsState()
    val models by viewModel.aiModels.collectAsState()
    val downloadStatus by viewModel.aiModelDownloadStatus.collectAsState()
    val premiumStatus by viewModel.premiumRepository.status.collectAsState()
    var showLocalModelDialog by remember { mutableStateOf(false) }
    val aiPremiumAvailable = PremiumPolicy.hasAccess(AppFeature.AI_ASSISTANT, premiumStatus)
    val aiControlsEnabled = settings.enabled && aiPremiumAvailable
    val registryFallback = AiModelRegistry.recommendedModels.map {
        com.medmonitoring.core.storage.entity.AiModelEntity(
            id = it.id,
            displayName = it.displayName,
            repo = it.repo,
            quantization = it.quantization,
            sizeMb = it.sizeMb,
            minRamGb = it.minRamGb,
            recommendedRamGb = it.recommendedRamGb,
            downloadUrl = it.downloadUrl,
            status = it.status.name,
            localPath = null,
            updatedAt = 0L
        )
    }
    val visibleModels = models.ifEmpty { registryFallback }
    val modelStatus = visibleModels.firstOrNull { it.status == "DOWNLOADING" }
        ?: visibleModels.firstOrNull { it.status == "READY" }
        ?: visibleModels.firstOrNull { it.status == "ERROR" }
        ?: visibleModels.firstOrNull()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.ai_assistant), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_assistant))
                Text(
                    when {
                        !aiPremiumAvailable -> stringResource(R.string.ai_premium_required)
                        settings.enabled -> stringResource(R.string.ai_enabled)
                        else -> stringResource(R.string.ai_disabled)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = settings.enabled && aiPremiumAvailable,
                enabled = aiPremiumAvailable,
                onCheckedChange = viewModel::setAiEnabled
            )
        }
        Text(
            stringResource(
                R.string.ai_personalization,
                when (settings.personalizationStatus) {
                    "ready" -> stringResource(R.string.ai_personalization_ready)
                    "partial" -> stringResource(R.string.ai_personalization_partial)
                    else -> stringResource(R.string.ai_personalization_none)
                }
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        modelStatus?.let {
            Text(
                stringResource(R.string.ai_model_status, "${it.displayName}: ${it.status}"),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            stringResource(
                R.string.ai_runtime_status,
                if (viewModel.aiRuntimeAvailable) {
                    stringResource(R.string.ai_runtime_available)
                } else {
                    stringResource(R.string.ai_runtime_unavailable)
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (viewModel.aiRuntimeAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
        downloadStatus?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiModeButton(
                selected = settings.mode == AiSettingsContract.MODE_BASIC,
                enabled = aiControlsEnabled,
                label = stringResource(R.string.ai_basic_mode),
                modifier = Modifier.weight(1f),
                onClick = { viewModel.setAiMode(AiSettingsContract.MODE_BASIC) }
            )
            AiModeButton(
                selected = settings.mode == AiSettingsContract.MODE_LOCAL_MODEL,
                enabled = aiControlsEnabled,
                label = if (settings.mode == AiSettingsContract.MODE_LOCAL_MODEL) {
                    stringResource(R.string.ai_manage_models)
                } else {
                    stringResource(R.string.ai_local_model_mode)
                },
                modifier = Modifier.weight(1f),
                onClick = { showLocalModelDialog = true }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ai_notify_analysis_ready), Modifier.weight(1f))
            Switch(
                checked = settings.notifyAnalysisReady,
                enabled = aiControlsEnabled,
                onCheckedChange = viewModel::setAiNotifyAnalysisReady
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ai_daily_motivation), Modifier.weight(1f))
            Switch(
                checked = settings.dailyMotivationEnabled,
                enabled = aiControlsEnabled,
                onCheckedChange = viewModel::setAiDailyMotivation
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = aiControlsEnabled,
                onClick = { onOpenAi(false) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(R.string.ai_open_assistant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(
                enabled = aiControlsEnabled,
                onClick = { onOpenAi(true) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(R.string.ai_configure),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (showLocalModelDialog) {
        AlertDialog(
            onDismissRequest = { showLocalModelDialog = false },
            title = { Text(stringResource(R.string.ai_models_title)) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(stringResource(R.string.ai_models_intro))
                        Text(stringResource(R.string.ai_hf_no_key), style = MaterialTheme.typography.bodySmall)
                        downloadStatus?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    itemsIndexed(visibleModels) { index, model ->
                        Surface(
                            tonalElevation = if (index == 0) 2.dp else 0.dp,
                            shape = MaterialTheme.shapes.small,
                            border = ButtonDefaults.outlinedButtonBorder
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                                        Text(stringResource(R.string.ai_model_specs, model.quantization, model.sizeMb, model.minRamGb, model.recommendedRamGb))
                                    }
                                    Text(
                                        model.status.lowercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(model.repo, style = MaterialTheme.typography.bodySmall)
                                if (model.recommendedRamGb >= 4) {
                                    Text(stringResource(R.string.ai_low_ram_warning), color = MaterialTheme.colorScheme.error)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = model.status != "READY",
                                        onClick = { viewModel.requestModelDownload(model.id) }
                                    ) {
                                        Text(
                                            when (model.status) {
                                                "DOWNLOADING" -> stringResource(R.string.ai_retry)
                                                "READY" -> stringResource(R.string.ai_downloaded)
                                                "ERROR" -> stringResource(R.string.ai_retry)
                                                else -> stringResource(R.string.ai_download)
                                            }
                                        )
                                    }
                                    FilledTonalButton(
                                        enabled = model.status == "READY" && model.localPath != null,
                                        onClick = {
                                            viewModel.setAiMode(AiSettingsContract.MODE_LOCAL_MODEL)
                                            showLocalModelDialog = false
                                        }
                                    ) { Text(stringResource(R.string.ai_use_model)) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocalModelDialog = false }) { Text(stringResource(R.string.close)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setAiMode(AiSettingsContract.MODE_BASIC)
                    showLocalModelDialog = false
                }) {
                    Text(stringResource(R.string.ai_stay_basic))
                }
            }
        )
    }
}

@Composable
private fun AiModeButton(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.filledTonalButtonColors()
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        colors = colors,
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PremiumSettings(viewModel: MedViewModel) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val status by viewModel.premiumRepository.status.collectAsState()
    val billingState by viewModel.billingManager.state.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    val config = viewModel.program.premiumConfig

    LaunchedEffect(Unit) {
        viewModel.premiumRepository.refreshTrial()
        viewModel.billingManager.connect(config)
    }

    ListItem(
        headlineContent = { Text("Premium") },
        supportingContent = {
            Text(
                when (val current = status) {
                    is PremiumStatus.Trial -> "Free trial until ${current.expiresAt.toString().take(10)}"
                    PremiumStatus.Basic -> "Basic plan. Premium features remain visible."
                    is PremiumStatus.Premium -> when (current.productId) {
                        "promo_lifetime" -> "Unlimited premium · developer promo"
                        "promo_extended" -> "Extended premium until ${current.expiresAt?.toString()?.take(10)} · developer promo"
                        else -> "Active Google Play subscription"
                    }
                }
            )
        },
        trailingContent = {
            Button(onClick = { showDialog = true }) {
                Text(if (status is PremiumStatus.Premium) "Manage" else "Upgrade")
            }
        }
    )

    if (showDialog) {
        var promo by remember { mutableStateOf("") }
        var promoMessage by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Premium access") },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Close") }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Subscriptions are restored automatically for the Google Play account currently used on this device.")
                    Button(
                        enabled = activity != null,
                        onClick = {
                            activity?.let { viewModel.billingManager.launchPurchase(it, config.monthlyProductId) }
                        }
                    ) { Text("$2 / month") }
                    FilledTonalButton(
                        enabled = activity != null,
                        onClick = {
                            activity?.let { viewModel.billingManager.launchPurchase(it, config.yearlyProductId) }
                        }
                    ) { Text("$20 / year") }
                    TextButton(onClick = { viewModel.billingManager.restorePurchases() }) {
                        Text("Restore Google Play purchase")
                    }
                    HorizontalDivider()
                    Text("Developer promo code", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = promo,
                        onValueChange = { promo = it },
                        label = { Text("Promo code") },
                        shape = MaterialTheme.shapes.small,
                        singleLine = true
                    )
                    Button(onClick = {
                        promoMessage = when (viewModel.premiumRepository.applyPromoCode(promo, config)) {
                            PromoCodeResult.APPLIED_LIFETIME -> "Unlimited premium activated"
                            PromoCodeResult.APPLIED_EXTENDED -> "Premium extended by 3 months"
                            PromoCodeResult.INVALID -> "Invalid promo code"
                        }
                    }) { Text("Apply promo") }
                    promoMessage?.let { Text(it) }
                    billingState.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SettingsSection(section: SettingsSectionType, viewModel: MedViewModel, context: Context, reminders: List<ReminderEntity>) {
    when (section) {
        SettingsSectionType.DevicesSection ->
            ListItem(
                headlineContent = { Text(stringResource(R.string.devices)) },
                supportingContent = { Text(stringResource(R.string.device_integrations_disabled)) }
            )
        SettingsSectionType.HealthConnectSection ->
            com.medmonitoring.core.ui.settings.HealthConnectSettings(
                dataSource = viewModel.healthConnectDataSource,
                settings = viewModel.collectionSettings
            )
        SettingsSectionType.SensorLaboratorySection ->
            com.medmonitoring.core.ui.settings.SensorLaboratory(
                program = viewModel.program,
                registry = viewModel.sensorRegistry,
                settings = viewModel.collectionSettings,
                healthConnectDataSource = viewModel.healthConnectDataSource
            )
        SettingsSectionType.UnitsSection ->
            ListItem(
                headlineContent = { Text(stringResource(R.string.units)) },
                supportingContent = { Text(stringResource(R.string.units_summary)) }
            )
        SettingsSectionType.RemindersSection ->
            RemindersSettings(viewModel, context, reminders)
        SettingsSectionType.DataExportImportSection ->
            DataExportImportSettings(viewModel, context)
        SettingsSectionType.AboutSection ->
            ListItem(headlineContent = { Text(stringResource(R.string.about)) }, supportingContent = { Text(stringResource(R.string.app_name)) })
    }
}

@Composable
private fun DataExportImportSettings(viewModel: MedViewModel, context: Context) {
    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(viewModel.exportCsvText())
            }
        }
    }
    val importCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val csv = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            viewModel.importCsv(csv)
        }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.data_export_import)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (viewModel.program.dataActions.any { it.type == DataActionType.ExportCsv }) {
                    Button(onClick = { exportCsvLauncher.launch("${viewModel.program.programId}.csv") }) { Text(stringResource(R.string.export_csv)) }
                }
                if (viewModel.program.dataActions.any { it.type == DataActionType.ImportCsv }) {
                    FilledTonalButton(onClick = { importCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/*")) }) { Text(stringResource(R.string.import_csv)) }
                }
            }
        }
    )
}

@Composable
private fun RemindersSettings(viewModel: MedViewModel, context: Context, reminders: List<ReminderEntity>) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    var editing by remember { mutableStateOf<ReminderEntity?>(null) }
    val reminderTypes = viewModel.program.reminderTypes
    val defaultReminderType = reminderTypes.firstOrNull()
    val defaultReminderLabel = viewModel.program.eventInputs.firstOrNull()?.let { input ->
        localizedResource(input.labelKey, input.defaultName.ifBlank { input.label })
    } ?: defaultReminderType?.localizedLabel().orEmpty()
    ListItem(
        headlineContent = { Text(stringResource(R.string.reminders)) },
        supportingContent = {
            Column {
                Button(onClick = {
                    editing = ReminderEntity(
                        UUID.randomUUID().toString(),
                        defaultReminderType?.id.orEmpty(),
                        defaultReminderLabel,
                        19,
                        30,
                        "Daily",
                        true
                    )
                }) { Text(stringResource(R.string.create_reminder)) }
                reminders.forEach {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${it.label} ${"%02d:%02d".format(it.hour, it.minute)}", Modifier.weight(1f))
                        IconButton(onClick = { editing = it }) { Icon(Icons.Default.Edit, null) }
                        Switch(checked = it.enabled, onCheckedChange = { enabled ->
                            val updated = it.copy(enabled = enabled)
                            viewModel.upsertReminder(updated)
                            if (enabled) scheduleAlarm(context, alarmManager, updated)
                        })
                        IconButton(onClick = { viewModel.deleteReminder(it.id) }) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }
    )
    editing?.let { reminder ->
        ReminderEditorDialog(
            initial = reminder,
            reminderTypes = reminderTypes,
            onDismiss = { editing = null },
            onSave = {
                viewModel.upsertReminder(it)
                if (it.enabled) scheduleAlarm(context, alarmManager, it)
                editing = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditorDialog(
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { showTypeMenu = true }) { Text(reminderTypeLabel(type, reminderTypes)) }
                    DropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false },
                        shape = MaterialTheme.shapes.small
                    ) {
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
                    label = { Text(stringResource(R.string.label)) },
                    shape = MaterialTheme.shapes.small
                )
                OutlinedButton(onClick = { showTimePicker = true }) { Text("%02d:%02d".format(hour, minute)) }
                Box {
                    OutlinedButton(onClick = { showRepeatMenu = true }) { Text(repeatLabel(repeat)) }
                    DropdownMenu(
                        expanded = showRepeatMenu,
                        onDismissRequest = { showRepeatMenu = false },
                        shape = MaterialTheme.shapes.small
                    ) {
                        listOf(
                            "Once" to stringResource(R.string.once),
                            "Daily" to stringResource(R.string.daily),
                            "Weekdays" to stringResource(R.string.weekdays),
                            "Custom days" to stringResource(R.string.custom_days)
                        ).forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { repeat = value; showRepeatMenu = false })
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
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(timePickerState) }
        )
    }
}

@Composable
private fun reminderTypeLabel(value: String, reminderTypes: List<ReminderTypeDefinition>): String =
    reminderTypes.firstOrNull { it.id == value }?.localizedLabel() ?: value

@Composable
private fun ReminderTypeDefinition.localizedLabel(): String = localizedResource(labelKey, label)

@Composable
private fun repeatLabel(value: String): String {
    return when (value) {
        "Once" -> stringResource(R.string.once)
        "Daily" -> stringResource(R.string.daily)
        "Weekdays" -> stringResource(R.string.weekdays)
        "Custom days" -> stringResource(R.string.custom_days)
        else -> value
    }
}

