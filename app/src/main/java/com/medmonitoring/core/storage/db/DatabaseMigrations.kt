package com.medmonitoring.core.storage.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `lisinopril_records` RENAME TO `health_records`")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `anamnesis` (
                    `id` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `goals` (
                    `id` TEXT NOT NULL,
                    `programId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `targetMetricKey` TEXT,
                    `targetValue` REAL,
                    `unit` TEXT,
                    `progressValue` REAL,
                    `enabled` INTEGER NOT NULL,
                    `status` TEXT NOT NULL DEFAULT 'accepted',
                    `source` TEXT NOT NULL DEFAULT 'chat',
                    `sourceRef` TEXT,
                    `completedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_programId` ON `goals` (`programId`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_reports` (
                    `id` TEXT NOT NULL,
                    `programId` TEXT NOT NULL,
                    `reportDate` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `recommendationsJson` TEXT NOT NULL,
                    `focusAreasJson` TEXT NOT NULL,
                    `alertsJson` TEXT NOT NULL,
                    `rawJson` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_reports_programId_reportDate` ON `ai_reports` (`programId`, `reportDate`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_models` (
                    `id` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `repo` TEXT NOT NULL,
                    `quantization` TEXT NOT NULL,
                    `sizeMb` INTEGER NOT NULL,
                    `minRamGb` INTEGER NOT NULL,
                    `recommendedRamGb` INTEGER NOT NULL,
                    `downloadUrl` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `localPath` TEXT,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `ai_reports`")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_settings` (
                    `id` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `mode` TEXT NOT NULL,
                    `personalizationStatus` TEXT NOT NULL,
                    `notifyAnalysisReady` INTEGER NOT NULL,
                    `dailyMotivationEnabled` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_profile_facts` (
                    `id` TEXT NOT NULL,
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_profile_facts_key` ON `ai_profile_facts` (`key`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_chat_messages` (
                    `id` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `role` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `payloadJson` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_chat_messages_createdAt` ON `ai_chat_messages` (`createdAt`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_program_state` (
                    `id` TEXT NOT NULL,
                    `date` TEXT NOT NULL,
                    `sliderJson` TEXT NOT NULL,
                    `checklistJson` TEXT NOT NULL,
                    `progressText` TEXT NOT NULL,
                    `motivationText` TEXT NOT NULL,
                    `focusText` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_reports` (
                    `id` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `inputJson` TEXT NOT NULL,
                    `outputJson` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_reports_createdAt` ON `ai_reports` (`createdAt`)")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addColumnIfMissing("goals", "status", "TEXT NOT NULL DEFAULT 'accepted'")
            db.addColumnIfMissing("goals", "source", "TEXT NOT NULL DEFAULT 'chat'")
            db.addColumnIfMissing("goals", "sourceRef", "TEXT")
            db.addColumnIfMissing("goals", "completedAt", "INTEGER")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        migrateToVersion5(2),
        migrateToVersion5(3),
        migrateToVersion5(4),
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9
    )

    private fun migrateToVersion5(startVersion: Int) = object : Migration(startVersion, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.addColumnIfMissing(
                table = "lisinopril_records",
                column = "sourceType",
                definition = "TEXT NOT NULL DEFAULT 'MANUAL'"
            )
            db.addColumnIfMissing(
                table = "lisinopril_records",
                column = "flag",
                definition = "TEXT NOT NULL DEFAULT 'Normal'"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `last_input` (
                    `id` TEXT NOT NULL,
                    `medicationFullText` TEXT NOT NULL,
                    `systolic` INTEGER,
                    `diastolic` INTEGER,
                    `pulse` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `record_source_links` (
                    `sourceRecordId` TEXT NOT NULL,
                    `localRecordId` TEXT NOT NULL,
                    PRIMARY KEY(`sourceRecordId`, `localRecordId`)
                )
                """.trimIndent()
            )
        }
    }

    private fun SupportSQLiteDatabase.addColumnIfMissing(
        table: String,
        column: String,
        definition: String
    ) {
        val exists = query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            generateSequence { if (cursor.moveToNext()) cursor else null }
                .any { it.getString(nameIndex) == column }
        }
        if (!exists) {
            execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
        }
    }
}
