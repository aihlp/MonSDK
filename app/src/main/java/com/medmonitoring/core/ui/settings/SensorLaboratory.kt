package com.medmonitoring.core.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medmonitoring.core.domain.model.SensorRule
import com.medmonitoring.core.domain.model.UniversalProgramDefinition
import com.medmonitoring.core.ingestion.SensorRegistry
import com.medmonitoring.core.ingestion.HealthConnectDataSource
import androidx.health.connect.client.PermissionController
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.medmonitoring.core.ingestion.PeriodicSyncWorker
import com.medmonitoring.core.domain.model.HealthConnectMappingRole
import com.medmonitoring.core.settings.CollectionSettings

@Composable
fun SensorLaboratory(
    program: UniversalProgramDefinition,
    registry: SensorRegistry,
    settings: CollectionSettings,
    healthConnectDataSource: HealthConnectDataSource
) {
    val sensors by registry.sensors.collectAsState()
    val collectionSettings by settings.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingHealthConnectType by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { permissions ->
        pendingHealthConnectType?.let { type ->
            if (!permissions.containsAll(healthConnectDataSource.requiredPermissions)) {
                settings.setHealthConnectTypeEnabled(type, false)
            }
        }
        pendingHealthConnectType = null
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Sensor Laboratory",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        if (sensors.isEmpty()) {
            ListItem(
                headlineContent = { Text("Hardware sensors") },
                supportingContent = { Text("No supported context sensors are available on this device.") }
            )
        }
        sensors.forEach { sensor ->
            val rules = program.integrations.hardwareSensors
                .firstOrNull { it.sensorId == sensor.id }?.rules.orEmpty()
            val config = program.integrations.hardwareSensors.firstOrNull { it.sensorId == sensor.id }
            ListItem(
                headlineContent = { Text(sensor.name) },
                supportingContent = {
                    Column {
                        Text(when {
                            !sensor.available -> "Not available on this device"
                            sensor.currentValue == null -> "Waiting for value"
                            else -> "%.1f %s".format(sensor.currentValue, sensor.unit).trim()
                        })
                        Text(
                            if (sensor.configured) {
                                "${config?.contextDomain?.name?.lowercase()?.replace('_', ' ')} context · values become tags"
                            } else "Unsupported",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                trailingContent = {
                    Switch(
                        checked = sensor.id in collectionSettings.enabledSensorIds,
                        enabled = sensor.available,
                        onCheckedChange = { settings.setSensorEnabled(sensor.id, it) }
                    )
                }
            )
            if (rules.isNotEmpty()) {
                Text(
                    rules.joinToString("\n", transform = SensorRule::summary),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HorizontalDivider()
        }
        program.integrations.healthConnectMappings
            .filter { mapping ->
                mapping.role == HealthConnectMappingRole.PRIMARY_METRIC ||
                    mapping.metricMappings.isNotEmpty() ||
                    mapping.rules.isNotEmpty()
            }
            .forEach { mapping ->
            val primary = mapping.role == HealthConnectMappingRole.PRIMARY_METRIC
            val enabled = primary || mapping.recordType.name in collectionSettings.enabledHealthConnectTypes
            ListItem(
                headlineContent = { Text(mapping.recordType.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) },
                supportingContent = {
                    Column {
                        Text(if (primary) "Primary tracked metric" else "Optional Health Connect context")
                        if (mapping.rules.isNotEmpty()) {
                            Text(
                                mapping.rules.joinToString("\n", transform = SensorRule::summary),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                trailingContent = {
                    Switch(
                        checked = enabled,
                        enabled = !primary && healthConnectDataSource.isAvailable(),
                        onCheckedChange = { checked ->
                            settings.setHealthConnectTypeEnabled(mapping.recordType.name, checked)
                            scope.launch {
                                PeriodicSyncWorker.resetChangesToken(context)
                            }
                            if (checked) {
                                pendingHealthConnectType = mapping.recordType.name
                                permissionLauncher.launch(healthConnectDataSource.foregroundPermissions)
                            }
                        }
                    )
                }
            )
        }
    }
}

private fun SensorRule.summary(): String = when {
    targetTag != null && threshold != null ->
        "${operator.symbol} ${threshold.toInt()} → $targetTag"
    targetMetricId != null -> "Metric: $targetMetricId"
    else -> transformType.name
}

private val com.medmonitoring.core.domain.model.ComparisonOperator.symbol: String
    get() = when (this) {
        com.medmonitoring.core.domain.model.ComparisonOperator.GREATER_THAN -> ">"
        com.medmonitoring.core.domain.model.ComparisonOperator.LESS_THAN -> "<"
        com.medmonitoring.core.domain.model.ComparisonOperator.EQUALS -> "="
    }
