package com.medmonitoring.core.ingestion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

data class RegisteredSensor(
    val id: String,
    val name: String,
    val unit: String,
    val type: Int,
    val available: Boolean,
    val configured: Boolean,
    val currentValue: Double? = null
)

@Singleton
class SensorRegistry @Inject constructor(
    @ApplicationContext context: Context
) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var configured = emptyList<com.medmonitoring.core.domain.model.HardwareSensorConfig>()
    private var sensorIds = emptyMap<Sensor, String>()
    private val _sensors = MutableStateFlow<List<RegisteredSensor>>(emptyList())
    val sensors: StateFlow<List<RegisteredSensor>> = _sensors.asStateFlow()

    fun configure(sensors: List<com.medmonitoring.core.domain.model.HardwareSensorConfig>) {
        stop()
        configured = sensors
        sensorIds = manager.getSensorList(Sensor.TYPE_ALL).associateWith(::sensorId)
        _sensors.value = discover()
    }

    fun start() {
        applyEnabledSensors(_sensors.value.map { it.id }.toSet())
    }

    fun applyEnabledSensors(enabledSensorIds: Set<String>) {
        stop()
        manager.getSensorList(Sensor.TYPE_ALL).forEach { sensor ->
            if (sensorId(sensor) in enabledSensorIds) {
                manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    fun stop() = manager.unregisterListener(this)

    fun currentValues(): Map<String, Double> =
        _sensors.value.mapNotNull { sensor -> sensor.currentValue?.let { sensor.id to it } }.toMap()

    override fun onSensorChanged(event: SensorEvent) {
        val id = sensorIds[event.sensor] ?: return
        val value = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (event.values.size < 3) return
                kotlin.math.abs(
                    sqrt(event.values.take(3).sumOf { it.toDouble() * it.toDouble() }) -
                        SensorManager.GRAVITY_EARTH
                )
            }
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_GYROSCOPE -> {
                if (event.values.size < 3) return
                sqrt(event.values.take(3).sumOf { it.toDouble() * it.toDouble() })
            }
            else -> event.values.firstOrNull()?.toDouble() ?: return
        }
        _sensors.value = _sensors.value.map {
            if (it.id == id) it.copy(currentValue = value) else it
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun discover() = manager.getSensorList(Sensor.TYPE_ALL)
        .filter { sensor -> configured.any { it.androidSensorType == sensor.type && it.rules.isNotEmpty() } }
        .distinctBy { sensorId(it) }
        .sortedBy { it.name }
        .map { sensor ->
            val config = configured.firstOrNull { it.androidSensorType == sensor.type }
            RegisteredSensor(
                id = sensorId(sensor),
                name = config?.displayName ?: sensor.name,
                unit = config?.unit ?: unitFor(sensor.type),
                type = sensor.type,
                available = true,
                configured = config != null
            )
        }

    private fun sensorId(sensor: Sensor): String =
        configured.firstOrNull { it.androidSensorType == sensor.type }?.sensorId
            ?: "android.sensor.${sensor.type}:${sensor.name.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"

    private fun unitFor(type: Int) = when (type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GRAVITY -> "m/s²"
        Sensor.TYPE_GYROSCOPE -> "rad/s"
        Sensor.TYPE_LIGHT -> "lx"
        Sensor.TYPE_PRESSURE -> "hPa"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
        Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR -> "count"
        Sensor.TYPE_PROXIMITY -> "cm"
        Sensor.TYPE_MAGNETIC_FIELD -> "μT"
        else -> ""
    }
}
