package com.medmonitoring.core.ai

object AiConversationContract {
    const val PROFILE_MAIN_GOAL = "main_goal"
    const val PROFILE_AGE = "age"
    const val PROFILE_GENDER = "gender"
    const val PROFILE_LIMITATIONS = "limitations"
    const val PROFILE_DAILY_MOTIVATION = "daily_motivation"
    const val PROFILE_ROUTINE_OR_TREATMENT = "routine_or_treatment"
    const val PROFILE_TRACKING_TIME = "tracking_time"
    const val PROFILE_TRACKED_TRIGGERS = "tracked_triggers"

    val onboardingKeys = listOf(
        PROFILE_MAIN_GOAL,
        PROFILE_AGE,
        PROFILE_GENDER,
        PROFILE_LIMITATIONS,
        PROFILE_DAILY_MOTIVATION,
        PROFILE_ROUTINE_OR_TREATMENT,
        PROFILE_TRACKING_TIME,
        PROFILE_TRACKED_TRIGGERS
    )
}
