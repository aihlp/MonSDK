package com.medmonitoring.core.storage.db

import androidx.room.*
import com.medmonitoring.core.storage.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RawEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(raw: RawEventEntity)

    @Query("SELECT * FROM raw_events WHERE processed = 0 ORDER BY capturedAt ASC")
    suspend fun getUnprocessed(): List<RawEventEntity>

    @Query("SELECT * FROM raw_events ORDER BY capturedAt ASC")
    suspend fun getAll(): List<RawEventEntity>

    @Query("SELECT * FROM raw_events WHERE sourceRecordId = :sourceRecordId")
    suspend fun bySourceRecordId(sourceRecordId: String): List<RawEventEntity>

    @Query("DELETE FROM raw_events WHERE sourceRecordId = :sourceRecordId")
    suspend fun deleteBySourceRecordId(sourceRecordId: String)

    @Query("UPDATE raw_events SET processed = :processed, error = :error WHERE id = :id")
    suspend fun updateProcessedState(id: String, processed: Boolean, error: String?)

    @Query("DELETE FROM raw_events")
    suspend fun clear()
}

@Dao
interface UserRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: UserRecordEntity)

    @Query("SELECT * FROM user_records ORDER BY timestamp DESC")
    fun observeRecords(): Flow<List<UserRecordEntity>>

    @Query("SELECT * FROM user_records ORDER BY timestamp DESC")
    suspend fun getRecords(): List<UserRecordEntity>

    @Query("SELECT * FROM user_records WHERE id = :id LIMIT 1")
    suspend fun get(id: String): UserRecordEntity?

    @Query("DELETE FROM user_records WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "DELETE FROM user_records WHERE id = '" +
            com.medmonitoring.core.ingestion.HealthConnectIds.EVENT_PREFIX +
            "' || :sourceRecordId OR id LIKE '" +
            com.medmonitoring.core.ingestion.HealthConnectIds.EVENT_PREFIX +
            "' || :sourceRecordId || ':%'"
    )
    suspend fun deleteBySourceRecordId(sourceRecordId: String)

    @Query("DELETE FROM user_records WHERE sourceType = :sourceType")
    suspend fun deleteBySourceType(sourceType: String)

    @Query("DELETE FROM user_records")
    suspend fun clear()
}

@Dao
interface CustomTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: CustomTagEntity)

    @Query("SELECT * FROM custom_tags ORDER BY createdAt ASC")
    fun observeTags(): Flow<List<CustomTagEntity>>

    @Query("SELECT * FROM custom_tags ORDER BY createdAt ASC")
    suspend fun getTags(): List<CustomTagEntity>

    @Query("DELETE FROM custom_tags")
    suspend fun clear()
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    suspend fun getReminders(): List<ReminderEntity>

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LastInputDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(input: LastInputEntity)

    @Query("SELECT * FROM last_input WHERE id = 'singleton'")
    suspend fun getLastInput(): LastInputEntity?

    @Query("SELECT * FROM last_input WHERE id = 'singleton'")
    fun observeLastInput(): Flow<LastInputEntity?>
}

@Dao
interface RecordSourceLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: RecordSourceLinkEntity)

    @Query("SELECT localRecordId FROM record_source_links WHERE sourceRecordId = :sourceRecordId")
    suspend fun localRecordIds(sourceRecordId: String): List<String>

    @Query("DELETE FROM record_source_links WHERE sourceRecordId = :sourceRecordId")
    suspend fun deleteBySourceRecordId(sourceRecordId: String)

    @Query("DELETE FROM record_source_links WHERE localRecordId = :localRecordId")
    suspend fun deleteByLocalRecordId(localRecordId: String)

    @Query("DELETE FROM record_source_links")
    suspend fun clear()
}

