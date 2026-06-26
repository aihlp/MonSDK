package com.medmonitoring

import com.medmonitoring.core.ai.AiConversationContract
import com.medmonitoring.core.ai.AiPromptBuilder
import com.medmonitoring.core.ai.AiQuestionResponseGrammar
import com.medmonitoring.core.ai.AiResponseGrammar
import com.medmonitoring.core.ai.AiGoalSource
import com.medmonitoring.core.ai.AiGoalStatus
import com.medmonitoring.core.analytics.AnalyticsEngine
import com.medmonitoring.core.domain.model.ActionEvent
import com.medmonitoring.core.domain.model.DataQuality
import com.medmonitoring.core.domain.model.Observation
import com.medmonitoring.core.domain.model.RecordSource
import com.medmonitoring.core.domain.model.SourceType
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.storage.entity.AiProfileFactEntity
import com.medmonitoring.core.storage.entity.GoalEntity
import com.medmonitoring.program.bloodpressure.BloodPressureDefinitions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

class AiAnalysisWorkerTest {
    @Test
    fun promptContainsSafetyRulesAndStrictJsonContract() {
        val analytics = AnalyticsEngine().calculate(emptyList(), BloodPressureDefinitions.analyticsConfig)

        val prompt = AiPromptBuilder().buildDailyPrompt(
            program = BloodPressureDefinitions.program,
            records = emptyList(),
            analytics = analytics,
            anamnesis = "No known restrictions.",
            profileFacts = emptyList(),
            checklist = emptyList()
        )

        assertTrue(prompt.contains("Do not diagnose"))
        assertTrue(prompt.contains("JSON only"))
        assertTrue(prompt.contains("Output JSON fields: summary,findingIndex,recommendation,checklist,alert"))
        assertTrue(prompt.contains("findingIndex"))
        assertTrue(AiResponseGrammar.gbnf.contains("\"\\\"summary\\\"\""))
        assertTrue(AiResponseGrammar.gbnf.contains("\"\\\"findingIndex\\\"\""))
        assertTrue(AiResponseGrammar.gbnf.contains("\"\\\"recommendation\\\"\""))
        assertTrue(AiResponseGrammar.gbnf.contains("\"\\\"checklist\\\"\""))
        assertTrue(AiResponseGrammar.gbnf.contains("\"\\\"alert\\\"\""))
        assertTrue(prompt.contains("\"analyticsStatus\""))
        assertTrue(prompt.contains("\"keyMetrics\""))
        assertTrue(prompt.contains("\"priorityFindings\""))
        assertTrue(prompt.contains("\"patientContext\""))
        assertTrue(prompt.contains("\"anamnesis\":\"No known restrictions.\""))
        assertFalse(prompt.contains("\"coverage\""))
        assertFalse(prompt.contains("\"unavailableMetrics\""))
        assertFalse(prompt.contains("\"availableContextSources\""))
        assertFalse(prompt.contains("\"appContext\""))
        assertFalse(prompt.contains("\"evidence\""))
        assertFalse(prompt.contains("\"recentRecords\""))
        assertTrue("Daily prompt should stay compact for phone inference, was ${prompt.length} chars", prompt.length < 3000)
        assertFalse(prompt.contains(BloodPressureDefinitions.program.programId))
    }

    @Test
    fun promptMarksRequestedLanguageAndKeepsStableLocalizationKeys() {
        val analytics = AnalyticsEngine().calculate(emptyList(), BloodPressureDefinitions.analyticsConfig)

        val prompt = AiPromptBuilder().buildDailyPrompt(
            program = BloodPressureDefinitions.program,
            records = emptyList(),
            analytics = analytics,
            anamnesis = "",
            profileFacts = emptyList(),
            checklist = emptyList(),
            locale = Locale("es")
        )

        assertTrue(prompt.contains("español (es)"))
        assertTrue(prompt.contains("Write every user-facing JSON string value in español"))
        assertTrue(prompt.contains("Do not answer in English"))
        assertTrue(prompt.contains("\"locale\":\"es\""))
        assertFalse(prompt.contains("\"fallbackLocale\":\"en\""))
        assertFalse(prompt.contains("\"labelPolicy\""))
    }

