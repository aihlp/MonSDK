package com.medmonitoring.core.ai

import com.medmonitoring.core.domain.model.AiOnboardingAnswerType
import com.medmonitoring.core.domain.model.AnalyticsState
import com.medmonitoring.core.domain.model.Finding
import com.medmonitoring.core.domain.model.FindingSeverity
import com.medmonitoring.core.domain.model.StatisticMetric
import com.medmonitoring.core.domain.model.StatisticRole
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.storage.entity.AiProfileFactEntity
import com.medmonitoring.core.storage.entity.GoalEntity
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Locale
import javax.inject.Inject

class AiPromptBuilder @Inject constructor() {
    fun buildDailyRequest(
        program: UniversalProgramDefinition,
        records: List<UserRecord>,
        analytics: AnalyticsState,
        anamnesis: String,
        profileFacts: List<AiProfileFactEntity> = emptyList(),
        checklist: List<AiGoalChecklistItem> = emptyList(),
        goals: List<GoalEntity> = emptyList(),
        model: AiModelSpec,
        locale: Locale = Locale.getDefault()
    ): AiGenerationRequest {
        return buildRequest(
            task = AiPromptConfig.periodAnalysis,
            program = program,
            records = records,
            analytics = analytics,
            anamnesis = anamnesis,
            profileFacts = profileFacts,
            checklist = checklist,
            goals = goals,
            question = null,
            model = model,
            locale = locale
        )
    }

    fun buildQuestionRequest(
        question: String,
        program: UniversalProgramDefinition,
        records: List<UserRecord>,
        analytics: AnalyticsState,
        anamnesis: String,
        profileFacts: List<AiProfileFactEntity> = emptyList(),
        checklist: List<AiGoalChecklistItem> = emptyList(),
        goals: List<GoalEntity> = emptyList(),
        model: AiModelSpec,
        locale: Locale = Locale.getDefault()
    ): AiGenerationRequest {
        return buildRequest(
            task = AiPromptConfig.chatMessage,
            program = program,
            records = records,
            analytics = analytics,
            anamnesis = anamnesis,
            profileFacts = profileFacts,
            checklist = checklist,
            goals = goals,
            question = question,
            model = model,
            locale = locale
        )
    }

    fun buildDailyPrompt(
        program: UniversalProgramDefinition,
        records: List<UserRecord>,
        analytics: AnalyticsState,
        anamnesis: String,
        profileFacts: List<AiProfileFactEntity> = emptyList(),
        checklist: List<AiGoalChecklistItem> = emptyList(),
        goals: List<GoalEntity> = emptyList(),
        locale: Locale = Locale.getDefault()
    ): String {
        return buildDailyRequest(
            program = program,
            records = records,
            analytics = analytics,
            anamnesis = anamnesis,
            profileFacts = profileFacts,
            checklist = checklist,
            goals = goals,
            model = AiModelRegistry.recommendedModels.first(),
            locale = locale
        ).prompt
    }

    private fun buildRequest(
        task: AiPromptTaskConfig,
        program: UniversalProgramDefinition,
        records: List<UserRecord>,
        analytics: AnalyticsState,
        anamnesis: String,
        profileFacts: List<AiProfileFactEntity>,
        checklist: List<AiGoalChecklistItem>,
        goals: List<GoalEntity>,
        question: String?,
        model: AiModelSpec,
        locale: Locale
    ): AiGenerationRequest {
        val context = buildPromptContext(
            task = task,
            program = program,
            records = records,
            analytics = analytics,
            anamnesis = anamnesis,
            profileFacts = profileFacts,
            checklist = checklist,
            goals = goals,
            question = question,
            locale = locale
        )
        return AiGenerationRequest(
            model = model,
            grammar = grammarFor(task, analytics),
            maxTokens = task.maxTokens,
            prompt = renderPrompt(task, program, context, goals, locale)
        )
    }

    private fun renderPrompt(
        task: AiPromptTaskConfig,
        program: UniversalProgramDefinition,
        context: String,
        goals: List<GoalEntity>,
        locale: Locale
    ): String = buildString {
        appendLine(roleFor(task, program))
        appendLine(AiPromptConfig.jsonOnly)
        appendLine(languageInstruction(locale))
        appendLine(task.outputContract)
        appendLine(AiPromptConfig.medicalSafetyPolicy)
        task.instructions.forEach { appendLine(it) }
        goalLifecycleText(goals).takeIf { it.isNotBlank() }?.let { appendLine(it) }
        appendLine("Prompt variables:")
        append(context)
    }

