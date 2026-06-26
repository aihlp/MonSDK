package com.medmonitoring.core.ai

import com.medmonitoring.core.storage.entity.AiReportEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive

enum class AiModelStatus { AVAILABLE, DOWNLOADING, READY, ERROR }

object AiGoalStatus {
    const val RECOMMENDED = "recommended"
    const val ACCEPTED = "accepted"
    const val SCHEDULED = "scheduled"
    const val REJECTED = "rejected"
    const val ACHIEVED = "achieved"
    const val DELETED = "deleted"
}

object AiGoalSource {
    const val CHAT = "chat"
    const val SCHEDULED_REMINDER = "scheduled_reminder"
    const val AI_RECOMMENDATION = "ai_recommendation"
    const val ANALYTICS_RECOMMENDATION = "analytics_recommendation"
}

object AiMenuAction {
    const val BASIC_ANALYSIS = "basic_analysis"
    const val AI_ANALYSIS = "ai_analysis"
    const val CONFIGURE_AI = "configure_ai"
    const val SET_REMINDER = "set_reminder"
    const val RECOMMEND_GOAL = "recommend_goal"
    const val CLEAR_HISTORY = "clear_history"

    val all = setOf(
        BASIC_ANALYSIS,
        AI_ANALYSIS,
        CONFIGURE_AI,
        SET_REMINDER,
        RECOMMEND_GOAL,
        CLEAR_HISTORY
    )
}

data class AiModelSpec(
    val id: String,
    val displayName: String,
    val repo: String,
    val filename: String,
    val quantization: String,
    val sizeMb: Int,
    val minRamGb: Int,
    val recommendedRamGb: Int,
    val downloadUrl: String,
    val status: AiModelStatus = AiModelStatus.AVAILABLE,
    val notes: String
)

data class AiGoalDraft(
    val id: String,
    val title: String,
    val description: String,
    val targetMetricKey: String? = null,
    val targetValue: Double? = null,
    val unit: String? = null,
    val progressValue: Double? = null,
    val enabled: Boolean = true,
    val status: String = AiGoalStatus.ACCEPTED,
    val source: String = AiGoalSource.CHAT,
    val sourceRef: String? = null
)

data class AiGoalChecklistItem(
    val id: String,
    val title: String,
    val done: Boolean,
    val createdAt: Long,
    val completedAt: Long?
)

data class AiRecommendation(
    val title: String,
    val explanation: String,
    val checklistAction: String
)

data class AiSliderItem(
    val type: String,
    val title: String,
    val text: String
)

data class AiResponseMessage(
    val type: String,
    val text: String
)

data class AiNotification(
    val title: String,
    val body: String
)

data class AiProgramState(
    val date: String,
    val checklist: List<AiGoalChecklistItem>,
    val progressText: String,
    val motivationText: String,
    val focusText: String
)

data class AiResponseJson(
    val slider: List<AiSliderItem>,
    val messages: List<AiResponseMessage>,
    val checklist: List<AiGoalChecklistItem>,
    val notification: AiNotification?
)

data class AiDailyDecision(
    val summary: String,
    val findingIndex: Int,
    val recommendation: String,
    val checklist: List<String>,
    val alert: Boolean
)

data class AiReportPayload(
    val summary: String,
    val recommendations: List<AiRecommendation>,
    val focusAreas: List<String>,
    val alerts: List<String>,
    val motivation: String
) {
    fun toEntity(id: String, createdAt: Long, status: String, inputJson: String, outputJson: String?): AiReportEntity {
        return AiReportEntity(
            id = id,
            createdAt = createdAt,
            status = status,
            inputJson = inputJson,
            outputJson = outputJson
        )
    }

    fun toJsonString(): String = buildJsonObject {
        put("summary", summary)
        put("recommendations", buildJsonArray {
            recommendations.forEach { recommendation ->
                add(buildJsonObject {
                    put("title", recommendation.title)
                    put("explanation", recommendation.explanation)
                    put("checklistAction", recommendation.checklistAction)
                })
            }
        })
        put("focusAreas", buildJsonArray { focusAreas.forEach { add(JsonPrimitive(it)) } })
        put("alerts", buildJsonArray { alerts.forEach { add(JsonPrimitive(it)) } })
        put("motivation", motivation)
    }.toString()
}

private fun List<AiRecommendation>.toJson(): String = buildJsonArray {
    forEach { recommendation ->
        add(buildJsonObject {
            put("title", recommendation.title)
            put("explanation", recommendation.explanation)
            put("checklistAction", recommendation.checklistAction)
        })
    }
}.toString()

private fun List<String>.toJsonArrayString(): String = buildJsonArray {
    forEach { add(JsonPrimitive(it)) }
}.toString()

object AiJsonCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parseResponse(output: String, now: Long = System.currentTimeMillis()): AiResponseJson? {
        return runCatching {
            val root = json.parseToJsonElement(extractJsonObject(output)).jsonObject
            val slider = root["slider"]?.jsonArray?.map { item ->
                val obj = item.jsonObject
                AiSliderItem(
                    type = obj["type"]?.jsonPrimitive?.content.orEmpty(),
                    title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                    text = obj["text"]?.jsonPrimitive?.content.orEmpty()
                )
            }.orEmpty()
            val messages = root["messages"]?.jsonArray?.map { item ->
                val obj = item.jsonObject
                AiResponseMessage(
                    type = obj["type"]?.jsonPrimitive?.content.orEmpty(),
                    text = obj["text"]?.jsonPrimitive?.content.orEmpty()
                )
            }.orEmpty()
            val checklist = root["checklist"]?.jsonArray?.map { item ->
                val obj = item.jsonObject
                AiGoalChecklistItem(
                    id = obj["id"]?.jsonPrimitive?.content.orEmpty().ifBlank { "goal-$now" },
                    title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                    done = obj["done"]?.jsonPrimitive?.content == "true",
                    createdAt = now,
                    completedAt = null
                )
            }.orEmpty()
            val notification = root["notification"]?.jsonObject?.let {
                AiNotification(
                    title = it["title"]?.jsonPrimitive?.content.orEmpty(),
                    body = it["body"]?.jsonPrimitive?.content.orEmpty()
                )
            }
            if (slider.isEmpty() || messages.isEmpty()) return null
            AiResponseJson(slider, messages, checklist, notification)
        }.getOrNull()
    }

    fun parseDailyDecision(output: String): AiDailyDecision? {
        return runCatching {
            val root = json.parseToJsonElement(extractJsonObject(output)).jsonObject
            AiDailyDecision(
                summary = root["summary"]?.jsonPrimitive?.content?.trim().orEmpty(),
                findingIndex = root["findingIndex"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                recommendation = root["recommendation"]?.jsonPrimitive?.content?.trim().orEmpty(),
                checklist = root["checklist"]?.jsonArray?.map { it.jsonPrimitive.content.trim() }.orEmpty(),
                alert = root["alert"]?.jsonPrimitive?.content == "true"
            ).takeIf { it.summary.isNotBlank() && it.recommendation.isNotBlank() }
        }.getOrNull()
    }

    fun parseQuestionResponse(output: String, now: Long = System.currentTimeMillis()): AiResponseJson? {
        return runCatching {
            val root = json.parseToJsonElement(extractJsonObject(output)).jsonObject
            val answer = root["answer"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (answer.isBlank()) return@runCatching null
            val needsClinician = root["needsClinician"]?.jsonPrimitive?.content == "true"
            AiResponseJson(
                slider = emptyList(),
                messages = listOf(AiResponseMessage(if (needsClinician) "finding" else "text", answer)),
                checklist = emptyList(),
                notification = null
            )
        }.getOrNull()
    }

    private fun extractJsonObject(output: String): String {
        val text = output.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (text.startsWith("{") && text.endsWith("}")) return text
        val start = text.indexOf('{')
        if (start < 0) return text
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\' && inString) {
                escaped = true
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (inString) continue
            when (char) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }
        return text
    }

    fun checklistToJson(items: List<AiGoalChecklistItem>): String = buildJsonArray {
        items.forEach { item ->
            add(buildJsonObject {
                put("id", item.id)
                put("title", item.title)
                put("done", item.done)
                put("createdAt", item.createdAt)
                item.completedAt?.let { put("completedAt", it) }
            })
        }
    }.toString()

    fun checklistFromJson(value: String): List<AiGoalChecklistItem> = runCatching {
        json.parseToJsonElement(value).jsonArray.map { item ->
            val obj = item.jsonObject
            AiGoalChecklistItem(
                id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
                title = obj["title"]?.jsonPrimitive?.content.orEmpty(),
                done = obj["done"]?.jsonPrimitive?.content == "true",
                createdAt = obj["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                completedAt = obj["completedAt"]?.jsonPrimitive?.content?.toLongOrNull()
            )
        }
    }.getOrDefault(emptyList())

    fun sliderToJson(items: List<AiSliderItem>): String = buildJsonArray {
        items.forEach {
            add(buildJsonObject {
                put("type", it.type)
                put("title", it.title)
                put("text", it.text)
            })
        }
    }.toString()

    fun responseToJson(response: AiResponseJson): String = buildJsonObject {
        put("slider", buildJsonArray {
            response.slider.forEach {
                add(buildJsonObject {
                    put("type", it.type)
                    put("title", it.title)
                    put("text", it.text)
                })
            }
        })
        put("messages", buildJsonArray {
            response.messages.forEach {
                add(buildJsonObject {
                    put("type", it.type)
                    put("text", it.text)
                })
            }
        })
        put("checklist", buildJsonArray {
            response.checklist.forEach {
                add(buildJsonObject {
                    put("id", it.id)
                    put("title", it.title)
                    put("done", it.done)
                })
            }
        })
        response.notification?.let {
            put("notification", buildJsonObject {
                put("title", it.title)
                put("body", it.body)
            })
        }
    }.toString()
}
