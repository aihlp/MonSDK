package com.medmonitoring.core.analytics

import com.medmonitoring.core.domain.model.AnalysisLevel
import com.medmonitoring.core.domain.model.Finding
import com.medmonitoring.core.domain.model.ProgramAnalyticsSchema
import com.medmonitoring.core.domain.model.StatisticMetric
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.domain.model.UserRecord
import com.medmonitoring.core.domain.repository.EventRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class BaseAnalysisResult(
    val programId: String,
    val recordCount: Int,
    val analysisLevel: AnalysisLevel,
    val dashboardMetrics: List<StatisticMetric>,
    val findings: List<Finding>,
    val generatedAt: Instant
)

@Singleton
class BaseAnalysisUseCase @Inject constructor(
    private val repository: EventRepository,
    private val analyticsEngine: AnalyticsEngine,
    private val recordMapper: ProgramRecordMapper<UserRecord>,
    private val program: UniversalProgramDefinition,
    private val analyticsSchema: ProgramAnalyticsSchema
) {
    suspend fun run(): BaseAnalysisResult {
        val records = repository.getRecords()
        val state = analyticsEngine.calculate(
            recordMapper.mapAll(records),
            analyticsSchema
        )
        return BaseAnalysisResult(
            programId = program.programId,
            recordCount = state.dataSummary.recordCount,
            analysisLevel = state.analysisLevel,
            dashboardMetrics = state.dashboardMetrics,
            findings = state.allFindings,
            generatedAt = Instant.now()
        )
    }
}