    /** Program-specific AI role override, falling back to the task's default role. */
    private fun roleFor(task: AiPromptTaskConfig, program: UniversalProgramDefinition): String {
        val roles = program.aiPromptRoles ?: return task.role
        return when (task.taskId) {
            AiPromptTaskId.PERIOD_ANALYSIS -> roles.periodAnalysisRole
            AiPromptTaskId.CHAT_MESSAGE -> roles.chatRole
            else -> task.role
        }
    }

    /** Plain-text goal lifecycle the model must respect; rejected/deleted goals must not be repeated. */
    private fun goalLifecycleText(goals: List<GoalEntity>): String {
        if (goals.isEmpty()) return ""
        val active = goals.filter { it.enabled && it.status in ACTIVE_GOAL_STATUSES }
        val achieved = goals.filter { it.status == AiGoalStatus.ACHIEVED }
        val rejected = goals.filter { it.status == AiGoalStatus.REJECTED || it.status == AiGoalStatus.DELETED }
        return buildString {
            appendLine("Goal lifecycle:")
            if (active.isNotEmpty()) appendLine("active: ${active.joinToString("; ") { it.title.take(MAX_GOAL_TITLE_LENGTH) }}")
            if (achieved.isNotEmpty()) appendLine("achieved: ${achieved.joinToString("; ") { it.title.take(MAX_GOAL_TITLE_LENGTH) }}")
            if (rejected.isNotEmpty()) {
                appendLine("rejected: ${rejected.joinToString("; ") { it.title.take(MAX_GOAL_TITLE_LENGTH) }}")
                append("Do not repeat rejected recommendations.")
            }
        }.trim()
    }

    private fun buildPromptContext(
        task: AiPromptTaskConfig,
        program: UniversalProgramDefinition,
        records: List<UserRecord>,
        analytics: AnalyticsState,
        anamnesis: String,
        profileFacts: List<AiProfileFactEntity>,
        checklist: List<AiGoalChecklistItem>,
        goals: List<GoalEntity>,
        question: String?,
        locale: Locale
    ) = buildJsonObject {
        put(program.localization.aiPromptLocaleField, locale.toLanguageTag())
        if ("patientContext" in task.variables) {
            put("patientContext", patientContext(program, anamnesis, profileFacts))
        }
        if ("goalContext" in task.variables) {
            put("goalContext", goalContext(goals, checklist))
        }
        if ("analyticsStatus" in task.variables) {
            putJsonObject("analyticsStatus") {
                put("recordCount", analytics.dataSummary.recordCount)
                put("analysisLevel", analytics.analysisLevel.name)
                put("hasEnoughData", analytics.hasDashboardData())
                put("findingCount", analytics.allFindings.size)
                put("riskFindingCount", analytics.allFindings.count { it.severity == FindingSeverity.Risk })
                put("inputRecordCount", records.size)
            }
        }
        if ("keyMetrics" in task.variables) {
            putJsonArray("keyMetrics") {
                analytics.dashboardMetrics.take(MAX_METRICS).forEach { metric ->
                    add(metric.toBriefJson())
                }
            }
        }
        if ("priorityFindings" in task.variables) {
            putJsonArray("priorityFindings") {
                selectedFindings(analytics, task).forEachIndexed { index, finding ->
                    add(finding.toBriefJson(index + 1))
                }
            }
        }
        if ("userMessage" in task.variables) {
            question?.trim()?.take(MAX_QUESTION_LENGTH)?.takeIf { it.isNotBlank() }?.let {
                put("userMessage", it)
            }
        }
    }.toString()

