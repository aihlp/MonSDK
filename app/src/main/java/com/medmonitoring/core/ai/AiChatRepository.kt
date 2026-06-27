package com.medmonitoring.core.ai

import com.medmonitoring.core.util.StringProvider
import com.medmonitoring.app.R
import com.medmonitoring.core.storage.db.MedDatabase
import com.medmonitoring.core.analytics.BaseAnalysisResult
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.storage.entity.AiChatMessageEntity
import com.medmonitoring.core.storage.entity.AiProgramStateEntity
import com.medmonitoring.core.storage.entity.AiReportEntity
import com.medmonitoring.core.storage.entity.AiSettingsEntity
import com.medmonitoring.core.storage.entity.GoalEntity
import com.medmonitoring.core.domain.model.Finding
import com.medmonitoring.core.domain.model.FindingSeverity
import com.medmonitoring.core.domain.model.AnalysisLevel
import com.medmonitoring.core.domain.model.StatisticMetric
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiChatRepository @Inject constructor(
    private val db: MedDatabase,
    private val stringProvider: StringProvider,
    program: UniversalProgramDefinition
) {
    private val programId = program.programId
    private val analysisStatusTexts by lazy {
        listOf(
            stringProvider.getString(R.string.ai_status_starting_analysis),
            stringProvider.getString(R.string.ai_status_analyzing),
            stringProvider.getString(R.string.ai_status_basic_running),
            stringProvider.getString(R.string.ai_status_analysis_stopped),
            stringProvider.getString(R.string.ai_status_collecting_data),
            stringProvider.getString(R.string.ai_status_analysis_ready),
            stringProvider.getString(R.string.ai_status_preparing_answer)
        )
    }

    fun observeMessages(): Flow<List<AiChatMessageEntity>> = db.aiChatMessageDao().observeMessages()
    fun observeSettings(): Flow<AiSettingsEntity> = db.aiSettingsDao().observe().map { it ?: AiSettingsEntity() }
    fun observeProgramState(): Flow<AiProgramStateEntity?> = db.aiProgramStateDao().observeToday()

    suspend fun ensureStorageInitialized() {
        if (db.aiSettingsDao().get() == null) db.aiSettingsDao().upsert(AiSettingsEntity())
        if (db.aiProgramStateDao().getToday() == null) refreshProgramStateFromGoals()
    }

    suspend fun ensureFirstRun(configure: Boolean = false) {
        ensureStorageInitialized()
        val settings = db.aiSettingsDao().get() ?: AiSettingsEntity()
        val messages = db.aiChatMessageDao().getMessages()
        val hasIntro = messages.any { it.type == "onboarding_start" }
        val shouldOfferSetup = configure && settings.personalizationStatus != "ready"
        if (messages.isEmpty() || !hasIntro || shouldOfferSetup) {
            addAssistant(
                type = "onboarding_start",
                text = stringProvider.getString(R.string.ai_onboarding_intro)
            )
        }
    }

    suspend fun startOnboarding() {
        addAssistant(
            type = "question",
            text = stringProvider.getString(R.string.ai_onboarding_start_question)
        )
    }

    suspend fun updateSettings(transform: (AiSettingsEntity) -> AiSettingsEntity) {
        db.aiSettingsDao().upsert(transform(db.aiSettingsDao().get() ?: AiSettingsEntity()))
    }

    suspend fun submitOnboardingAnswer(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        addUserMessage(trimmed)
        saveOnboardingAnswer(trimmed)
    }

    suspend fun shouldCaptureOnboardingAnswer(): Boolean {
        val settings = db.aiSettingsDao().get() ?: AiSettingsEntity()
        val lastAssistant = db.aiChatMessageDao()
            .getMessages()
            .lastOrNull { it.role == "assistant" }
        return when (lastAssistant?.type) {
            "question" -> true
            "onboarding_start" -> settings.personalizationStatus != "ready"
            else -> false
        }
    }

    suspend fun addUserMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        addMessage("user", "text", trimmed, null)
    }

    suspend fun requestLocalModelMode(): Boolean {
        val ready = db.aiModelDao().getModels().any { it.status == AiModelStatus.READY.name && it.localPath != null }
        return if (ready) {
            updateSettings { it.copy(mode = AiSettingsContract.MODE_LOCAL_MODEL) }
            addAssistant("status", stringProvider.getString(R.string.ai_status_local_enabled))
            true
        } else {
            updateSettings { it.copy(mode = AiSettingsContract.MODE_BASIC) }
            addAssistant("status", localModelMissingText())
            false
        }
    }

    suspend fun requestModelDownload(modelId: String) {
        db.aiModelDao().updateStatus(modelId, AiModelStatus.DOWNLOADING.name, null, System.currentTimeMillis())
        val model = AiModelRegistry.specFor(modelId)
        addAssistant(
            "status",
            stringProvider.getString(
                R.string.ai_status_download_started,
                model?.displayName ?: modelId,
                model?.repo.orEmpty()
            )
        )
    }

    suspend fun reportModelDownloadFinished(modelId: String, success: Boolean, details: String) {
        val model = AiModelRegistry.specFor(modelId)
        addAssistant(
            "status",
            if (success) {
                stringProvider.getString(R.string.ai_status_download_success, model?.displayName ?: modelId)
            } else {
                stringProvider.getString(R.string.ai_status_download_failed, model?.displayName ?: modelId, details)
            }
        )
    }

    suspend fun postMenuAction(action: String) {
        if (action !in AiMenuAction.all) return
        addAssistant(
            type = MENU_ACTION_TYPE,
            text = action.menuActionText(),
            payloadJson = buildJsonObject {
                put("kind", MENU_ACTION_TYPE)
                put("action", action)
            }.toString()
        )
    }

    suspend fun deleteMessage(id: String) {
        db.aiChatMessageDao().delete(id)
    }

    suspend fun clearAiContext() {
        db.aiChatMessageDao().deleteAll()
        db.aiReportDao().deleteAll()
        db.aiProfileFactDao().deleteAll()
        db.aiProgramStateDao().deleteAll()
        db.goalDao().deleteForProgram(programId)
        ensureStorageInitialized()
        addAssistant("status", stringProvider.getString(R.string.ai_status_context_cleared))
    }

    suspend fun showBaseAnalysis(result: BaseAnalysisResult) {
        val previouslyKnownKeys = latestBasicFindingKeys()
        val newFindings = result.findings.filterNot { it.stableKey in previouslyKnownKeys }
        saveBasicAnalysisReport(result)

        val messages = mutableListOf(
            AiResponseMessage("finding", result.basicSummary(newFindings.size))
        )
        messages += newFindings.take(4).map { finding ->
            AiResponseMessage("finding", finding.chatText(prefix = stringProvider.getString(R.string.ai_finding_prefix_new)))
        }
        if (newFindings.isEmpty() && result.findings.isNotEmpty()) {
            messages += result.findings.take(3).map { finding ->
                AiResponseMessage("finding", finding.chatText(prefix = stringProvider.getString(R.string.ai_finding_prefix_current)))
            }
        }
        result.contextRecommendation(newFindings)?.takeUnless { recommendationExists(it) }?.let { recommendation ->
            addRecommendationGoal(
                title = recommendation.title,
                description = recommendation.description,
                source = AiGoalSource.ANALYTICS_RECOMMENDATION,
                sourceRef = recommendation.sourceRef
            )
        }
        replaceAnalysisStatusOrAdd(messages.first().type, messages.first().text)
        db.aiChatMessageDao().upsertAll(messages.drop(1).map {
            newMessage("assistant", it.type, it.text, null)
        })
    }

    suspend fun recommendGoal(result: BaseAnalysisResult) {
        val recommendation = result.contextRecommendation(result.findings)
        if (recommendation == null || recommendationExists(recommendation)) {
            addAssistant("status", stringProvider.getString(R.string.ai_status_no_recommendation))
            return
        }
        addRecommendationGoal(
            title = recommendation.title,
            description = recommendation.description,
            source = AiGoalSource.ANALYTICS_RECOMMENDATION,
            sourceRef = recommendation.sourceRef
        )
    }

    suspend fun showAiAnalysis(result: AiAnalysisResult) {
        when (result) {
            is AiAnalysisResult.Unavailable -> replaceAnalysisStatusOrAdd("status", result.reason)
            is AiAnalysisResult.Ready -> applyModelResponse(result.response)
        }
    }

    suspend fun showAnalysisStatus(text: String) {
        val dao = db.aiChatMessageDao()
        val existing = dao.latestAssistantMessageByPayload(ANALYSIS_STATUS_PAYLOAD)
        dao.upsert(
            existing?.copy(
                type = "status",
                text = text
            ) ?: newMessage("assistant", "status", text, ANALYSIS_STATUS_PAYLOAD)
        )
    }

    suspend fun clearAnalysisStatus() {
        val dao = db.aiChatMessageDao()
        dao.deleteAssistantStatusByPayload(ANALYSIS_STATUS_PAYLOAD)
        dao.deleteAssistantStatusesByText(analysisStatusTexts)
    }

    suspend fun showProgress() {
        addAssistant("checklist", progressText(currentChecklist()))
    }

    suspend fun postReminderNotification(label: String, type: String) {
        val title = label.ifBlank { stringProvider.getString(R.string.ai_reminder_label) }
        val action = when (type.lowercase()) {
            "medication" -> stringProvider.getString(R.string.ai_reminder_take_medication, title)
            "measurement" -> stringProvider.getString(R.string.ai_reminder_record_value, title)
            "record" -> stringProvider.getString(R.string.ai_reminder_add_record, title)
            "diary" -> stringProvider.getString(R.string.ai_reminder_add_diary, title)
            else -> title
        }
        val goal = ensureReminderGoal(action, title, type)
        refreshProgramStateFromGoals()
        addLinkedGoalMessage(goal, action)
    }

    private suspend fun addLinkedGoalMessage(goal: GoalEntity, text: String) {
        addAssistant(
            type = "recommendation",
            text = text,
            payloadJson = buildJsonObject {
                put("goalId", goal.id)
                put("kind", "goal_action")
            }.toString()
        )
    }

    private suspend fun ensureReminderGoal(action: String, title: String, type: String): GoalEntity {
        val goals = currentActionGoals()
        return goals.firstOrNull { it.title == action } ?: insertGoal(
            title = action,
            description = stringProvider.getString(R.string.ai_reminder_scheduled_desc, title),
            status = AiGoalStatus.SCHEDULED,
            source = AiGoalSource.SCHEDULED_REMINDER,
            sourceRef = "reminder-${type.sanitizeId()}-${title.sanitizeId()}"
        )
    }

    suspend fun promptGoalEditing() {
        addAssistant("checklist", stringProvider.getString(R.string.ai_checklist_edit_prompt))
    }

    suspend fun toggleChecklistItem(id: String) {
        val goal = db.goalDao().getForProgram(programId).firstOrNull { it.id == id }
        val nextStatus = if (goal?.status == AiGoalStatus.ACHIEVED) AiGoalStatus.ACCEPTED else AiGoalStatus.ACHIEVED
        setGoalStatus(id, nextStatus)
        refreshProgramStateFromGoals()
        addAssistant("checklist", progressText(currentChecklist()))
    }

    suspend fun rejectGoal(id: String) {
        setGoalStatus(id, AiGoalStatus.REJECTED)
        refreshProgramStateFromGoals()
    }

    suspend fun acceptGoal(id: String) {
        setGoalStatus(id, AiGoalStatus.ACCEPTED)
        refreshProgramStateFromGoals()
        addAssistant("checklist", progressText(currentChecklist()))
    }

    suspend fun deleteGoal(id: String) {
        db.goalDao().markDeleted(id, AiGoalStatus.DELETED, System.currentTimeMillis())
        refreshProgramStateFromGoals()
    }

    suspend fun applyModelResponse(response: AiResponseJson) {
        val addedRecommendation = response.checklist.isNotEmpty()
        if (addedRecommendation) {
            val item = response.checklist.first()
            val description = response.messages.firstOrNull { it.type == "recommendation" }?.text.orEmpty()
            val recommendation = GoalRecommendation(
                title = item.title,
                description = description,
                sourceRef = "ai-${item.title.recommendationFingerprint(description)}"
            )
            if (!recommendationExists(recommendation)) {
                addRecommendationGoal(
                    title = recommendation.title,
                    description = recommendation.description,
                    source = AiGoalSource.AI_RECOMMENDATION,
                    sourceRef = recommendation.sourceRef
                )
            }
        }
        if (response.slider.isNotEmpty()) {
            saveProgramState(
                checklist = currentChecklist(),
                slider = response.slider
            )
        } else {
            refreshProgramStateFromGoals()
        }
        val first = response.messages.firstOrNull()
        if (first == null) {
            clearAnalysisStatus()
            return
        }
        replaceAnalysisStatusOrAdd(first.type, first.text)
        db.aiChatMessageDao().upsertAll(response.messages.drop(1).filterNot {
            addedRecommendation && it.type == "recommendation"
        }.map {
            newMessage("assistant", it.type, it.text, null)
        })
    }

    suspend fun addAssistant(type: String, text: String, payloadJson: String? = null) {
        addMessage("assistant", type, text, payloadJson)
    }

    private suspend fun saveOnboardingAnswer(text: String) {
        val facts = db.aiProfileFactDao().getFacts().associateBy { it.key }
        val nextKey = AiConversationContract.onboardingKeys.firstOrNull { facts[it] == null }
        if (nextKey == null) {
            addAssistant("text", stringProvider.getString(R.string.ai_status_onboarding_ready))
            return
        }
        val now = System.currentTimeMillis()
        db.aiProfileFactDao().upsert(
            com.medmonitoring.core.storage.entity.AiProfileFactEntity(
                id = nextKey,
                key = nextKey,
                value = normalizeProfileAnswer(nextKey, text),
                updatedAt = now
            )
        )
        val updatedFacts = db.aiProfileFactDao().getFacts()
        val status = when {
            updatedFacts.size >= 8 -> "ready"
            updatedFacts.isNotEmpty() -> "partial"
            else -> "none"
        }
        db.aiSettingsDao().upsert((db.aiSettingsDao().get() ?: AiSettingsEntity()).copy(personalizationStatus = status))
        val next = nextQuestion(updatedFacts.map { it.key }.toSet())
        addAssistant(if (status == "ready") "status" else "question", next)
    }

    private suspend fun currentChecklist(): List<AiGoalChecklistItem> {
        return currentActionGoals().map { it.toChecklistItem() }
    }

    private suspend fun currentActionGoals(): List<GoalEntity> {
        return db.goalDao()
            .getForProgram(programId)
            .filter { it.enabled && it.isVisibleActionGoalToday() }
    }

    private suspend fun addRecommendationGoal(
        title: String,
        description: String,
        source: String,
        sourceRef: String? = null
    ): GoalEntity {
        val goal = insertGoal(
            title = title,
            description = description,
            status = AiGoalStatus.RECOMMENDED,
            source = source,
            sourceRef = sourceRef
        )
        addAssistant(
            type = "recommendation",
            text = title,
            payloadJson = buildJsonObject {
                put("goalId", goal.id)
                put("kind", "goal_recommendation")
            }.toString()
        )
        return goal
    }

    private suspend fun insertGoal(
        title: String,
        description: String,
        status: String,
        source: String,
        sourceRef: String? = null
    ): GoalEntity {
        val now = System.currentTimeMillis()
        val goal = GoalEntity(
            id = UUID.randomUUID().toString(),
            programId = programId,
            title = title.trim(),
            description = description.trim(),
            targetMetricKey = null,
            targetValue = null,
            unit = null,
            progressValue = null,
            enabled = true,
            status = status,
            source = source,
            sourceRef = sourceRef,
            completedAt = null,
            createdAt = now,
            updatedAt = now
        )
        db.goalDao().upsert(goal)
        return goal
    }

    private suspend fun setGoalStatus(id: String, status: String) {
        val now = System.currentTimeMillis()
        db.goalDao().updateStatus(
            id = id,
            status = status,
            completedAt = if (status == AiGoalStatus.ACHIEVED) now else null,
            updatedAt = now
        )
    }

    private suspend fun refreshProgramStateFromGoals() {
        saveProgramState(currentChecklist())
    }

    private suspend fun saveProgramState(
        checklist: List<AiGoalChecklistItem>,
        slider: List<AiSliderItem> = defaultSlider(checklist)
    ) {
        val date = LocalDate.now(ZoneId.systemDefault()).toString()
        db.aiProgramStateDao().upsert(
            AiProgramStateEntity(
                date = date,
                sliderJson = AiJsonCodec.sliderToJson(slider),
                checklistJson = AiJsonCodec.checklistToJson(checklist),
                progressText = progressText(checklist),
                motivationText = slider.firstOrNull { it.type == "motivation" }?.text ?: stringProvider.getString(R.string.ai_default_motivation),
                focusText = slider.firstOrNull { it.type == "focus" }?.text ?: stringProvider.getString(R.string.ai_default_focus)
            )
        )
    }

    private fun defaultSlider(checklist: List<AiGoalChecklistItem>) = listOf(
        AiSliderItem("progress", stringProvider.getString(R.string.ai_summary_title), progressText(checklist)),
        AiSliderItem("motivation", stringProvider.getString(R.string.ai_daily_motivation), stringProvider.getString(R.string.ai_initial_motivation)),
        AiSliderItem("focus", stringProvider.getString(R.string.ai_focus_title), stringProvider.getString(R.string.ai_initial_focus))
    )

    private fun progressText(checklist: List<AiGoalChecklistItem>): String {
        return stringProvider.getString(R.string.ai_checklist_progress, checklist.count { it.done }, checklist.size)
    }

    private suspend fun latestBasicFindingKeys(): Set<String> {
        val report = db.aiReportDao().latest()?.takeIf { it.status == BASIC_ANALYSIS_STATUS } ?: return emptySet()
        return runCatching {
            Json.parseToJsonElement(report.inputJson)
                .jsonObject["findingKeys"]
                ?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content }
                ?.toSet()
                .orEmpty()
        }.getOrDefault(emptySet())
    }

    private suspend fun saveBasicAnalysisReport(result: BaseAnalysisResult) {
        val now = System.currentTimeMillis()
        db.aiReportDao().upsert(
            AiReportEntity(
                id = "basic-$now",
                createdAt = now,
                status = BASIC_ANALYSIS_STATUS,
                inputJson = buildJsonObject {
                    put("programId", result.programId)
                    put("recordCount", result.recordCount)
                    put("analysisLevel", result.analysisLevel.name)
                    put("findingKeys", buildJsonArray {
                        result.findings.forEach { add(it.stableKey) }
                    })
                }.toString(),
                outputJson = buildJsonObject {
                    put("summary", result.basicSummary(newFindingCount = 0))
                }.toString()
            )
        )
    }

    private fun BaseAnalysisResult.basicSummary(newFindingCount: Int): String {
        val riskCount = findings.count { it.severity == FindingSeverity.Risk }
        val positiveCount = findings.count { it.severity == FindingSeverity.Positive }
        val metrics = dashboardMetrics.take(4).joinToString(", ") { metric ->
            listOfNotNull(metric.localizedLabel(), metric.value.takeUnless { it == "-" }, metric.unit).joinToString(" ")
        }.ifBlank { stringProvider.getString(R.string.ai_no_stable_dashboard_metrics) }
        val discoveryText = when {
            findings.isEmpty() -> stringProvider.getString(R.string.ai_no_stable_findings_yet)
            newFindingCount > 0 -> stringProvider.getString(R.string.ai_new_findings_discovered, newFindingCount, if (newFindingCount == 1) "" else "s")
            else -> stringProvider.getString(R.string.ai_no_new_findings)
        }
        return stringProvider.getString(
            R.string.ai_basic_summary_format,
            recordCount,
            analysisLevel.localizedName(),
            discoveryText,
            riskCount,
            positiveCount,
            metrics
        )
    }

    private suspend fun recommendationExists(recommendation: GoalRecommendation): Boolean {
        val titleKey = recommendation.title.normalizedRecommendationText()
        val descriptionKey = recommendation.description.normalizedRecommendationText()
        return db.goalDao()
            .getForProgram(programId)
            .filter { it.enabled && it.status != AiGoalStatus.REJECTED }
            .any { goal ->
                goal.sourceRef == recommendation.sourceRef ||
                    goal.title.normalizedRecommendationText() == titleKey ||
                    (descriptionKey.isNotBlank() && goal.description.normalizedRecommendationText() == descriptionKey)
            }
    }

    private fun BaseAnalysisResult.contextRecommendation(newFindings: List<Finding>): GoalRecommendation? {
        val focus = (newFindings.firstOrNull { it.severity == FindingSeverity.Risk }
            ?: findings.firstOrNull { it.severity == FindingSeverity.Risk }
            ?: newFindings.firstOrNull()
            ?: findings.firstOrNull())
            ?: return if (recordCount < 3) {
                GoalRecommendation(
                    title = stringProvider.getString(R.string.ai_goal_insufficient_data_title),
                    description = stringProvider.getString(R.string.ai_goal_insufficient_data_desc),
                    sourceRef = "analytics-insufficient-data"
                )
            } else {
                null
            }
        val title = focus.recommendationTitle()
        val explanation = focus.recommendationDescription(title)
        return GoalRecommendation(
            title = title,
            description = explanation,
            sourceRef = "analytics-${focus.stableKey}"
        )
    }

    private fun Finding.chatText(prefix: String): String =
        "$prefix: ${localizedTitle()}\n${localizedMessage()}\n${localizedBasis()}"

    private fun AnalysisLevel.localizedName(): String = when (this) {
        AnalysisLevel.NONE -> stringProvider.getString(R.string.level_none)
        AnalysisLevel.BASIC_3_9 -> stringProvider.getString(R.string.level_basic)
        AnalysisLevel.COMPARATIVE_10_30 -> stringProvider.getString(R.string.level_comparative)
        AnalysisLevel.ADVANCED_31_PLUS -> stringProvider.getString(R.string.level_advanced)
    }

    private fun StatisticMetric.localizedLabel(): String =
        stringProvider.getStringByName(labelKey ?: id, label)

    private fun Finding.localizedTitle(): String = when (titleKey) {
        "finding_adherence" -> stringProvider.getString(R.string.finding_adherence)
        "finding_combination" -> stringProvider.getString(R.string.finding_combination)
        "finding_frequent_prefix" -> stringProvider.getString(
            R.string.finding_frequent_prefix,
            sourceRuleId.removePrefix("frequent_tag_").humanizeAnalyticsToken()
        )
        "finding_comparison" -> stringProvider.getString(
            R.string.finding_comparison,
            evidence.firstOrNull()?.localizedLabel() ?: metricKey.orEmpty().humanizeAnalyticsToken(),
            (dimensionKey ?: eventKey).orEmpty().humanizeAnalyticsToken()
        )
        "finding_extreme" -> stringProvider.getString(
            R.string.finding_extreme,
            if (stableKey.startsWith("highest_")) stringProvider.getString(R.string.highest) else stringProvider.getString(R.string.lowest),
            evidence.firstOrNull()?.localizedLabel() ?: metricKey.orEmpty().humanizeAnalyticsToken()
        )
        else -> title
    }

    private fun Finding.localizedMessage(): String = when (messageKey) {
        "msg_adherence" -> stringProvider.getString(
            R.string.msg_adherence,
            evidence.firstOrNull { it.id.endsWith("_percent") }?.value?.toIntOrNull() ?: 0,
            if (message.contains("taken")) stringProvider.getString(R.string.taken) else stringProvider.getString(R.string.missed)
        )
        "msg_frequent" -> stringProvider.getString(R.string.msg_frequent, leftGroupLabel.orEmpty().humanizeAnalyticsToken(), leftGroupSize ?: 0, recordCount)
        "msg_combination" -> stringProvider.getString(
            R.string.msg_combination,
            leftGroupLabel.orEmpty().split(" + ").joinToString(" + ") { it.humanizeAnalyticsToken() },
            leftGroupSize ?: 0
        )
        "msg_comparison" -> {
            val right = evidence.firstOrNull { it.id.endsWith("_right") }
            val left = evidence.firstOrNull { it.id.endsWith("_left") }
            if (right != null && left != null) {
                stringProvider.getString(
                    R.string.msg_comparison,
                    right.localizedLabel(),
                    right.value.toIntOrNull() ?: 0,
                    right.unit.orEmpty(),
                    left.value.toIntOrNull() ?: 0,
                    left.localizedLabel()
                )
            } else {
                message
            }
        }
        "msg_extreme" -> {
            val metric = evidence.firstOrNull()
            stringProvider.getString(
                R.string.msg_extreme,
                if (stableKey.startsWith("highest_")) stringProvider.getString(R.string.highest) else stringProvider.getString(R.string.lowest),
                metric?.localizedLabel() ?: metricKey.orEmpty().humanizeAnalyticsToken(),
                metric?.value?.toIntOrNull() ?: 0,
                metric?.unit.orEmpty()
            )
        }
        else -> message
    }

    private fun Finding.localizedBasis(): String = when (basisKey) {
        "basis_meds" -> stringProvider.getString(R.string.basis_meds, (leftGroupSize ?: 0) + (rightGroupSize ?: 0))
        "basis_records" -> stringProvider.getString(R.string.basis_records, recordCount)
        "basis_comparison", "basis_tagged_comparison" -> stringProvider.getString(
            R.string.basis_comparison,
            leftGroupSize ?: 0,
            leftGroupLabel.orEmpty().humanizeAnalyticsToken(),
            rightGroupSize ?: 0,
            rightGroupLabel.orEmpty().humanizeAnalyticsToken()
        )
        else -> basis
    }

    private fun String.humanizeAnalyticsToken(): String {
        val snake = lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        return stringProvider.getStringByName(
            snake,
            stringProvider.getStringByName(
                "tag_$snake",
                stringProvider.getStringByName(
                    "group_$snake",
                    replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                )
            )
        )
    }

    private fun nextQuestion(doneKeys: Set<String>): String {
        return when {
            AiConversationContract.PROFILE_MAIN_GOAL !in doneKeys -> stringProvider.getString(R.string.ai_question_main_goal)
            AiConversationContract.PROFILE_AGE !in doneKeys -> stringProvider.getString(R.string.ai_question_age)
            AiConversationContract.PROFILE_GENDER !in doneKeys -> stringProvider.getString(R.string.ai_question_gender)
            AiConversationContract.PROFILE_LIMITATIONS !in doneKeys -> stringProvider.getString(R.string.ai_question_limitations)
            AiConversationContract.PROFILE_DAILY_MOTIVATION !in doneKeys -> stringProvider.getString(R.string.ai_question_motivation)
            AiConversationContract.PROFILE_ROUTINE_OR_TREATMENT !in doneKeys -> stringProvider.getString(R.string.ai_question_treatment)
            AiConversationContract.PROFILE_TRACKING_TIME !in doneKeys -> stringProvider.getString(R.string.ai_question_tracking_time)
            AiConversationContract.PROFILE_TRACKED_TRIGGERS !in doneKeys -> stringProvider.getString(R.string.ai_question_triggers)
            else -> stringProvider.getString(R.string.ai_status_onboarding_ready)
        }
    }

    private fun normalizeProfileAnswer(key: String, text: String): String {
        val trimmed = text.trim().replace(Regex("\\s+"), " ")
        if (key != AiConversationContract.PROFILE_DAILY_MOTIVATION) return trimmed
        return when (trimmed.normalizedYesNo()) {
            "yes" -> "yes"
            "no" -> "no"
            else -> trimmed
        }
    }

    private fun String.normalizedYesNo(): String {
        val normalized = lowercase().trim().replace(Regex("[.!?\\s]+"), " ")
        return when (normalized) {
            "yes", "y", "true", "ok", "\u0434\u0430", "\u0430\u0433\u0430", "si", "s\u00ed" -> "yes"
            "no", "n", "false", "\u043d\u0435\u0442" -> "no"
            else -> normalized
        }
    }

    private fun localModelMissingText(): String {
        val model = AiModelRegistry.recommendedModels.first()
        return stringProvider.getString(
            R.string.ai_local_model_missing_details,
            model.displayName,
            model.repo,
            model.sizeMb,
            model.minRamGb,
            model.recommendedRamGb
        )
    }

    private suspend fun addMessage(role: String, type: String, text: String, payloadJson: String?) {
        db.aiChatMessageDao().upsert(newMessage(role, type, text, payloadJson))
    }

    private suspend fun replaceAnalysisStatusOrAdd(type: String, text: String) {
        val dao = db.aiChatMessageDao()
        val existing = dao.latestAssistantMessageByPayload(ANALYSIS_STATUS_PAYLOAD)
        dao.upsert(
            existing?.copy(
                type = type,
                text = text,
                payloadJson = null
            ) ?: newMessage("assistant", type, text, null)
        )
    }

    private fun newMessage(role: String, type: String, text: String, payloadJson: String?): AiChatMessageEntity {
        return AiChatMessageEntity(UUID.randomUUID().toString(), System.currentTimeMillis(), role, type, text, payloadJson)
    }

    private fun Finding.recommendationTitle(): String {
        val target = readableContextLabel()
        val metric = metricKey.readableKey().ifBlank { stringProvider.getString(R.string.ai_tracked_value) }
        return when (type) {
            "adherence" -> stringProvider.getString(R.string.ai_rec_review_pattern, target.takeUnless { it == stringProvider.getString(R.string.ai_today_context) } ?: stringProvider.getString(R.string.ai_routine))
            "dimension_frequency" -> when (severity) {
                FindingSeverity.Positive -> stringProvider.getString(R.string.ai_rec_continue, target)
                FindingSeverity.Risk -> stringProvider.getString(R.string.ai_rec_watch_today, target)
                else -> stringProvider.getString(R.string.ai_rec_track_today, target)
            }
            "dimension_combination" -> when (severity) {
                FindingSeverity.Positive -> stringProvider.getString(R.string.ai_rec_continue_pattern)
                FindingSeverity.Risk -> stringProvider.getString(R.string.ai_rec_watch_pattern)
                else -> stringProvider.getString(R.string.ai_rec_track_pattern)
            }
            "metric_by_dimension",
            "metric_by_event" -> stringProvider.getString(R.string.ai_rec_compare, metric, target)
            "dangerous_metric" -> stringProvider.getString(R.string.ai_rec_recheck, metric)
            else -> when (severity) {
                FindingSeverity.Positive -> stringProvider.getString(R.string.ai_rec_keep_helpful)
                FindingSeverity.Risk -> stringProvider.getString(R.string.ai_fallback_goal_title)
                else -> stringProvider.getString(R.string.ai_fallback_goal_title)
            }
        }.compactTitle()
    }

    private fun Finding.recommendationDescription(actionTitle: String): String {
        val evidence = "${localizedMessage()} ${localizedBasis()}".trim()
        val action = actionTitle.trim()
        return when (severity) {
            FindingSeverity.Risk -> stringProvider.getString(R.string.ai_rec_desc_risk, action, evidence)
            FindingSeverity.Positive -> stringProvider.getString(R.string.ai_rec_desc_positive, action, evidence)
            FindingSeverity.Neutral -> stringProvider.getString(R.string.ai_rec_desc_neutral, action, evidence)
        }
    }

    private fun Finding.readableContextLabel(): String {
        if (type == "adherence") return stringProvider.getString(R.string.ai_routine)
        return listOfNotNull(leftGroupLabel, dimensionKey.readableKey(), metricKey.readableKey())
            .firstOrNull { it.isNotBlank() }
            ?: stringProvider.getString(R.string.ai_today_context)
    }

    private fun String?.readableKey(): String {
        return orEmpty()
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
    }

    private fun String.compactTitle(): String {
        return trim()
            .replace(Regex("\\s+"), " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .take(MAX_RECOMMENDATION_TITLE_LENGTH)
            .trimEnd()
    }

    private fun String.sanitizeId(): String {
        return lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "item" }
    }

    private fun String.menuActionText(): String {
        return when (this) {
            AiMenuAction.BASIC_ANALYSIS -> stringProvider.getString(R.string.ai_menu_basic_analysis)
            AiMenuAction.AI_ANALYSIS -> stringProvider.getString(R.string.ai_menu_ai_analysis)
            AiMenuAction.CONFIGURE_AI -> stringProvider.getString(R.string.ai_menu_configure_ai)
            AiMenuAction.SET_REMINDER -> stringProvider.getString(R.string.ai_menu_set_reminder)
            AiMenuAction.RECOMMEND_GOAL -> stringProvider.getString(R.string.ai_menu_recommend_goal)
            AiMenuAction.CLEAR_HISTORY -> stringProvider.getString(R.string.ai_menu_clear_history)
            else -> stringProvider.getString(R.string.ai_menu_generic_start)
        }
    }

    private companion object {
        const val ANALYSIS_STATUS_PAYLOAD = "{\"transient\":\"ai_analysis\"}"
        const val BASIC_ANALYSIS_STATUS = "BASIC_ANALYSIS"
        const val MENU_ACTION_TYPE = "menu_action"
        const val MAX_RECOMMENDATION_TITLE_LENGTH = 70
        val actionStatuses = setOf(AiGoalStatus.ACCEPTED, AiGoalStatus.SCHEDULED, AiGoalStatus.ACHIEVED)
        val onboardingCaptureTypes = setOf("onboarding_start", "question")
    }
}

private fun GoalEntity.toChecklistItem(): AiGoalChecklistItem {
    return AiGoalChecklistItem(
        id = id,
        title = title,
        done = status == AiGoalStatus.ACHIEVED,
        createdAt = createdAt,
        completedAt = completedAt
    )
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
    return java.time.Instant.ofEpochMilli(this).atZone(zone).toLocalDate() == java.time.Instant.now().atZone(zone).toLocalDate()
}

private data class GoalRecommendation(
    val title: String,
    val description: String,
    val sourceRef: String
)

private fun String.recommendationFingerprint(other: String): String {
    return "${normalizedRecommendationText()}-${other.normalizedRecommendationText()}"
        .lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').ifBlank { "item" }
        .take(80)
}

private fun String.normalizedRecommendationText(): String {
    return lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}