    @Test
    fun promptUsesAnalyticsLayerRecordCountInsteadOfRecentRawRecordWindow() {
        val analytics = AnalyticsEngine().calculate(
            (1..5).map { record("r$it") },
            BloodPressureDefinitions.analyticsConfig
        )

        val prompt = AiPromptBuilder().buildDailyPrompt(
            program = BloodPressureDefinitions.program,
            records = emptyList(),
            analytics = analytics,
            anamnesis = "",
            profileFacts = emptyList(),
            checklist = emptyList()
        )

        assertTrue(prompt.contains("\"analyticsStatus\""))
        assertTrue(prompt.contains("\"recordCount\":5"))
        assertTrue(prompt.contains("\"analysisLevel\":\"BASIC_3_9\""))
        assertTrue(prompt.contains("\"inputRecordCount\":0"))
        assertTrue(prompt.contains("If there is not enough data"))
        assertTrue(prompt.contains("recommendation: one concrete action"))
        assertTrue(prompt.contains("checklist: JSON array with exactly one short goal"))
        assertTrue(prompt.contains("\"hasEnoughData\":true"))
        assertTrue(prompt.contains("\"findingCount\""))
        assertTrue(AiPromptBuilder().buildDailyRequest(
            program = BloodPressureDefinitions.program,
            records = emptyList(),
            analytics = analytics,
            anamnesis = "",
            profileFacts = emptyList(),
            checklist = emptyList(),
            model = com.medmonitoring.core.ai.AiModelRegistry.recommendedModels.first()
        ).maxTokens >= 140)
    }

    @Test
    fun chatPromptUsesPatientContextWithoutContentClassification() {
        val analytics = AnalyticsEngine().calculate(
            (1..8).map { record("r$it") },
            BloodPressureDefinitions.analyticsConfig
        )

        val request = AiPromptBuilder().buildQuestionRequest(
            question = "Как дела",
            program = BloodPressureDefinitions.program,
            records = emptyList(),
            analytics = analytics,
            anamnesis = "",
            profileFacts = profileFacts(),
            checklist = emptyList(),
            model = com.medmonitoring.core.ai.AiModelRegistry.recommendedModels.first(),
            locale = Locale("ru", "RU")
        )

        assertTrue(request.prompt.contains("Output JSON fields: answer,needsClinician"))
        assertTrue(request.prompt.contains("русский (ru-RU)"))
        assertTrue(request.prompt.contains("Write every user-facing JSON string value in русский"))
        assertTrue(request.prompt.contains("Do not answer in English"))
        assertTrue(request.prompt.contains("Always consider patientContext"))
        assertTrue(request.prompt.contains("Do not repeat or ask the profile setup questions"))
        assertTrue(request.prompt.contains("Do not create goals, reminders, recommendations"))
        assertTrue(request.prompt.contains("\"patientContext\""))
        assertTrue(request.prompt.contains("\"analyticsStatus\""))
        assertTrue(request.prompt.contains("\"keyMetrics\""))
        assertFalse(request.prompt.contains("\"priorityFindings\""))
        assertTrue(request.prompt.contains("\"age\":\"45\""))
        assertTrue(request.prompt.contains("\"sex\":\"мужчина\""))
        assertTrue(request.prompt.contains("\"limitations\":\"не бегать\""))
        assertTrue(request.prompt.contains("\"userMessage\":\"Как дела\""))
        assertFalse(request.prompt.contains("\"coverage\""))
        assertFalse(request.prompt.contains("\"availableContextSources\""))
        assertFalse(request.prompt.contains("\"appContext\""))
        assertFalse(request.prompt.contains("\"tagGroups\""))
        assertFalse(request.prompt.contains("\"evidence\""))
        assertTrue("Question prompt should stay compact for phone inference, was ${request.prompt.length} chars", request.prompt.length < 3000)
        assertTrue(AiQuestionResponseGrammar.gbnf.contains("\"\\\"answer\\\"\""))
        assertTrue(AiQuestionResponseGrammar.gbnf.contains("\"\\\"needsClinician\\\"\""))
        assertFalse(AiQuestionResponseGrammar.gbnf.contains("\"\\\"recommendation\\\"\""))
    }

