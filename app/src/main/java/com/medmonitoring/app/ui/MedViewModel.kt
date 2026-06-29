package com.medmonitoring.app.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.medmonitoring.core.ai.AiChatRepository
import com.medmonitoring.core.ai.AiAnalysisUseCase
import com.medmonitoring.core.ai.AiAnalysisWorker
import com.medmonitoring.core.ai.AiGoalDraft
import com.medmonitoring.core.ai.AiGoalStatus
import com.medmonitoring.core.ai.AiMenuAction
import com.medmonitoring.core.ai.AndroidLlamaRuntime
import com.medmonitoring.core.ai.AiProfileRepository
import com.medmonitoring.core.ai.AiModelDownloadWorker
import com.medmonitoring.core.ai.AiSettingsContract
import com.medmonitoring.core.analytics.AnalyticsEngine
import com.medmonitoring.core.analytics.BaseAnalysisUseCase
import com.medmonitoring.core.analytics.ProgramRecordMapper
import com.medmonitoring.core.config.ConfigStrictMode
import com.medmonitoring.core.domain.model.*
import kotlin.math.roundToInt
import com.medmonitoring.core.domain.repository.EventRepository
import com.medmonitoring.core.ingestion.IngestionManager
import com.medmonitoring.core.ingestion.HealthConnectDataSource
import com.medmonitoring.core.ingestion.SensorRegistry
import com.medmonitoring.core.normalization.SensorRuleEvaluator
import com.medmonitoring.core.normalization.CalendarContextTagger
import com.medmonitoring.core.reports.CsvRecordCodec
import com.medmonitoring.core.storage.entity.GoalEntity
import com.medmonitoring.core.storage.entity.ReminderEntity
import com.medmonitoring.core.settings.CollectionSettings
import com.medmonitoring.core.premium.BillingManager
import com.medmonitoring.core.premium.AppFeature
import com.medmonitoring.core.premium.PremiumPolicy
import com.medmonitoring.core.premium.PremiumRepository
import com.medmonitoring.core.util.StringProvider
import com.medmonitoring.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class RecordInputState(
    val timestamp: Instant = Instant.now(),
    val eventStatuses: Map<String, String> = emptyMap(),
    val eventTexts: Map<String, String> = emptyMap(),
    val medicationStatus: MedicationStatus = MedicationStatus.NOT_RECORDED,
    val medicationFullText: String = "",
    val metricValues: Map<String, Int> = emptyMap(),
    val note: String = "",
) {
    fun metricValue(metricId: String): Int? = metricValues[metricId]
    fun eventStatus(eventKey: String): String? = eventStatuses[eventKey]
    fun eventText(eventKey: String): String = eventTexts[eventKey].orEmpty()
}

