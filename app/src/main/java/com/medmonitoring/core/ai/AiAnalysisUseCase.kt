package com.medmonitoring.core.ai

import android.util.Log
import com.medmonitoring.core.analytics.BaseAnalysisResult
import com.medmonitoring.core.analytics.AnalyticsEngine
import com.medmonitoring.core.analytics.ProgramRecordMapper
import com.medmonitoring.core.domain.model.ProgramAnalyticsSchema
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.domain.model.FindingSeverity
import com.medmonitoring.core.domain.model.AnalyticsState
import com.medmonitoring.core.domain.model.Finding
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.domain.repository.EventRepository
import com.medmonitoring.core.storage.db.MedDatabase
import com.medmonitoring.core.storage.entity.AiReportEntity
import com.medmonitoring.core.domain.model.AnalysisLevel
import com.medmonitoring.core.util.StringProvider
import com.medmonitoring.app.R
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AiAnalysisResult {
    data class Unavailable(val reason: String) : AiAnalysisResult
    data class Ready(val response: AiResponseJson) : AiAnalysisResult
}

@Singleton
class AiAnalysisUseCase @Inject constructor(
    private val db: MedDatabase,
    private val repository: EventRepository,
    private val analyticsEngine: AnalyticsEngine,
    private val recordMapper: ProgramRecordMapper<UserRecord>,
    private val program: UniversalProgramDefinition,
    private val analyticsSchema: ProgramAnalyticsSchema,
    private val promptBuilder: AiPromptBuilder,
    private val aiEngine: AiEngine,
    private val stringProvider: StringProvider
) {
    suspend fun run(baseAnalysis: BaseAnalysisResult): AiAnalysisResult {
        Log.i("AiAnalysis", "Requested AI analysis records=${baseAnalysis.recordCount}")
        val settings = db.aiSettingsDao().get() ?: return AiAnalysisResult.Unavailable(
            stringProvider.getString(R.string.ai_analysis_not_configured)
        )
        Log.i("AiAnalysis", "Settings enabled=${settings.enabled} mode=${settings.mode}")
        if (!settings.enabled) {
            return AiAnalysisResult.Unavailable(stringProvider.getString(R.string.ai_assistant_off))
        }
        if (settings.mode != AiSettingsContract.MODE_LOCAL_MODEL) {
            return AiAnalysisResult.Unavailable(
                stringProvider.getString(R.string.ai_mode_basic_selected)
            )
        }
        val readyModel = db.aiModelDao().getModels().firstOrNull {
            it.status == AiModelStatus.READY.name && it.localPath != null
        }
        Log.i("AiAnalysis", "Ready model=${readyModel?.id} path=${readyModel?.localPath}")
        if (readyModel == null) {
            return AiAnalysisResult.Unavailable(
                stringProvider.getString(
                    R.string.ai_local_model_not_installed,
                    baseAnalysis.recordCount,
                    baseAnalysis.analysisLevel.localizedName()
                )
            )
        }
        val spec = AiModelRegistry.specFor(readyModel.id)
        if (spec == null) {
            saveFailedReport(baseAnalysis, "unknown_model")
            return AiAnalysisResult.Unavailable(
                stringProvider.getString(R.string.ai_model_not_in_registry, readyModel.displayName)
            )
        }
        val records = repository.getRecords()
        val analytics = analyticsEngine.calculate(recordMapper.mapAll(records), analyticsSchema)
        val currentChecklist = db.aiProgramStateDao().getToday()
            ?.checklistJson
            ?.let(AiJsonCodec::checklistFromJson)
            .orEmpty()
        val goals = db.goalDao().getForProgram(program.programId)
        val request = promptBuilder.buildDailyRequest(
            program = program,
            records = records,
            analytics = analytics,
            anamnesis = db.anamnesisDao().get()?.text.orEmpty(),
            profileFacts = db.aiProfileFactDao().getFacts(),
            checklist = currentChecklist,
            goals = goals,
            model = spec,
            locale = stringProvider.currentLocale()
        )
        Log.i(
            "AiAnalysis",
            "Prompt analytics recordCount=${analytics.dataSummary.recordCount} findings=${analytics.allFindings.size} riskFindings=${analytics.allFindings.count { it.severity == FindingSeverity.Risk }} promptChars=${request.prompt.length}"
        )
        Log.d("AiPrompt", request.prompt)
        val output = runCatching { aiEngine.generate(request) }.getOrElse { error ->
            Log.e("AiAnalysis", "Generation failed model=${spec.id}", error)
            saveFailedReport(baseAnalysis, "runtime_error:${error.message.orEmpty()}")
            return AiAnalysisResult.Unavailable(
                stringProvider.getString(R.string.ai_model_generation_failed)
            )
        }
        val decision = AiJsonCodec.parseDailyDecision(output)
        if (decision == null) {
            Log.w("AiAnalysis", "Invalid JSON from model=${spec.id}")
            saveFailedReport(baseAnalysis, "invalid_json")
            return AiAnalysisResult.Unavailable(
                stringProvider.getString(R.string.ai_model_invalid_format)
            )
        }
        val response = renderDailyResponse(decision, analytics)
        saveSuccessReport(baseAnalysis, response)
        Log.i("AiAnalysis", "AI analysis succeeded model=${spec.id}")
        return AiAnalysisResult.Ready(response)
    }

    suspend fun answerQuestion(question: String, baseAnalysis: BaseAnalysisResult): AiAnalysisResult {
        val trimmed = question.trim()
        if (trimmed.isBlank()) return AiAnalysisResult.Unavailable(stringProvider.getString(R.string.ai_question_empty))
        val settings = db.aiSettingsDao().get() ?: return AiAnalysisResult.Unavailable(
            stringProvider.getString(R.string.ai_analysis_not_configured)
        )
        if (!settings.enabled) return AiAnalysisResult.Unavailable(stringProvider.getString(R.string.ai_assistant_off))
        if (settings.mode != AiSettingsContract.MODE_LOCAL_MODEL) {
            return AiAnalysisResult.Unavailable(stringProvider.getString(R.string.ai_question_needs_local_mode))
        }
        val readyModel = db.aiModelDao().getModels().firstOrNull {
            it.status == AiModelStatus.READY.name && it.localPath != null
        } ?: return AiAnalysisResult.Unavailable(
            stringProvider.getString(R.string.ai_local_model_not_installed, baseAnalysis.recordCount, baseAnalysis.analysisLevel.localizedName())
        )
        val spec = AiModelRegistry.specFor(readyModel.id)
            ?: return AiAnalysisResult.Unavailable(
                stringProvider.getString(R.string.ai_model_not_in_registry, readyModel.displayName)
            )
        val records = repository.getRecords()
        val analytics = analyticsEngine.calculate(recordMapper.mapAll(records), analyticsSchema)
        val currentChecklist = db.aiProgramStateDao().getToday()
            ?.checklistJson
            ?.let(AiJsonCodec::checklistFromJson)
            .orEmpty()
        val goals = db.goalDao().getForProgram(program.programId)
        val request = promptBuilder.buildQuestionRequest(
            question = trimmed,
            program = program,
            records = records,
            analytics = analytics,
            anamnesis = db.anamnesisDao().get()?.text.orEmpty(),
            profileFacts = db.aiProfileFactDao().getFacts(),
            checklist = currentChecklist,
            goals = goals,
            model = spec,
            locale = stringProvider.currentLocale()
        )
        Log.i(
            "AiAnalysis",
            "Question prompt analytics recordCount=${analytics.dataSummary.recordCount} findings=${analytics.allFindings.size} promptChars=${request.prompt.length}"
        )
        Log.d("AiPrompt", request.prompt)
        val output = runCatching { aiEngine.generate(request) }.getOrElse { error ->
            Log.e("AiAnalysis", "Question generation failed model=${spec.id}", error)
            return AiAnalysisResult.Unavailable(stringProvider.getString(R.string.ai_question_generation_failed))
        }
        val response = AiJsonCodec.parseQuestionResponse(output)
            ?: return AiAnalysisResult.Unavailable(stringProvider.getString(R.string.ai_question_invalid_format))
        saveSuccessReport(baseAnalysis, response)
        return AiAnalysisResult.Ready(response)
    }

    private fun AnalysisLevel.localizedName(): String = when (this) {
        AnalysisLevel.NONE -> stringProvider.getString(R.string.level_none)
        AnalysisLevel.BASIC_3_9 -> stringProvider.getString(R.string.level_basic)
        AnalysisLevel.COMPARATIVE_10_30 -> stringProvider.getString(R.string.level_comparative)
        AnalysisLevel.ADVANCED_31_PLUS -> stringProvider.getString(R.string.level_advanced)
    }

    private suspend fun saveSuccessReport(baseAnalysis: BaseAnalysisResult, response: AiResponseJson) {
        val now = System.currentTimeMillis()
        db.aiReportDao().upsert(
            AiReportEntity(
                id = UUID.randomUUID().toString(),
                createdAt = now,
                status = "READY",
                inputJson = inputJson(baseAnalysis),
                outputJson = AiJsonCodec.responseToJson(response)
            )
        )
    }

    private fun renderDailyResponse(decision: AiDailyDecision, analytics: AnalyticsState): AiResponseJson {
        val now = System.currentTimeMillis()
        val finding = decision.findingIndex
            .takeIf { it > 0 }
            ?.let { analytics.allFindings.getOrNull(it - 1) }
        val summary = dailySummary(decision, analytics, finding)
        val recommendation = decision.recommendation.cleanModelText(MAX_RECOMMENDATION_LENGTH)
            .let { if (it.isLikelyEnglishForCurrentLocale()) "" else it }
            .ifBlank { fallbackRecommendation(analytics, finding) }
        val checklist = decision.checklist
            .map { it.cleanModelText(MAX_GOAL_TITLE_LENGTH) }
            .filter { it.isNotBlank() }
            .filterNot { it.isLikelyEnglishForCurrentLocale() }
            .distinct()
            .take(1)
            .ifEmpty { listOf(stringProvider.getString(R.string.ai_fallback_goal_title)) }
            .mapIndexed { index, title ->
                AiGoalChecklistItem(
                    id = "ai-action-$now-$index",
                    title = title,
                    done = false,
                    createdAt = now,
                    completedAt = null
                )
            }
        return AiResponseJson(
            slider = listOf(
                AiSliderItem("progress", stringProvider.getString(R.string.ai_summary_title), summary),
                AiSliderItem("focus", stringProvider.getString(R.string.ai_focus_title), recommendation)
            ),
            messages = listOf(
                AiResponseMessage("summary", summary),
                AiResponseMessage("recommendation", recommendation)
            ),
            checklist = checklist,
            notification = AiNotification(
                title = if (decision.alert) {
                    stringProvider.getString(R.string.ai_check_readings)
                } else {
                    stringProvider.getString(R.string.ai_analysis_ready_title)
                },
                body = summary
            )
        )
    }

    private fun dailySummary(
        decision: AiDailyDecision,
        analytics: AnalyticsState,
        finding: Finding?
    ): String {
        if (!analytics.hasDashboardData()) {
            return stringProvider.getString(
                R.string.ai_insufficient_data,
                analytics.dataSummary.recordCount,
                analytics.dataSummary.minRecordsForDashboard
            )
        }
        val records = analytics.dataSummary.recordCount
        val riskCount = analytics.allFindings.count { it.severity == FindingSeverity.Risk }
        if (finding != null) {
            return stringProvider.getString(R.string.ai_main_focus, records, finding.localizedTitle().lowercase(), riskCount)
        }
        return stringProvider.getString(R.string.ai_no_stable_pattern, records)
    }

    private fun dailyFindingText(finding: Finding): String {
        val evidence = finding.evidence.take(2).joinToString(", ") { metric ->
            listOfNotNull(metric.localizedLabel(), metric.value, metric.unit).joinToString(" ")
        }
        return listOf(
            finding.localizedMessage(),
            finding.localizedBasis(),
            evidence.takeIf { it.isNotBlank() }?.let { stringProvider.getString(R.string.ai_finding_data, it) }
        ).filterNotNull().joinToString(" ")
    }

    private fun noFindingText(analytics: AnalyticsState): String {
        val metrics = analytics.dashboardMetrics.take(4).joinToString(", ") { metric ->
            listOfNotNull(metric.localizedLabel(), metric.value, metric.unit).joinToString(" ")
        }
        return stringProvider.getString(R.string.ai_no_stable_findings, metrics)
    }

    private fun String.isLikelyEnglishForCurrentLocale(): Boolean {
        val language = stringProvider.currentLocale().language.lowercase(Locale.ROOT)
        if (language == "en") return false
        val letters = filter(Char::isLetter)
        if (letters.length < 8) return false
        val latin = letters.count { it in 'A'..'Z' || it in 'a'..'z' }
        if (latin.toDouble() / letters.length < 0.85) return false
        val words = lowercase(Locale.ROOT).split(Regex("[^a-z]+")).filter { it.length > 2 }.toSet()
        return words.any { it in ENGLISH_OUTPUT_MARKERS }
    }

    private fun fallbackRecommendation(
        analytics: AnalyticsState,
        finding: Finding?
    ): String {
        val hasRisk = finding?.severity == FindingSeverity.Risk || analytics.allFindings.any { it.severity == FindingSeverity.Risk }
        return if (hasRisk) {
            stringProvider.getString(R.string.ai_fallback_recommendation_risk)
        } else {
            stringProvider.getString(R.string.ai_fallback_recommendation_normal)
        }
    }

    private suspend fun saveFailedReport(baseAnalysis: BaseAnalysisResult, reason: String) {
        val now = System.currentTimeMillis()
        db.aiReportDao().upsert(
            AiReportEntity(
                id = UUID.randomUUID().toString(),
                createdAt = now,
                status = "ERROR",
                inputJson = inputJson(baseAnalysis, reason),
                outputJson = null
            )
        )
    }

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
        "msg_frequent" -> stringProvider.getString(
            R.string.msg_frequent,
            leftGroupLabel.orEmpty().humanizeAnalyticsToken(),
            leftGroupSize ?: 0,
            recordCount
        )
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
        "basis_meds" -> stringProvider.getString(R.string.basis_meds, leftGroupSize.orZero() + rightGroupSize.orZero())
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

    private fun String.cleanModelText(maxLength: Int): String =
        trim()
            .replace(Regex("\\s+"), " ")
            .trim('"', '\'', '.', ' ')
            .take(maxLength)
            .trim()

    private fun com.medmonitoring.core.domain.model.StatisticMetric.localizedLabel(): String {
        val key = labelKey ?: id
        return stringProvider.getStringByName(key, label)
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun String.humanizeAnalyticsToken(): String =
        lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').let { snake ->
            stringProvider.getStringByName(
                snake,
                stringProvider.getStringByName(
                    "tag_$snake",
                    stringProvider.getStringByName(
                        "group_$snake",
                        replace("_", " ").replaceFirstChar { it.uppercase() }
                    )
                )
            )
        }

    private fun inputJson(baseAnalysis: BaseAnalysisResult, error: String? = null): String = buildJsonObject {
        put("programId", baseAnalysis.programId)
        put(program.localization.analyticsLocaleField, stringProvider.currentLocale().toLanguageTag())
        put("recordCount", baseAnalysis.recordCount)
        put("analysisLevel", baseAnalysis.analysisLevel.name)
        put("findingCount", baseAnalysis.findings.size)
        put("riskFindingCount", baseAnalysis.findings.count { it.severity == FindingSeverity.Risk })
        put("generatedAt", baseAnalysis.generatedAt.toString())
        error?.let { put("error", it) }
    }.toString()

    private fun AnalyticsState.hasDashboardData(): Boolean {
        return dataSummary.recordCount >= dataSummary.minRecordsForDashboard
    }

    private companion object {
        const val MAX_RECOMMENDATION_LENGTH = 180
        const val MAX_GOAL_TITLE_LENGTH = 70
        val ENGLISH_OUTPUT_MARKERS = setOf(
            "add",
            "avoid",
            "blood",
            "check",
            "continue",
            "daily",
            "data",
            "doctor",
            "medication",
            "measure",
            "pressure",
            "record",
            "reduce",
            "review",
            "take",
            "today",
            "track"
        )
    }
}
