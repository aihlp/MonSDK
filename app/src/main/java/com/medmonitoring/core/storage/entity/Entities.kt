package com.medmonitoring.core.storage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "raw_events")
data class RawEventEntity(
    @PrimaryKey val id: String,
    val sourceType: String,
    val payloadJson: String,
    val capturedAt: Long,
    val sourceTimestamp: Long?,
    val schemaVersion: Int,
    val error: String?,
    val sourceRecordId: String?,
    val processed: Boolean = false
)

@Entity(tableName = "user_records")
data class UserRecordEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val timestamp: Long,
    val measurementsJson: String,
    val eventsJson: String,
    val dimensionsJson: String,
    val qualityJson: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceType: String = "MANUAL",
    val flag: String = "Normal"
)

@Entity(tableName = "custom_tags")
data class CustomTagEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val label: String,
    val createdAt: Long
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val type: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val repeat: String,
    val enabled: Boolean
)

@Entity(tableName = "last_input")
data class LastInputEntity(
    @PrimaryKey val id: String = "singleton",
    val medicationFullText: String,
    val valuesJson: String = "{}"
)

@Entity(
    tableName = "record_source_links",
    primaryKeys = ["sourceRecordId", "localRecordId"]
)
data class RecordSourceLinkEntity(
    val sourceRecordId: String,
    val localRecordId: String
)

@Entity(tableName = "anamnesis")
data class AnamnesisEntity(
    @PrimaryKey val id: String = "singleton",
    val text: String,
    val updatedAt: Long
)

@Entity(tableName = "goals", indices = [Index(value = ["programId"])])
data class GoalEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val title: String,
    val description: String,
    val targetMetricKey: String?,
    val targetValue: Double?,
    val unit: String?,
    val progressValue: Double?,
    val enabled: Boolean,
    val status: String = "accepted",
    val source: String = "chat",
    val sourceRef: String? = null,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "ai_settings")
data class AiSettingsEntity(
    @PrimaryKey val id: String = "default",
    val enabled: Boolean = true,
    val mode: String = "basic",
    val personalizationStatus: String = "none",
    val notifyAnalysisReady: Boolean = true,
    val dailyMotivationEnabled: Boolean = true
)

@Entity(tableName = "ai_profile_facts", indices = [Index(value = ["key"], unique = true)])
data class AiProfileFactEntity(
    @PrimaryKey val id: String,
    val key: String,
    val value: String,
    val updatedAt: Long
)

@Entity(tableName = "ai_chat_messages", indices = [Index(value = ["createdAt"])])
data class AiChatMessageEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val role: String,
    val type: String,
    val text: String,
    val payloadJson: String?
)

@Entity(tableName = "ai_program_state")
data class AiProgramStateEntity(
    @PrimaryKey val id: String = "today",
    val date: String,
    val sliderJson: String,
    val checklistJson: String,
    val progressText: String,
    val motivationText: String,
    val focusText: String
)

@Entity(tableName = "ai_reports", indices = [Index(value = ["createdAt"])])
data class AiReportEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val status: String,
    val inputJson: String,
    val outputJson: String?
)

@Entity(tableName = "ai_models")
data class AiModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val repo: String,
    val quantization: String,
    val sizeMb: Int,
    val minRamGb: Int,
    val recommendedRamGb: Int,
    val downloadUrl: String,
    val status: String,
    val localPath: String?,
    val updatedAt: Long
)