@HiltViewModel
class MedViewModel @Inject constructor(
    private val repository: EventRepository,
    private val ingestionManager: IngestionManager,
    private val analyticsEngine: AnalyticsEngine,
    val healthConnectDataSource: HealthConnectDataSource,
    val sensorRegistry: SensorRegistry,
    val collectionSettings: CollectionSettings,
    val premiumRepository: PremiumRepository,
    val billingManager: BillingManager,
    private val recordMapper: ProgramRecordMapper<UserRecord>,
    private val baseAnalysisUseCase: BaseAnalysisUseCase,
    private val aiAnalysisUseCase: AiAnalysisUseCase,
    val aiProfileRepository: AiProfileRepository,
    private val aiChatRepository: AiChatRepository,
    private val stringProvider: StringProvider,
    val program: UniversalProgramDefinition,
    val uiDefinition: ProgramUiDefinition,
    private val analyticsSchema: AnalyticsConfig,
    @param:ApplicationContext private val appContext: Context
) : ViewModel() {
    private val aiPrefs: SharedPreferences = appContext.getSharedPreferences("ai_chat_ui", Context.MODE_PRIVATE)
    private val _goalPanelExpanded = MutableStateFlow(aiPrefs.getBoolean(KEY_GOAL_PANEL_EXPANDED, true))
    val goalPanelExpanded: StateFlow<Boolean> = _goalPanelExpanded.asStateFlow()
    private val _aiChatOperationBusy = MutableStateFlow(false)
    val input = mutableStateOf(
        RecordInputState(
            eventStatuses = program.eventInputs.associate { input ->
                input.key to (input.statuses.firstOrNull { it.positive } ?: input.statuses.first()).status
            },
            eventTexts = program.eventInputs.associate { input ->
                input.key to input.defaultText()
            },
            metricValues = program.metricComponents.mapNotNull { metric ->
                metric.defaultValue?.let { metric.id to it }
            }.toMap()
        )
    )
    val selectedTags = mutableStateMapOf<String, Set<String>>()
    val otherInputs = mutableStateMapOf<String, String>()

    init {
        sensorRegistry.configure(program.integrations.hardwareSensors)
        collectionSettings.initializeSensors(emptySet())
        viewModelScope.launch {
            collectionSettings.state.collect { settings ->
                sensorRegistry.applyEnabledSensors(settings.enabledSensorIds)
            }
        }
        viewModelScope.launch {
            repository.getLastInput()?.let { (med, values) ->
                input.value = input.value.copy(
                    eventTexts = input.value.eventTexts.mapValues { (_, text) -> med.ifBlank { text } },
                    metricValues = input.value.metricValues + values
                )
            }
        }
    }

    override fun onCleared() {
        sensorRegistry.stop()
        super.onCleared()
    }

    val records = repository.observeRecords()
        .onEach { if (it.isNotEmpty()) collectionSettings.markRecordsCreated() }
        .map<List<UserRecord>, List<UserRecord>?> { it }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val customTags = repository.observeCustomTags().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val reminders = repository.observeReminders().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val anamnesis = aiProfileRepository.observeAnamnesis().stateIn(viewModelScope, SharingStarted.Lazily, "")
    val goals = aiProfileRepository.observeGoals(program.programId).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val aiReports = aiProfileRepository.observeReports(program.programId).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val aiModels = aiProfileRepository.observeModels().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val aiSettings = aiChatRepository.observeSettings().stateIn(viewModelScope, SharingStarted.Lazily, com.medmonitoring.core.storage.entity.AiSettingsEntity())
    val aiChatMessages = aiChatRepository.observeMessages().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val aiProgramState = aiChatRepository.observeProgramState().stateIn(viewModelScope, SharingStarted.Lazily, null)
    private val aiManualAnalysisBusy = WorkManager.getInstance(appContext)
        .getWorkInfosForUniqueWorkFlow(AiAnalysisWorker.MANUAL_WORK)
        .map { work -> work.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED } }
    private val aiDailyAnalysisBusy = WorkManager.getInstance(appContext)
        .getWorkInfosForUniqueWorkFlow(AiAnalysisWorker.UNIQUE_WORK)
        .map { work -> work.any { it.state == WorkInfo.State.RUNNING } }
    // A native process crash can leave a persisted WorkManager row in RUNNING
    // state. Do not permanently disable the AI UI because of that stale row:
    // runOnce uses REPLACE and safely supersedes it.
    val aiChatBusy = _aiChatOperationBusy.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val aiModelDownloadStatus = WorkManager.getInstance(appContext)
        .getWorkInfosByTagFlow(AiModelDownloadWorker.TAG)
        .map { work ->
            val active = work.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                ?: return@map null
            val downloaded = active.progress.getLong(AiModelDownloadWorker.KEY_DOWNLOADED, 0L)
            val total = active.progress.getLong(AiModelDownloadWorker.KEY_TOTAL, 0L)
            val modelId = active.progress.getString(AiModelDownloadWorker.KEY_MODEL_ID)
                ?: active.tags.firstOrNull { it.startsWith(AiModelDownloadWorker.MODEL_TAG_PREFIX) }
                    ?.removePrefix(AiModelDownloadWorker.MODEL_TAG_PREFIX)
                ?: "model"
            if (total > 0L) {
                val percent = (downloaded * 100 / total).coerceIn(0, 100)
                "Downloading $modelId: $percent% (${downloaded / 1_048_576} / ${total / 1_048_576} MB)"
            } else "Preparing $modelId download…"
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val aiRuntimeAvailable: Boolean = AndroidLlamaRuntime.isAvailable
    val aiChecklist = goals.map { currentGoals ->
        currentGoals
            .filter { it.enabled && it.isVisibleActionGoalToday() }
            .map {
                com.medmonitoring.core.ai.AiGoalChecklistItem(
                    id = it.id,
                    title = it.title,
                    done = it.status == AiGoalStatus.ACHIEVED,
                    createdAt = it.createdAt,
                    completedAt = it.completedAt
                )
            }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val analytics = records.map { current ->
        analyticsEngine.calculate(
            recordMapper.mapAll(current.orEmpty()),
            analyticsSchema
        )
    }
        .stateIn(viewModelScope, SharingStarted.Lazily, analyticsEngine.calculate(emptyList(), analyticsSchema))

    init {
        viewModelScope.launch {
            aiProfileRepository.ensureModelRegistrySeeded()
            aiChatRepository.ensureFirstRun()
        }
    }

    fun setTimestamp(timestamp: Instant) {
        input.value = input.value.copy(timestamp = timestamp)
    }

    private fun persistInput() {
        val state = input.value
        viewModelScope.launch {
            repository.saveLastInput(
                state.eventTexts.values.firstOrNull().orEmpty(),
                state.metricValues
            )
        }
    }

    fun setEventStatus(eventKey: String, status: String) {
        input.value = input.value.copy(eventStatuses = input.value.eventStatuses + (eventKey to status))
    }

    fun setEventText(eventKey: String, text: String) {
        input.value = input.value.copy(eventTexts = input.value.eventTexts + (eventKey to text))
        persistInput()
    }

    fun setMetricValue(metricId: String, value: Int) {
        input.value = input.value.copy(metricValues = input.value.metricValues + (metricId to value))
        persistInput()
    }

    fun setNote(note: String) {
        input.value = input.value.copy(note = note)
    }

    fun toggleTag(groupId: String, tag: String) {
        if (!ConfigStrictMode.assertTagGroupAllowed(program, groupId)) return
        val current = selectedTags[groupId].orEmpty()
        selectedTags[groupId] = if (current.contains(tag)) current - tag else current + tag
    }

    fun setOtherInput(groupId: String, value: String) {
        if (!ConfigStrictMode.assertTagGroupAllowed(program, groupId)) return
        otherInputs[groupId] = value
    }

    fun confirmOtherTag(groupId: String) {
        if (!ConfigStrictMode.assertTagGroupAllowed(program, groupId)) return
        val label = otherInputs[groupId]?.trim().orEmpty()
        if (label.isBlank()) return
        selectedTags[groupId] = selectedTags[groupId].orEmpty() + label
        otherInputs[groupId] = ""
        viewModelScope.launch { repository.upsertCustomTag(groupId, label) }
    }

    fun saveRecord(onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val state = input.value
            val requiredMetrics = program.metricComponents
                .filter { it.isRequired }
                .map { it.id }
            if (requiredMetrics.any { state.metricValues[it] == null }) return@launch
            val enabledSensorValues = sensorRegistry.currentValues()
                .filterKeys { it in collectionSettings.state.value.enabledSensorIds }
            val sensorContext = SensorRuleEvaluator.evaluate(
                enabledSensorValues,
                program.integrations.hardwareSensors.flatMap { it.rules }
            )
            val contextualTags = sensorContext.tags.map { it.label }.toSet()
            val calendarTags = CalendarContextTagger.tags(state.timestamp)
            val payload = buildJsonObject {
                put("id", UUID.randomUUID().toString())
                put("timestamp", state.timestamp.toEpochMilli())
                put("measurements", buildJsonObject {
                    program.metricComponents.forEach { metric ->
                        if (metric.computedFrom != null) return@forEach
                        state.metricValues[metric.id]?.let { put(metric.id, it) }
                    }
                    // Derived metrics (e.g. BMI) are computed from entered source metrics.
                    program.metricComponents.forEach { metric ->
                        val computed = metric.computedFrom ?: return@forEach
                        computeMetric(computed, state.metricValues)?.let { put(metric.id, it) }
                    }
                })
                put("events", buildJsonArray {
                    program.eventInputs.forEach { eventInput ->
                        val status = state.eventStatus(eventInput.key).orEmpty()
                        if (status.isBlank()) return@forEach
                        val (name, amount, unit) = parseEventText(state.eventText(eventInput.key), eventInput)
                        add(buildJsonObject {
                            put("key", eventInput.key)
                            put("name", name)
                            put("status", status)
                            amount?.let { put("amount", it) }
                            unit?.let { put("unit", it) }
                        })
                    }
                })
                put("dimensions", buildJsonArray {
                    program.tagGroups.forEach { group ->
                        selectedTags[group.id].orEmpty().forEach { label ->
                            addDimension(group.id, label)
                        }
                    }
                    (contextualTags + calendarTags).forEach { label ->
                        addDimension("custom", label)
                    }
                })
                put("sensorContext", buildJsonObject {
                    enabledSensorValues.forEach { (sensorId, value) -> put(sensorId, value) }
                })
                put("programId", program.programId)
                put("note", state.note)
                put("createdAt", Instant.now().toEpochMilli())
            }
            runCatching {
                ingestionManager.ingestData(payload.toString(), SourceType.MANUAL)
            }.onSuccess { result ->
                Log.i("RecordSave", "Saved manual raw event ${result.rawEventId}")
                selectedTags.clear()
                input.value = state.copy(timestamp = Instant.now())
                onResult(Result.success(Unit))
            }.onFailure { error ->
                Log.e("RecordSave", "Manual record was not saved", error)
                onResult(Result.failure(error))
            }
        }
    }

    /** Computes a derived metric (e.g. BMI) from entered source values; null if sources are missing. */
    private fun computeMetric(
        definition: ComputedMetricDefinition,
        values: Map<String, Int>
    ): Double? = when (definition.formula) {
        ComputedMetricFormula.BMI -> {
            val weightKey = definition.sourceMetricIds.getOrNull(0)
            val heightKey = definition.sourceMetricIds.getOrNull(1)
            val weightKg = weightKey?.let { values[it] }?.toDouble()
            val heightCm = heightKey?.let { values[it] }?.toDouble()
            if (weightKg == null || heightCm == null || heightCm <= 0.0) {
                null
            } else {
                val heightM = heightCm / 100.0
                (weightKg / (heightM * heightM) * 10).roundToInt() / 10.0
            }
        }
    }

    private fun EventInputDefinition.defaultText(): String = buildString {
        append(defaultName)
        if (defaultAmount != null) append(" ${defaultAmount.toInt()}")
        if (!defaultUnit.isNullOrBlank()) append(" $defaultUnit")
    }.trim()

    private fun parseEventText(input: String, definition: EventInputDefinition): Triple<String, Double?, String> {
        val parts = input.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return Triple(definition.defaultName, definition.defaultAmount, definition.defaultUnit.orEmpty())
        
        // Try to find dose and unit at the end
        val last = parts.lastOrNull() ?: ""
        val secondLast = if (parts.size >= 2) parts[parts.size - 2] else ""
        
        return when {
            // Case: "Medication 10 mg"
            last.equals("mg", ignoreCase = true) || last.equals("g", ignoreCase = true) || last.equals("mcg", ignoreCase = true) -> {
                val dose = secondLast.toDoubleOrNull()
                val name = parts.dropLast(2).joinToString(" ")
                Triple(name.ifBlank { definition.defaultName.ifBlank { secondLast } }, dose, last)
            }
            // Case: "Medication 10mg"
            last.matches(Regex("\\d+(\\.\\d+)?(mg|g|mcg)", RegexOption.IGNORE_CASE)) -> {
                val match = Regex("(\\d+(?:\\.\\d+)?)(mg|g|mcg)", RegexOption.IGNORE_CASE).find(last)
                val dose = match?.groupValues?.get(1)?.toDoubleOrNull()
                val unit = match?.groupValues?.get(2) ?: ""
                val name = parts.dropLast(1).joinToString(" ")
                Triple(name.ifBlank { definition.defaultName.ifBlank { parts.dropLast(1).lastOrNull() ?: "" } }, dose, unit)
            }
            // Fallback
            else -> Triple(input.ifBlank { definition.defaultName }, definition.defaultAmount, definition.defaultUnit.orEmpty())
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch { repository.deleteRecord(id) }
    }

    fun upsertRecord(record: UserRecord) {
        viewModelScope.launch { repository.upsertRecord(record.copy(updatedAt = Instant.now())) }
    }

    fun upsertReminder(reminder: ReminderEntity) {
        viewModelScope.launch { repository.upsertReminder(reminder) }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch { repository.deleteReminder(id) }
    }

    fun exportCsvText(): String {
        if (!ConfigStrictMode.assertDataActionAllowed(program, DataActionType.ExportCsv)) return ""
        return CsvRecordCodec.encode(records.value.orEmpty())
    }

    fun importCsv(csv: String) {
        if (!ConfigStrictMode.assertDataActionAllowed(program, DataActionType.ImportCsv)) return
        viewModelScope.launch {
            repository.replaceRecords(CsvRecordCodec.decode(csv))
        }
    }

    fun saveAnamnesis(text: String) {
        viewModelScope.launch { aiProfileRepository.saveAnamnesis(text) }
    }

    fun upsertGoal(draft: AiGoalDraft) {
        viewModelScope.launch { aiProfileRepository.upsertGoal(program.programId, draft) }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch { aiProfileRepository.deleteGoal(id) }
    }

    fun acceptGoal(id: String) {
        if (!hasAiAccess()) return
        viewModelScope.launch { aiChatRepository.acceptGoal(id) }
    }

    fun rejectGoal(id: String) {
        if (!hasAiAccess()) return
        viewModelScope.launch { aiChatRepository.rejectGoal(id) }
    }

    fun deleteAiGoal(id: String) {
        if (!hasAiAccess()) return
        viewModelScope.launch { aiChatRepository.deleteGoal(id) }
    }

    fun requestModelDownload(id: String) {
        if (!hasAiAccess()) return
        AiModelDownloadWorker.enqueue(appContext, id)
    }

    fun runAiAnalysisNow() {
        if (!hasAiAccess()) return
        viewModelScope.launch {
            if (_aiChatOperationBusy.value || aiChatBusy.value) return@launch
            _aiChatOperationBusy.value = true
            try {
                aiChatRepository.showBaseAnalysis(baseAnalysisUseCase.run())
            } finally {
                _aiChatOperationBusy.value = false
            }
        }
    }

    fun requestAiMenuAction(action: String) {
        if (!hasAiAccess()) return
        viewModelScope.launch {
            if (_aiChatOperationBusy.value || aiChatBusy.value) return@launch
            aiChatRepository.postMenuAction(action)
        }
    }

    fun dismissAiMenuAction(messageId: String) {
        if (!hasAiAccess()) return
        viewModelScope.launch { aiChatRepository.deleteMessage(messageId) }
    }

    fun startAiMenuAction(action: String, messageId: String, onOpenReminder: () -> Unit = {}) {
        if (!hasAiAccess()) return
        viewModelScope.launch {
            if (_aiChatOperationBusy.value || aiChatBusy.value) return@launch
            aiChatRepository.deleteMessage(messageId)
            _aiChatOperationBusy.value = true
            try {
                when (action) {
                    AiMenuAction.BASIC_ANALYSIS -> aiChatRepository.showBaseAnalysis(baseAnalysisUseCase.run())
                    AiMenuAction.AI_ANALYSIS -> {
                        aiChatRepository.showAnalysisStatus(stringProvider.getString(R.string.ai_status_starting_analysis))
                        AiAnalysisWorker.runOnce(appContext, program.programId)
                    }
                    AiMenuAction.CONFIGURE_AI -> aiChatRepository.startOnboarding()
                    AiMenuAction.SET_REMINDER -> onOpenReminder()
                    AiMenuAction.RECOMMEND_GOAL -> aiChatRepository.recommendGoal(baseAnalysisUseCase.run())
                    AiMenuAction.CLEAR_HISTORY -> aiChatRepository.clearAiContext()
                }
            } finally {
                _aiChatOperationBusy.value = false
            }
        }
    }

    fun runAiModelAnalysisNow() {
        if (!hasAiAccess()) return
        viewModelScope.launch {
            if (_aiChatOperationBusy.value || aiChatBusy.value) return@launch
            aiChatRepository.showAnalysisStatus(stringProvider.getString(R.string.ai_status_starting_analysis))
            AiAnalysisWorker.runOnce(appContext, program.programId)
        }
    }

    fun openAiAssistant(configure: Boolean = false) {
        if (!hasAiAccess()) return
        viewModelScope.launch { aiChatRepository.ensureFirstRun(configure) }
    }

    fun onSendAiMessage(text: String) {
        viewModelScope.launch {
            val trimmed = text.trim()
            if (trimmed.isBlank()) return@launch
            if (!hasAiAccess()) return@launch
            if (_aiChatOperationBusy.value || aiChatBusy.value) return@launch
            _aiChatOperationBusy.value = true
            try {
                if (aiChatRepository.shouldCaptureOnboardingAnswer()) {
                    aiChatRepository.submitOnboardingAnswer(trimmed)
                    return@launch
                }
                val settings = aiSettings.value
                if (!settings.enabled || settings.mode != AiSettingsContract.MODE_LOCAL_MODEL) {
                    aiChatRepository.addUserMessage(trimmed)
                    aiChatRepository.addAssistant("status", stringProvider.getString(R.string.ai_question_needs_local_mode))
                    return@launch
                }
                aiChatRepository.addUserMessage(trimmed)
                aiChatRepository.showAnalysisStatus(stringProvider.getString(R.string.ai_status_preparing_answer))
                val result = aiAnalysisUseCase.answerQuestion(trimmed, baseAnalysisUseCase.run())
                aiChatRepository.showAiAnalysis(result)
            } finally {
                _aiChatOperationBusy.value = false
            }
        }
    }

    fun startAiOnboarding() {
        if (!hasAiAccess()) return
        viewModelScope.launch { aiChatRepository.startOnboarding() }
    }

    fun showAiProgress() {
        if (!hasAiAccess()) return
        viewModelScope.launch { aiChatRepository.showProgress() }
    }

    fun promptAiGoalEditing() {
        if (!hasAiAccess()) return
        viewModelScope.launch { aiChatRepository.promptGoalEditing() }
    }

    fun onChecklistItemToggle(id: String) {
        if (!hasAiAccess()) return
        viewModelScope.launch { aiChatRepository.toggleChecklistItem(id) }
    }

    fun setGoalPanelExpanded(expanded: Boolean) {
        _goalPanelExpanded.value = expanded
        aiPrefs.edit().putBoolean(KEY_GOAL_PANEL_EXPANDED, expanded).apply()
    }

    fun setAiEnabled(enabled: Boolean) {
        if (enabled && !hasAiAccess()) return
        viewModelScope.launch { aiChatRepository.updateSettings { it.copy(enabled = enabled) } }
    }

    fun setAiMode(mode: String) {
        if (!hasAiAccess()) return
        viewModelScope.launch {
            if (mode == AiSettingsContract.MODE_LOCAL_MODEL) {
                aiChatRepository.requestLocalModelMode()
            } else {
                aiChatRepository.updateSettings { it.copy(mode = mode) }
            }
        }
    }

    fun setAiNotifyAnalysisReady(enabled: Boolean) {
        if (!hasAiAccess()) return
        viewModelScope.launch { aiChatRepository.updateSettings { it.copy(notifyAnalysisReady = enabled) } }
    }

    fun setAiDailyMotivation(enabled: Boolean) {
        if (!hasAiAccess()) return
        viewModelScope.launch { aiChatRepository.updateSettings { it.copy(dailyMotivationEnabled = enabled) } }
    }

    fun formattedTimestamp(): String {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(input.value.timestamp)
    }

    private fun Set<String>.toJsonArray(): JsonArray = buildJsonArray { forEach { add(it) } }

    private fun JsonArrayBuilder.addDimension(groupId: String, label: String) {
        add(buildJsonObject {
            put("group", groupId)
            put("key", label.toDimensionKey())
            put("label", label)
        })
    }

    private fun String.toDimensionKey(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    private fun hasAiAccess(): Boolean {
        return PremiumPolicy.hasAccess(AppFeature.AI_ASSISTANT, premiumRepository.status.value)
    }

    private fun GoalEntity.isVisibleActionGoalToday(): Boolean {
        return when (status) {
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

    private companion object {
        const val KEY_GOAL_PANEL_EXPANDED = "goal_panel_expanded"
    }
}