    private fun patientContext(
        program: UniversalProgramDefinition,
        anamnesis: String,
        facts: List<AiProfileFactEntity>
    ) = buildJsonObject {
        anamnesis.trim().take(MAX_PATIENT_FIELD_LENGTH).takeIf { it.isNotBlank() }?.let {
            put("anamnesis", it)
        }
        val values = facts
            .filter { it.value.isNotBlank() }
            .associate { it.key to it.value.trim().take(MAX_PATIENT_FIELD_LENGTH) }
        // Program-driven mapping: each onboarding question stores its answer under its contextKey.
        val onboarding = program.aiOnboardingQuestions
        if (onboarding.isNotEmpty()) {
            onboarding.forEach { question ->
                val value = values[question.id] ?: return@forEach
                when (question.answerType) {
                    AiOnboardingAnswerType.YesNo -> when (value.normalizedAnswer()) {
                        "yes" -> put(question.contextKey, true)
                        "no" -> put(question.contextKey, false)
                        else -> put(question.contextKey, value)
                    }
                    else -> put(question.contextKey, value)
                }
            }
            return@buildJsonObject
        }
        // Legacy hardcoded mapping for programs without onboarding questions (e.g. blood pressure).
        values[AiConversationContract.PROFILE_MAIN_GOAL]?.let { put("goal", it) }
        values[AiConversationContract.PROFILE_AGE]?.let { put("age", it) }
        values[AiConversationContract.PROFILE_GENDER]?.let { put("sex", it) }
        values[AiConversationContract.PROFILE_LIMITATIONS]?.let { put("limitations", it) }
        values[AiConversationContract.PROFILE_DAILY_MOTIVATION]?.let { value ->
            when (value.normalizedAnswer()) {
                "yes" -> put("wantsDailyMotivation", true)
                "no" -> put("wantsDailyMotivation", false)
                else -> put("motivationText", value)
            }
        }
        values[AiConversationContract.PROFILE_ROUTINE_OR_TREATMENT]?.let { put("treatmentOrRoutine", it) }
        values[AiConversationContract.PROFILE_TRACKING_TIME]?.let { put("trackingSchedule", it) }
        values[AiConversationContract.PROFILE_TRACKED_TRIGGERS]?.let { put("trackedTriggers", it) }
    }

    private fun goalContext(
        goals: List<GoalEntity>,
        checklist: List<AiGoalChecklistItem>
    ) = buildJsonObject {
        if (goals.isEmpty()) {
            val openGoals = checklist.filterNot { it.done }.take(MAX_OPEN_GOALS)
            if (openGoals.isNotEmpty()) {
                putJsonArray("active") {
                    openGoals.forEach { goal -> add(goal.title.take(MAX_GOAL_TITLE_LENGTH)) }
                }
            }
            return@buildJsonObject
        }
        goalListJson(goals.filter { it.enabled && it.status in ACTIVE_GOAL_STATUSES }, MAX_OPEN_GOALS)
            .takeIf { it.isNotEmpty() }
            ?.let { put("active", it) }
        goalListJson(goals.filter { it.enabled && it.status == AiGoalStatus.RECOMMENDED }, MAX_GOAL_HISTORY)
            .takeIf { it.isNotEmpty() }
            ?.let { put("recommended", it) }
        goalListJson(goals.filter { it.status == AiGoalStatus.ACHIEVED }, MAX_GOAL_HISTORY)
            .takeIf { it.isNotEmpty() }
            ?.let { put("achievedRecent", it) }
        goalListJson(goals.filter { it.status == AiGoalStatus.REJECTED || it.status == AiGoalStatus.DELETED }, MAX_GOAL_HISTORY)
            .takeIf { it.isNotEmpty() }
            ?.let { put("rejectedRecent", it) }
    }

    private fun StatisticMetric.toBriefJson() = buildJsonObject {
        put("id", id)
        put("label", label.take(MAX_METRIC_LABEL_LENGTH))
        put("value", value)
        put("valueKind", valueKind)
        unit?.takeIf { it.isNotBlank() }?.let { put("unit", it) }
        put("role", role.name)
        labelKey?.let { put("labelKey", it) }
        unitKey?.let { put("unitKey", it) }
    }

    private val StatisticMetric.valueKind: String
        get() = when {
            role == StatisticRole.Percent || unit == "%" || id.contains("adherence", ignoreCase = true) -> "percent"
            unit.equals("mmHg", ignoreCase = true) -> "pressure"
            unit.equals("mg", ignoreCase = true) -> "medicationDose"
            role == StatisticRole.Count -> "count"
            else -> "measurement"
        }

