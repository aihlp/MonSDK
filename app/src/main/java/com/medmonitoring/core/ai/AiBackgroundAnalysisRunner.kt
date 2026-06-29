package com.medmonitoring.core.ai

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.medmonitoring.app.R
import com.medmonitoring.app.di.AndroidStringProvider
import com.medmonitoring.core.analytics.AnalyticsEngine
import com.medmonitoring.core.analytics.BaseAnalysisUseCase
import com.medmonitoring.core.program.ActiveProgramModuleProvider
import com.medmonitoring.core.program.ProgramModuleDefinition
import com.medmonitoring.core.storage.db.DatabaseMigrations
import com.medmonitoring.core.storage.db.MedDatabase
import com.medmonitoring.core.storage.repository.EventRepositoryImpl
import com.medmonitoring.core.units.UnitPreferenceStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class AiBackgroundAnalysisRunner(
    private val context: Context,
    private val expectedProgramId: String? = null,
    private val programModule: ProgramModuleDefinition = ActiveProgramModuleProvider.current()
) {
    private val stringProvider = AndroidStringProvider(context.applicationContext)

    suspend fun run(): AiBackgroundAnalysisResult {
        if (expectedProgramId != null && expectedProgramId != programModule.program.programId) {
            Log.e(TAG, "Program mismatch: expected=$expectedProgramId active=${programModule.program.programId}")
            return AiBackgroundAnalysisResult.Unavailable(
                stringProvider.getString(R.string.ai_status_analysis_stopped)
            )
        }
        val db = Room.databaseBuilder(context, MedDatabase::class.java, DATABASE_NAME)
            .addMigrations(*DatabaseMigrations.ALL)
            .enableMultiInstanceInvalidation()
            .build()
        return try {
            val chatRepository = AiChatRepository(db, stringProvider, programModule.program)
            chatRepository.ensureStorageInitialized()
            AiProfileRepository(context, db).ensureModelRegistrySeeded()

            val settings = db.aiSettingsDao().get() ?: com.medmonitoring.core.storage.entity.AiSettingsEntity()
            val baseAnalysis = createBaseAnalysisUseCase(db).run()
            if (settings.mode != AiSettingsContract.MODE_LOCAL_MODEL) {
                chatRepository.showAnalysisStatus(stringProvider.getString(R.string.ai_status_basic_running))
                chatRepository.showBaseAnalysis(baseAnalysis)
                Log.i(TAG, "Background basic analysis finished")
                return AiBackgroundAnalysisResult.BasicReady
            }

            chatRepository.showAnalysisStatus(stringProvider.getString(R.string.ai_status_analyzing))
            val aiResult = createAiAnalysisUseCase(db).run(baseAnalysis)
            chatRepository.showAiAnalysis(aiResult)

            when (aiResult) {
                is AiAnalysisResult.Ready -> {
                    Log.i(TAG, "Background AI analysis finished with model response")
                    AiBackgroundAnalysisResult.Ready(
                        response = aiResult.response,
                        notifyWhenReady = settings.notifyAnalysisReady
                    )
                }
                is AiAnalysisResult.Unavailable -> {
                    Log.i(TAG, "Background AI analysis unavailable: ${aiResult.reason}")
                    AiBackgroundAnalysisResult.Unavailable(aiResult.reason)
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Background AI analysis failed", error)
            withContext(NonCancellable) {
                runCatching {
                    val chatRepository = AiChatRepository(db, stringProvider, programModule.program)
                    chatRepository.showAiAnalysis(
                        AiAnalysisResult.Unavailable(stringProvider.getString(R.string.ai_status_analysis_stopped))
                    )
                }
            }
            AiBackgroundAnalysisResult.Unavailable(stringProvider.getString(R.string.ai_status_analysis_stopped))
        } finally {
            db.close()
        }
    }

    suspend fun runQuestion(question: String): AiBackgroundAnalysisResult {
        if (expectedProgramId != null && expectedProgramId != programModule.program.programId) {
            Log.e(TAG, "Program mismatch: expected=$expectedProgramId active=${programModule.program.programId}")
            return AiBackgroundAnalysisResult.Unavailable(
                stringProvider.getString(R.string.ai_status_analysis_stopped)
            )
        }
        val db = Room.databaseBuilder(context, MedDatabase::class.java, DATABASE_NAME)
            .addMigrations(*DatabaseMigrations.ALL)
            .enableMultiInstanceInvalidation()
            .build()
        return try {
            val chatRepository = AiChatRepository(db, stringProvider, programModule.program)
            chatRepository.ensureStorageInitialized()
            AiProfileRepository(context, db).ensureModelRegistrySeeded()

            chatRepository.showAnalysisStatus(stringProvider.getString(R.string.ai_status_analyzing))
            val baseAnalysis = createBaseAnalysisUseCase(db).run()
            val aiResult = createAiAnalysisUseCase(db).answerQuestion(question, baseAnalysis)
            chatRepository.showAiAnalysis(aiResult)

            when (aiResult) {
                is AiAnalysisResult.Ready -> {
                    Log.i(TAG, "Background AI question answered")
                    val settings = db.aiSettingsDao().get() ?: com.medmonitoring.core.storage.entity.AiSettingsEntity()
                    AiBackgroundAnalysisResult.Ready(
                        response = aiResult.response,
                        notifyWhenReady = settings.notifyAnalysisReady
                    )
                }
                is AiAnalysisResult.Unavailable -> {
                    Log.i(TAG, "Background AI question unavailable: ${aiResult.reason}")
                    AiBackgroundAnalysisResult.Unavailable(aiResult.reason)
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Background AI question failed", error)
            withContext(NonCancellable) {
                runCatching {
                    val chatRepository = AiChatRepository(db, stringProvider, programModule.program)
                    chatRepository.showAiAnalysis(
                        AiAnalysisResult.Unavailable(stringProvider.getString(R.string.ai_status_analysis_stopped))
                    )
                }
            }
            AiBackgroundAnalysisResult.Unavailable(stringProvider.getString(R.string.ai_status_analysis_stopped))
        } finally {
            db.close()
        }
    }

    private fun createBaseAnalysisUseCase(db: MedDatabase): BaseAnalysisUseCase {
        val repository = EventRepositoryImpl(db)
        return BaseAnalysisUseCase(
            repository = repository,
            analyticsEngine = AnalyticsEngine(),
            recordMapper = programModule.recordMapper,
            program = programModule.program,
            analyticsSchema = programModule.analytics
        )
    }

    private fun createAiAnalysisUseCase(db: MedDatabase): AiAnalysisUseCase {
        val repository = EventRepositoryImpl(db)
        return AiAnalysisUseCase(
            db = db,
            repository = repository,
            analyticsEngine = AnalyticsEngine(),
            recordMapper = programModule.recordMapper,
            program = programModule.program,
            analyticsSchema = programModule.analytics,
            promptBuilder = AiPromptBuilder(),
            aiEngine = LlamaCppAiEngine(context),
            stringProvider = stringProvider,
            ui = programModule.ui,
            unitPreferences = UnitPreferenceStore(context.applicationContext, programModule.program, programModule.ui)
        )
    }

    companion object {
        private const val TAG = "AiAnalysis"
        private const val DATABASE_NAME = "med_database"
    }
}

sealed interface AiBackgroundAnalysisResult {
    data object BasicReady : AiBackgroundAnalysisResult

    data class Ready(
        val response: AiResponseJson,
        val notifyWhenReady: Boolean
    ) : AiBackgroundAnalysisResult

    data class Unavailable(val reason: String) : AiBackgroundAnalysisResult
}