    @Test
    fun periodAnalysisPromptIncludesPriorityFindingsAndGoalHistory() {
        val analytics = AnalyticsEngine().calculate(
            (1..5).map { record("r$it") },
            BloodPressureDefinitions.analyticsConfig
        )

        val request = AiPromptBuilder().buildDailyRequest(
            program = BloodPressureDefinitions.program,
            records = emptyList(),
            analytics = analytics,
            anamnesis = "",
            profileFacts = profileFacts(),
            checklist = emptyList(),
            goals = goals(),
            model = com.medmonitoring.core.ai.AiModelRegistry.recommendedModels.first(),
            locale = Locale("ru", "RU")
        )

        assertTrue(request.prompt.contains("\"priorityFindings\""))
        assertTrue(request.prompt.contains("\"rejectedRecent\""))
        assertTrue(request.prompt.contains("\"achievedRecent\""))
        assertFalse(request.prompt.contains("\"coverage\""))
        assertFalse(request.prompt.contains("\"availableContextSources\""))
        assertFalse(request.prompt.contains("\"tagGroups\""))
        assertFalse(request.prompt.contains("\"evidence\""))
        assertTrue("Analysis prompt should stay compact, was ${request.prompt.length} chars", request.prompt.length < 3000)
    }

    private fun profileFacts() = listOf(
        AiProfileFactEntity(AiConversationContract.PROFILE_MAIN_GOAL, AiConversationContract.PROFILE_MAIN_GOAL, "держать давление стабильным", 1L),
        AiProfileFactEntity(AiConversationContract.PROFILE_AGE, AiConversationContract.PROFILE_AGE, "45", 1L),
        AiProfileFactEntity(AiConversationContract.PROFILE_GENDER, AiConversationContract.PROFILE_GENDER, "мужчина", 1L),
        AiProfileFactEntity(AiConversationContract.PROFILE_LIMITATIONS, AiConversationContract.PROFILE_LIMITATIONS, "не бегать", 1L)
    )

    private fun goals() = listOf(
        goal("g1", "Measure after dinner", AiGoalStatus.REJECTED, AiGoalSource.AI_RECOMMENDATION, 5L),
        goal("g2", "Measure before breakfast", AiGoalStatus.ACHIEVED, AiGoalSource.AI_RECOMMENDATION, 4L),
        goal("g3", "Take evening reading", AiGoalStatus.ACCEPTED, AiGoalSource.CHAT, 3L)
    )

    private fun goal(
        id: String,
        title: String,
        status: String,
        source: String,
        updatedAt: Long
    ) = GoalEntity(
        id = id,
        programId = BloodPressureDefinitions.program.programId,
        title = title,
        description = "",
        targetMetricKey = null,
        targetValue = null,
        unit = null,
        progressValue = null,
        enabled = true,
        status = status,
        source = source,
        sourceRef = null,
        completedAt = if (status == AiGoalStatus.ACHIEVED) updatedAt else null,
        createdAt = updatedAt,
        updatedAt = updatedAt
    )

    private fun record(id: String) = UserRecord(
        id = id,
        programId = BloodPressureDefinitions.program.programId,
        timestamp = Instant.now(),
        measurements = listOf(
            Observation("systolic", 138.0, "mmHg", group = "blood_pressure"),
            Observation("diastolic", 88.0, "mmHg", group = "blood_pressure"),
            Observation("pulse", 90.0, "bpm")
        ),
        events = listOf(ActionEvent("medication", "Lisinopril", "taken", 10.0, "mg")),
        dimensions = emptyList(),
        quality = DataQuality(),
        source = RecordSource(SourceType.MANUAL),
        note = null
    )
}
