package com.medmonitoring.core.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.medmonitoring.core.premium.AppFeature
import com.medmonitoring.core.premium.FeatureToggle

data class CollectionSettingsState(
    val automaticCollectionEnabled: Boolean = false,
    val enabledSensorIds: Set<String> = emptySet(),
    val enabledHealthConnectTypes: Set<String> = emptySet(),
    val hasEverHadRecords: Boolean = false
)

@Singleton
class CollectionSettings @Inject constructor(
    @ApplicationContext context: Context,
    private val featureToggle: FeatureToggle
) {
    private val preferences = context.getSharedPreferences("collection_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<CollectionSettingsState> = _state.asStateFlow()

    fun initializeSensors(defaultSensorIds: Set<String>) {
        if (!preferences.contains(KEY_SENSORS_INITIALIZED)) {
            preferences.edit()
                .putBoolean(KEY_SENSORS_INITIALIZED, true)
                .putStringSet(KEY_ENABLED_SENSORS, defaultSensorIds)
                .apply()
            _state.value = read()
        }
    }

    fun setAutomaticCollection(enabled: Boolean): Boolean {
        if (enabled && !featureToggle.hasAccess(AppFeature.SENSOR_AUTO_RECORD)) return false
        update {
            preferences.edit().putBoolean(KEY_AUTO_COLLECTION, enabled).apply()
        }
        return true
    }

    fun setSensorEnabled(sensorId: String, enabled: Boolean): Boolean {
        if (enabled && !featureToggle.hasAccess(AppFeature.SENSOR_AUTO_RECORD)) return false
        update {
        val ids = _state.value.enabledSensorIds.toMutableSet()
        if (enabled) ids += sensorId else ids -= sensorId
        preferences.edit().putStringSet(KEY_ENABLED_SENSORS, ids).apply()
        }
        return true
    }

    fun setHealthConnectTypeEnabled(recordType: String, enabled: Boolean): Boolean {
        if (enabled && !featureToggle.hasAccess(AppFeature.SENSOR_AUTO_RECORD)) return false
        update {
        val types = _state.value.enabledHealthConnectTypes.toMutableSet()
        if (enabled) types += recordType else types -= recordType
        preferences.edit().putStringSet(KEY_ENABLED_HEALTH_CONNECT_TYPES, types).apply()
        }
        return true
    }

    fun markRecordsCreated() {
        if (_state.value.hasEverHadRecords) return
        update {
            preferences.edit().putBoolean(KEY_HAS_EVER_HAD_RECORDS, true).apply()
        }
    }

    private fun update(write: () -> Unit) {
        write()
        _state.value = read()
    }

    private fun read() = CollectionSettingsState(
        automaticCollectionEnabled = preferences.getBoolean(KEY_AUTO_COLLECTION, false),
        enabledSensorIds = preferences.getStringSet(KEY_ENABLED_SENSORS, emptySet()).orEmpty(),
        enabledHealthConnectTypes = preferences
            .getStringSet(KEY_ENABLED_HEALTH_CONNECT_TYPES, emptySet())
            .orEmpty(),
        hasEverHadRecords = preferences.getBoolean(KEY_HAS_EVER_HAD_RECORDS, false)
    )

    private companion object {
        const val KEY_AUTO_COLLECTION = "automatic_collection"
        const val KEY_ENABLED_SENSORS = "enabled_sensors"
        const val KEY_SENSORS_INITIALIZED = "sensors_initialized"
        const val KEY_ENABLED_HEALTH_CONNECT_TYPES = "enabled_health_connect_types"
        const val KEY_HAS_EVER_HAD_RECORDS = "has_ever_had_records"
    }
}
