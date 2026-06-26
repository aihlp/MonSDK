package com.medmonitoring.core.ai

import android.content.Context
import android.util.Log
import com.medmonitoring.core.storage.db.MedDatabase
import com.medmonitoring.core.storage.entity.AiModelEntity
import com.medmonitoring.core.storage.entity.AnamnesisEntity
import com.medmonitoring.core.storage.entity.GoalEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiProfileRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: MedDatabase
) {
    fun observeAnamnesis(): Flow<String> = db.anamnesisDao().observe().map { it?.text.orEmpty() }
    suspend fun getAnamnesis(): String = db.anamnesisDao().get()?.text.orEmpty()

    suspend fun saveAnamnesis(text: String) {
        db.anamnesisDao().upsert(AnamnesisEntity(text = text.trim(), updatedAt = Instant.now().toEpochMilli()))
    }

    fun observeGoals(programId: String): Flow<List<GoalEntity>> = db.goalDao().observeForProgram(programId)
    suspend fun getGoals(programId: String): List<GoalEntity> = db.goalDao().getForProgram(programId)

    suspend fun upsertGoal(programId: String, draft: AiGoalDraft) {
        val now = Instant.now().toEpochMilli()
        db.goalDao().upsert(
            GoalEntity(
                id = draft.id.ifBlank { UUID.randomUUID().toString() },
                programId = programId,
                title = draft.title.trim(),
                description = draft.description.trim(),
                targetMetricKey = draft.targetMetricKey?.trim()?.ifBlank { null },
                targetValue = draft.targetValue,
                unit = draft.unit?.trim()?.ifBlank { null },
                progressValue = draft.progressValue,
                enabled = draft.enabled,
                status = draft.status,
                source = draft.source,
                sourceRef = draft.sourceRef,
                completedAt = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun addGoal(
        programId: String,
        title: String,
        description: String = "",
        status: String = AiGoalStatus.ACCEPTED,
        source: String = AiGoalSource.CHAT,
        sourceRef: String? = null
    ): GoalEntity {
        val now = Instant.now().toEpochMilli()
        val goal = GoalEntity(
            id = UUID.randomUUID().toString(),
            programId = programId,
            title = title.trim(),
            description = description.trim(),
            targetMetricKey = null,
            targetValue = null,
            unit = null,
            progressValue = null,
            enabled = true,
            status = status,
            source = source,
            sourceRef = sourceRef?.trim()?.ifBlank { null },
            completedAt = null,
            createdAt = now,
            updatedAt = now
        )
        db.goalDao().upsert(goal)
        return goal
    }

    suspend fun addRecommendationGoal(
        programId: String,
        title: String,
        description: String,
        source: String
    ): GoalEntity {
        return addGoal(
            programId = programId,
            title = title,
            description = description,
            status = AiGoalStatus.RECOMMENDED,
            source = source
        )
    }

    suspend fun setGoalStatus(id: String, status: String) {
        val now = Instant.now().toEpochMilli()
        db.goalDao().updateStatus(
            id = id,
            status = status,
            completedAt = if (status == AiGoalStatus.ACHIEVED) now else null,
            updatedAt = now
        )
    }

    suspend fun deleteGoal(id: String) {
        db.goalDao().markDeleted(id, AiGoalStatus.DELETED, Instant.now().toEpochMilli())
    }

    fun observeReports(programId: String) = db.aiReportDao().observeReports()
    suspend fun latestReport(programId: String) = db.aiReportDao().latest()

    fun observeModels(): Flow<List<AiModelEntity>> = db.aiModelDao().observeModels()

    suspend fun ensureModelRegistrySeeded() {
        val now = Instant.now().toEpochMilli()
        val existing = db.aiModelDao().getModels().associateBy { it.id }
        AiModelRegistry.recommendedModels.forEach { spec ->
            val current = existing[spec.id]
            db.aiModelDao().upsert(
                AiModelEntity(
                    id = spec.id,
                    displayName = spec.displayName,
                    repo = spec.repo,
                    quantization = spec.quantization,
                    sizeMb = spec.sizeMb,
                    minRamGb = spec.minRamGb,
                    recommendedRamGb = spec.recommendedRamGb,
                    downloadUrl = spec.downloadUrl,
                    status = current?.status ?: spec.status.name,
                    localPath = current?.localPath,
                    updatedAt = current?.updatedAt ?: now
                )
            )
        }
    }

    suspend fun downloadModel(id: String): Result<File> = withContext(Dispatchers.IO) {
        val spec = AiModelRegistry.specFor(id)
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown AI model: $id"))
        val store = AiModelFileStore(File(context.filesDir, "ai_models"))
        when (val state = store.prepare(spec)) {
            is AiModelFileState.Ready -> {
                db.aiModelDao().updateStatus(id, AiModelStatus.READY.name, state.file.absolutePath, Instant.now().toEpochMilli())
                Log.i("AiModelDownload", "Model already present id=$id bytes=${state.file.length()} path=${state.file.absolutePath}")
                return@withContext Result.success(state.file)
            }
            is AiModelFileState.NeedsDownload -> {
                db.aiModelDao().updateStatus(id, AiModelStatus.DOWNLOADING.name, null, Instant.now().toEpochMilli())
                Log.i("AiModelDownload", "Starting download id=$id url=${spec.downloadUrl} target=${state.target.absolutePath}")
                downloadModelFile(id, spec, state, store)
            }
        }
    }

    private suspend fun downloadModelFile(
        id: String,
        spec: AiModelSpec,
        state: AiModelFileState.NeedsDownload,
        store: AiModelFileStore
    ): Result<File> {
        return runCatching {
            val connection = (URL(spec.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            try {
                Log.i("AiModelDownload", "HTTP ${connection.responseCode} for id=$id")
                if (connection.responseCode !in 200..299) {
                    error("Hugging Face returned HTTP ${connection.responseCode}")
                }
                connection.inputStream.use { input ->
                    state.temp.outputStream().use { output -> input.copyTo(output) }
                }
                val target = store.commit(state.temp, state.target)
                db.aiModelDao().updateStatus(id, AiModelStatus.READY.name, target.absolutePath, Instant.now().toEpochMilli())
                Log.i("AiModelDownload", "Download complete id=$id bytes=${target.length()} path=${target.absolutePath}")
                target
            } finally {
                connection.disconnect()
                if (state.temp.exists()) state.temp.delete()
            }
        }.onFailure {
            Log.e("AiModelDownload", "Download failed id=$id", it)
            db.aiModelDao().updateStatus(id, AiModelStatus.ERROR.name, null, Instant.now().toEpochMilli())
        }
    }
}
