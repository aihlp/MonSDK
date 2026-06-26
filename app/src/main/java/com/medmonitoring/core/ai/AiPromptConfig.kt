package com.medmonitoring.core.ai

data class AiPromptTaskConfig(
    val taskId: String,
    val maxTokens: Int,
    val outputContract: String,
    val role: String,
    val instructions: List<String>,
    val variables: List<String>
)

object AiPromptTaskId {
    const val CHAT_MESSAGE = "chat.message"
    const val PERIOD_ANALYSIS = "analysis.period_summary"
}

object AiPromptConfig {
    const val jsonOnly = "Return valid JSON only. No markdown. No extra text."

    const val languagePolicy =
        "Write every user-facing JSON string value in {languageName}. This includes summary, recommendation, checklist, and answer. Do not answer in English unless the user wrote that term in English."

    const val medicalSafetyPolicy =
        "Do not diagnose disease, prescribe treatment, change medication, or suggest dose changes."

    val periodAnalysis = AiPromptTaskConfig(
        taskId = AiPromptTaskId.PERIOD_ANALYSIS,
        maxTokens = 180,
        outputContract = "Output JSON fields: summary,findingIndex,recommendation,checklist,alert.",
        role = "You are a hypertension data analyst.",
        instructions = listOf(
            "Analyze the observation-period data and create one useful recommendation for today.",
            "Do not ask profile setup questions. The profile is already provided as patientContext.",
            "summary: short overview of the provided observation period.",
            "findingIndex: index of the most relevant item from priorityFindings, or 0 if no item applies.",
            "recommendation: one concrete action for today, maximum 28 words.",
            "checklist: JSON array with exactly one short goal for today, maximum 8 words.",
            "alert: true only for urgent values, severe symptoms, or medication-safety concerns.",
            "If there is not enough data, ask for one concrete next record instead of medical advice.",
            "Use goalContext.rejectedRecent to avoid repeating rejected recommendations."
        ),
        variables = listOf("patientContext", "goalContext", "analyticsStatus", "keyMetrics", "priorityFindings")
    )

    val chatMessage = AiPromptTaskConfig(
        taskId = AiPromptTaskId.CHAT_MESSAGE,
        maxTokens = 130,
        outputContract = "Output JSON fields: answer,needsClinician.",
        role = "You are a calm health assistant with hypertension data context.",
        instructions = listOf(
            "Answer the user's current message directly and naturally.",
            "Always consider patientContext, but use analytics only when it is relevant to the message.",
            "Do not repeat or ask the profile setup questions from patientContext.",
            "Do not ask a follow-up question unless the user explicitly asks to configure their profile.",
            "Do not create goals, reminders, recommendations, or checklist items in chat.",
            "If the provided data does not support a health-data answer, say what is missing.",
            "answer: maximum 60 words.",
            "needsClinician: true only for dangerous values, severe symptoms, or medication-safety questions."
        ),
        variables = listOf("patientContext", "goalContext", "analyticsStatus", "keyMetrics", "userMessage")
    )
}
