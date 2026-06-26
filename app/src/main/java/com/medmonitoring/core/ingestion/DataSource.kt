package com.medmonitoring.core.ingestion

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.medmonitoring.core.domain.model.HealthConnectMapping
import com.medmonitoring.core.domain.model.HealthConnectRecordType
import com.medmonitoring.core.domain.model.HealthConnectMappingRole
import com.medmonitoring.core.domain.model.PlatformIntegrationConfig
import com.medmonitoring.core.domain.model.RawSourceEvent
import com.medmonitoring.core.domain.model.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.reflect.KClass
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class DataSourceChanges(
    val upsertions: List<RawSourceEvent>,
    val deletedSourceIds: List<String>,
    val nextToken: String,
    val tokenExpired: Boolean = false
)

interface DataSource {
    val sourceType: SourceType
    val requiredPermissions: Set<String>
    suspend fun createChangesToken(): String
    suspend fun readInitialSnapshot(since: Instant): List<RawSourceEvent>
    suspend fun readChanges(token: String): DataSourceChanges
}

@Singleton
class HealthConnectDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val integrationConfig: PlatformIntegrationConfig,
    private val mapper: HealthConnectMapper,
    private val settings: com.medmonitoring.core.settings.CollectionSettings
) : DataSource {
    override val sourceType = SourceType.HEALTH_CONNECT
    private val activeMappings: List<HealthConnectMapping>
        get() = integrationConfig.healthConnectMappings.filter {
            it.role == HealthConnectMappingRole.PRIMARY_METRIC ||
                it.recordType.name in settings.state.value.enabledHealthConnectTypes
        }

    private val recordTypes: Set<KClass<out Record>>
        get() = activeMappings.map { it.recordType.recordClass }.toSet()

    val foregroundPermissions: Set<String>
        get() = recordTypes.mapTo(mutableSetOf()) { HealthPermission.getReadPermission(it) }

    override val requiredPermissions: Set<String>
        get() = foregroundPermissions

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(): Boolean {
        if (!isAvailable()) return false
        val granted = HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        return granted.containsAll(requiredPermissions)
    }

    suspend fun hasBackgroundPermission(): Boolean {
        if (!integrationConfig.backgroundReadEnabled || !isAvailable()) return true
        return HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
            .contains(BACKGROUND_READ_PERMISSION)
    }

    val backgroundReadEnabled: Boolean
        get() = integrationConfig.backgroundReadEnabled

    override suspend fun createChangesToken(): String =
        HealthConnectClient.getOrCreate(context).getChangesToken(ChangesTokenRequest(recordTypes))

    override suspend fun readInitialSnapshot(since: Instant): List<RawSourceEvent> {
        val client = HealthConnectClient.getOrCreate(context)
        val filter = TimeRangeFilter.after(since)
        return recordTypes.flatMap { recordType ->
            client.readRecords(ReadRecordsRequest(recordType, filter)).records
                .mapNotNull { mapper.map(it, activeMappings) }
        }
    }

    override suspend fun readChanges(token: String): DataSourceChanges {
        val client = HealthConnectClient.getOrCreate(context)
        var nextToken = token
        val upsertions = mutableListOf<RawSourceEvent>()
        val deletions = mutableListOf<String>()
        do {
            val response = client.getChanges(nextToken)
            if (response.changesTokenExpired) {
                return DataSourceChanges(emptyList(), emptyList(), createChangesToken(), tokenExpired = true)
            }
            response.changes.forEach { change ->
                when (change) {
                    is UpsertionChange -> mapper.map(change.record, activeMappings)
                        ?.let(upsertions::add)
                    is DeletionChange -> deletions += change.recordId
                }
            }
            nextToken = response.nextChangesToken
        } while (response.hasMore)
        return DataSourceChanges(upsertions, deletions, nextToken)
    }

    companion object {
        const val BACKGROUND_READ_PERMISSION = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    }
}

internal val HealthConnectRecordType.recordClass: KClass<out Record>
    get() = when (this) {
        HealthConnectRecordType.BLOOD_PRESSURE -> BloodPressureRecord::class
        HealthConnectRecordType.HEART_RATE -> HeartRateRecord::class
        HealthConnectRecordType.BLOOD_GLUCOSE -> BloodGlucoseRecord::class
        HealthConnectRecordType.STEPS -> StepsRecord::class
        HealthConnectRecordType.WEIGHT -> WeightRecord::class
        HealthConnectRecordType.EXERCISE -> ExerciseSessionRecord::class
        HealthConnectRecordType.SLEEP -> SleepSessionRecord::class
    }
