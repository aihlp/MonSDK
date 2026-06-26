package com.medmonitoring.core.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.medmonitoring.core.storage.entity.*

@Database(
    entities = [
        RawEventEntity::class,
        UserRecordEntity::class,
        CustomTagEntity::class,
        ReminderEntity::class,
        LastInputEntity::class,
        RecordSourceLinkEntity::class,
        AnamnesisEntity::class,
        GoalEntity::class,
        AiSettingsEntity::class,
        AiProfileFactEntity::class,
        AiChatMessageEntity::class,
        AiProgramStateEntity::class,
        AiReportEntity::class,
        AiModelEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class MedDatabase : RoomDatabase() {
    abstract fun rawEventDao(): RawEventDao
    abstract fun userRecordDao(): UserRecordDao
    abstract fun customTagDao(): CustomTagDao
    abstract fun reminderDao(): ReminderDao
    abstract fun lastInputDao(): LastInputDao
    abstract fun recordSourceLinkDao(): RecordSourceLinkDao
    abstract fun anamnesisDao(): AnamnesisDao
    abstract fun goalDao(): GoalDao
    abstract fun aiSettingsDao(): AiSettingsDao
    abstract fun aiProfileFactDao(): AiProfileFactDao
    abstract fun aiChatMessageDao(): AiChatMessageDao
    abstract fun aiProgramStateDao(): AiProgramStateDao
    abstract fun aiReportDao(): AiReportDao
    abstract fun aiModelDao(): AiModelDao
}
