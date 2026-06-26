package com.medmonitoring.core.config

import android.hardware.Sensor
import com.medmonitoring.core.domain.model.ComparisonOperator
import com.medmonitoring.core.domain.model.ContextDomain
import com.medmonitoring.core.domain.model.ContextPriority
import com.medmonitoring.core.domain.model.HardwareSensorConfig
import com.medmonitoring.core.domain.model.SensorRule
import com.medmonitoring.core.domain.model.TransformType

object HealthContextCatalog {
    val hardwareSensors: List<HardwareSensorConfig> = listOf(
        sensor(Sensor.TYPE_PRESSURE, "android.sensor.pressure", "Atmospheric pressure", "hPa",
            ContextDomain.ENVIRONMENT, lessThan(990.0, "context.environment.pressure_low"),
            greaterThan(1020.0, "context.environment.pressure_high")),
        sensor(Sensor.TYPE_LIGHT, "android.sensor.light", "Ambient light", "lx",
            ContextDomain.ENVIRONMENT, lessThan(10.0, "context.environment.dark"),
            greaterThan(1000.0, "context.environment.bright_light")),
        sensor(Sensor.TYPE_AMBIENT_TEMPERATURE, "android.sensor.ambient_temperature", "Ambient temperature", "°C",
            ContextDomain.ENVIRONMENT, lessThan(18.0, "context.environment.cold"),
            greaterThan(28.0, "context.environment.hot")),
        sensor(Sensor.TYPE_RELATIVE_HUMIDITY, "android.sensor.relative_humidity", "Relative humidity", "%",
            ContextDomain.ENVIRONMENT, lessThan(30.0, "context.environment.air_dry"),
            greaterThan(70.0, "context.environment.air_humid")),
        sensor(Sensor.TYPE_ACCELEROMETER, "android.sensor.accelerometer", "Movement", "m/s²",
            ContextDomain.ACTIVITY, lessThan(0.6, "context.activity.motion_low"),
            greaterThan(2.5, "context.activity.motion_high")),
        sensor(Sensor.TYPE_LINEAR_ACCELERATION, "android.sensor.linear_acceleration", "Linear movement", "m/s²",
            ContextDomain.ACTIVITY, lessThan(0.4, "context.activity.stationary_possible"),
            greaterThan(2.0, "context.activity.high_possible")),
        sensor(Sensor.TYPE_STEP_COUNTER, "android.sensor.step_counter", "Step counter", "count",
            ContextDomain.ACTIVITY, greaterThan(2500.0, "context.activity.steps_high")),
        sensor(Sensor.TYPE_STEP_DETECTOR, "android.sensor.step_detector", "Step detector", "event",
            ContextDomain.ACTIVITY, greaterThan(0.5, "context.activity.step_detected")),
        sensor(Sensor.TYPE_PROXIMITY, "android.sensor.proximity", "Phone proximity", "cm",
            ContextDomain.BEHAVIOR, lessThan(1.0, "context.behavior.phone_interaction")),
        sensor(Sensor.TYPE_STATIONARY_DETECT, "android.sensor.stationary_detect", "Stationary detection", "event",
            ContextDomain.ACTIVITY, greaterThan(0.5, "context.activity.stationary")),
        sensor(Sensor.TYPE_MOTION_DETECT, "android.sensor.motion_detect", "Motion detection", "event",
            ContextDomain.ACTIVITY, greaterThan(0.5, "context.activity.motion_detected")),
        sensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, "android.sensor.off_body", "Device off-body", "state",
            ContextDomain.DATA_QUALITY, greaterThan(0.5, "context.quality.device_off_body"))
    )

    private fun sensor(
        type: Int,
        id: String,
        name: String,
        unit: String,
        domain: ContextDomain,
        vararg rules: SensorRule
    ) = HardwareSensorConfig(
        androidSensorType = type,
        sensorId = id,
        displayName = name,
        unit = unit,
        contextDomain = domain,
        priority = ContextPriority.IMPORTANT,
        rules = rules.map { it.copy(sensorId = id) }
    )

    private fun greaterThan(threshold: Double, tag: String) =
        rule(ComparisonOperator.GREATER_THAN, threshold, tag)

    private fun lessThan(threshold: Double, tag: String) =
        rule(ComparisonOperator.LESS_THAN, threshold, tag)

    private fun rule(operator: ComparisonOperator, threshold: Double, tag: String) = SensorRule(
        sensorId = "",
        transformType = TransformType.TAG,
        operator = operator,
        threshold = threshold,
        targetTag = tag
    )
}