@Dao
interface AnamnesisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(anamnesis: AnamnesisEntity)

    @Query("SELECT * FROM anamnesis WHERE id = 'singleton'")
    fun observe(): Flow<AnamnesisEntity?>

    @Query("SELECT * FROM anamnesis WHERE id = 'singleton'")
    suspend fun get(): AnamnesisEntity?
}

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE programId = :programId ORDER BY createdAt ASC")
    fun observeForProgram(programId: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE programId = :programId ORDER BY createdAt ASC")
    suspend fun getForProgram(programId: String): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE programId = :programId AND status = :status ORDER BY createdAt DESC")
    suspend fun getForProgramByStatus(programId: String, status: String): List<GoalEntity>

    @Query("UPDATE goals SET status = :status, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, completedAt: Long?, updatedAt: Long)

    @Query("UPDATE goals SET status = :status, enabled = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markDeleted(id: String, status: String, updatedAt: Long)

    @Query("UPDATE goals SET status = :newStatus, updatedAt = :updatedAt WHERE programId = :programId AND status = :oldStatus")
    suspend fun updateStatusForProgram(programId: String, oldStatus: String, newStatus: String, updatedAt: Long)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM goals WHERE programId = :programId")
    suspend fun deleteForProgram(programId: String)
}

@Dao
interface AiReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: AiReportEntity)

    @Query("SELECT * FROM ai_reports ORDER BY createdAt DESC")
    fun observeReports(): Flow<List<AiReportEntity>>

    @Query("SELECT * FROM ai_reports ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): AiReportEntity?

    @Query("DELETE FROM ai_reports")
    suspend fun deleteAll()
}

@Dao
interface AiSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: AiSettingsEntity)

    @Query("SELECT * FROM ai_settings WHERE id = 'default'")
    fun observe(): Flow<AiSettingsEntity?>

    @Query("SELECT * FROM ai_settings WHERE id = 'default'")
    suspend fun get(): AiSettingsEntity?
}

@Dao
interface AiProfileFactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fact: AiProfileFactEntity)

    @Query("SELECT * FROM ai_profile_facts ORDER BY updatedAt ASC")
    fun observeFacts(): Flow<List<AiProfileFactEntity>>

    @Query("SELECT * FROM ai_profile_facts ORDER BY updatedAt ASC")
    suspend fun getFacts(): List<AiProfileFactEntity>

    @Query("DELETE FROM ai_profile_facts")
    suspend fun deleteAll()
}

@Dao
interface AiChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: AiChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<AiChatMessageEntity>)

    @Query("SELECT * FROM ai_chat_messages ORDER BY createdAt ASC")
    fun observeMessages(): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages ORDER BY createdAt ASC")
    suspend fun getMessages(): List<AiChatMessageEntity>

    @Query("SELECT * FROM ai_chat_messages WHERE role = 'assistant' AND payloadJson = :payloadJson ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestAssistantMessageByPayload(payloadJson: String): AiChatMessageEntity?

    @Query("DELETE FROM ai_chat_messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM ai_chat_messages")
    suspend fun deleteAll()

    @Query("DELETE FROM ai_chat_messages WHERE role = 'assistant' AND type = 'status' AND payloadJson = :payloadJson")
    suspend fun deleteAssistantStatusByPayload(payloadJson: String)

    @Query("DELETE FROM ai_chat_messages WHERE role = 'assistant' AND type = 'status' AND text IN (:texts)")
    suspend fun deleteAssistantStatusesByText(texts: List<String>)
}

@Dao
interface AiProgramStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: AiProgramStateEntity)

    @Query("SELECT * FROM ai_program_state WHERE id = 'today'")
    fun observeToday(): Flow<AiProgramStateEntity?>

    @Query("SELECT * FROM ai_program_state WHERE id = 'today'")
    suspend fun getToday(): AiProgramStateEntity?

    @Query("DELETE FROM ai_program_state")
    suspend fun deleteAll()
}

@Dao
interface AiModelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: AiModelEntity)

    @Query("SELECT * FROM ai_models ORDER BY recommendedRamGb ASC, sizeMb ASC")
    fun observeModels(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models ORDER BY recommendedRamGb ASC, sizeMb ASC")
    suspend fun getModels(): List<AiModelEntity>

    @Query("UPDATE ai_models SET status = :status, localPath = :localPath, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, localPath: String?, updatedAt: Long)
}
