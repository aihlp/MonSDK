package com.medmonitoring

import com.medmonitoring.core.ai.AiGoalSource
import com.medmonitoring.core.ai.AiGoalStatus
import com.medmonitoring.core.ai.AiModelRegistry
import com.medmonitoring.core.ai.AiPromptBuilder
import com.medmonitoring.core.domain.model.AnalysisLevel
import com.medmonitoring.core.domain.model.AnalyticsState
import com.medmonitoring.core.domain.model.DataSummary
import com.medmonitoring.core.storage.entity.AiProfileFactEntity
import com.medmonitoring.core.storage.entity.GoalEntity
import com.medmonitoring.program.diabetes.DiabetesProgramModule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AiPromptBuilderContextTest {
    private val program = DiabetesProgramModule.program
    private val builder = AiPromptBuilder()

    @Test
    fun diabetesOnboardingFactsUseProgramContextKeysInPrompt() {
        val prompt = builder.buildDailyRequest(
            program = program,
            records = emptyList(),
            analytics = emptyAnalytics(),
            anamnesis = "Type 2 diabetes self-monitoring.",
            profileFacts = listOf(
                fact("main_goal", "Reduce morning spikes"),
                fact("age", "54"),
                fact("diabetes_treatment_plan", "Metformin and basal insulin"),
                fact("glucose_measurement_schedule", "Fasting and after dinner"),
                fact("hypo_hyper_symptoms", "Shaking and blurred vision"),
                fact("food_activity_triggers", "Late meals and poor sleep"),
                fact("limitations", "Follow clinician glucose plan")
            ),
            model = AiModelRegistry.recommendedModels.first(),
            locale = Locale.ENGLISH
        ).prompt

        assertTrue(prompt.contains("\"goal\":\"Reduce morning spikes\""))
        assertTrue(prompt.contains("\"age\":\"54\""))
        assertTrue(prompt.contains("\"treatmentPlan\":\"Metformin and basal insulin\""))
        assertTrue(prompt.contains("\"measurementSchedule\":\"Fasting and after dinner\""))
        assertTrue(prompt.contains("\"symptomsToWatch\":\"Shaking and blurred vision\""))
        assertTrue(prompt.contains("\"trackedTriggers\":\"Late meals and poor sleep\""))
        assertTrue(prompt.contains("\"limitations\":\"Follow clinician glucose plan\""))
        assertFalse(prompt.contains("programFacts"))
        assertFalse(prompt.contains("Lisinopril", ignoreCase = true))
    }

    @Test
    fun promptIncludesGoalLifecycleAndSuppressedRecommendationHistory() {
        val prompt = builder.buildDailyRequest(
            program = program,
            records = emptyList(),
            analytics = emptyAnalytics(),
            anamnesis = "",
            goals = listOf(
                goal("active", "Check fasting glucose", AiGoalStatus.ACCEPTED),
                goal("achieved", "Log bedtime glucose", AiGoalStatus.ACHIEVED, completedAt = 1000L),
                goal("rejected", "Add another lunch reminder", AiGoalStatus.REJECTED),
                goal("deleted", "Track the same late snack", AiGoalStatus.DELETED, enabled = false)
            ),
            model = AiModelRegistry.recommendedModels.first(),
            locale = Locale.ENGLISH
        ).prompt

        assertTrue(prompt.contains("active:"))
        assertTrue(prompt.contains("Check fasting glucose"))
        assertTrue(prompt.contains("achieved:"))
        assertTrue(prompt.contains("Log bedtime glucose"))
        assertTrue(prompt.contains("rejected:"))
        assertTrue(prompt.contains("Add another lunch reminder"))
        assertTrue(prompt.contains("Track the same late snack"))
        assertTrue(prompt.contains("Do not repeat rejected recommendations."))
    }

    private fun emptyAnalytics() = AnalyticsState(
        dashboardMetrics = emptyList(),
        showcaseFindings = emptyList(),
        allFindings = emptyList(),
        dataSummary = DataSummary(
            recordCount = 0,
            metricCoverage = emptyMap(),
            eventCoverage = emptyMap(),
            dimensionCoverage = emptyMap(),
            minRecordsForDashboard = 3,
            minRecordsForFindings = 5,
            minGroupSizeForComparison = 3
        ),
        analysisLevel = AnalysisLevel.NONE
    )

    private fun fact(key: String, value: String) = AiProfileFactEntity(key, key, value, 1L)

    private fun goal(
        id: String,
        title: String,
        status: String,
        enabled: Boolean = true,
        completedAt: Long? = null
    ) = GoalEntity(
        id = id,
        programId = program.programId,
        title = title,
        description = "Description for $title",
        targetMetricKey = null,
        targetValue = null,
        unit = null,
        progressValue = null,
        enabled = enabled,
        status = status,
        source = AiGoalSource.AI_RECOMMENDATION,
        sourceRef = "test-$id",
        completedAt = completedAt,
        createdAt = 1L,
        updatedAt = 1L
    )
}