    private fun Finding.toBriefJson(index: Int) = buildJsonObject {
        put("index", index)
        put("severity", severity.name.lowercase())
        put("finding", message.compact(MAX_FINDING_TEXT_LENGTH))
        put("basis", basis.compact(MAX_FINDING_BASIS_LENGTH))
        evidence.firstOrNull()?.let { metric ->
            putJsonObject("measure") {
                put("kind", metric.valueKind)
                put("value", metric.value)
                metric.unit?.takeIf { it.isNotBlank() }?.let { put("unit", it) }
            }
        }
    }

    private fun selectedFindings(analytics: AnalyticsState, task: AiPromptTaskConfig): List<Finding> {
        val limit = if (task.taskId == AiPromptTaskId.PERIOD_ANALYSIS) MAX_ANALYSIS_FINDINGS else MAX_CHAT_FINDINGS
        val prioritized = analytics.allFindings.sortedWith(
            compareBy<Finding> { it.severity.priority }
                .thenBy { it.stableKey }
        )
        return prioritized.take(limit)
    }

    private fun languageInstruction(locale: Locale): String {
        val tag = locale.toLanguageTag()
        val displayLanguage = locale.getDisplayLanguage(locale).ifBlank { tag }
        return AiPromptConfig.languagePolicy
            .replace("{languageName}", "$displayLanguage ($tag)")
    }

    private fun grammarFor(task: AiPromptTaskConfig, analytics: AnalyticsState): String = when (task.taskId) {
        AiPromptTaskId.PERIOD_ANALYSIS -> AiResponseGrammar.build(
            allowInsufficientData = analytics.dataSummary.recordCount < analytics.dataSummary.minRecordsForDashboard
        )
        else -> AiQuestionResponseGrammar.gbnf
    }

    private fun String.normalizedAnswer(): String {
        val normalized = lowercase().trim().replace(Regex("[.!?\\s]+"), " ")
        return when (normalized) {
            "yes", "y", "true", "ok", "\u0434\u0430", "\u0430\u0433\u0430" -> "yes"
            "no", "n", "false", "\u043d\u0435\u0442" -> "no"
            else -> normalized
        }
    }

    private fun goalListJson(
        goals: List<GoalEntity>,
        limit: Int
    ) = buildJsonArray {
        val selected = goals.sortedByDescending { it.updatedAt }.take(limit)
        selected.forEach { goal ->
            add(buildJsonObject {
                put("title", goal.title.take(MAX_GOAL_TITLE_LENGTH))
                goal.description.take(MAX_GOAL_DESCRIPTION_LENGTH).takeIf { it.isNotBlank() }?.let {
                    put("description", it)
                }
                put("status", goal.status)
                put("source", goal.source)
            })
        }
    }

    private fun String.compact(maxLength: Int): String =
        trim().replace(Regex("\\s+"), " ").take(maxLength).trim()

    private fun AnalyticsState.hasDashboardData(): Boolean {
        return dataSummary.recordCount >= dataSummary.minRecordsForDashboard
    }

    private val FindingSeverity.priority: Int
        get() = when (this) {
            FindingSeverity.Risk -> 0
            FindingSeverity.Neutral -> 1
            FindingSeverity.Positive -> 2
        }

    companion object {
        private const val MAX_QUESTION_LENGTH = 240
        private const val MAX_PATIENT_FIELD_LENGTH = 120
        private const val MAX_OPEN_GOALS = 3
        private const val MAX_GOAL_HISTORY = 3
        private const val MAX_GOAL_TITLE_LENGTH = 70
        private const val MAX_GOAL_DESCRIPTION_LENGTH = 120
        private const val MAX_METRIC_LABEL_LENGTH = 60
        private const val MAX_METRICS = 4
        private const val MAX_ANALYSIS_FINDINGS = 4
        private const val MAX_CHAT_FINDINGS = 2
        private const val MAX_FINDING_TEXT_LENGTH = 120
        private const val MAX_FINDING_BASIS_LENGTH = 90
        private val ACTIVE_GOAL_STATUSES = setOf(AiGoalStatus.ACCEPTED, AiGoalStatus.SCHEDULED)
    }
}
