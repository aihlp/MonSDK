package com.medmonitoring.core.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import com.medmonitoring.core.ingestion.HealthConnectDataSource
import com.medmonitoring.core.ingestion.PeriodicSyncWorker
import com.medmonitoring.core.settings.CollectionSettings

@Composable
fun HealthConnectSettings(dataSource: HealthConnectDataSource, settings: CollectionSettings) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(false) }
    var backgroundGranted by remember { mutableStateOf(false) }
    var enableAfterPermission by remember { mutableStateOf(false) }
    val collectionState by settings.state.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { permissions ->
        granted = permissions.containsAll(dataSource.foregroundPermissions)
        if (granted && enableAfterPermission) {
            settings.setAutomaticCollection(true)
            PeriodicSyncWorker.schedule(context)
        } else if (!granted) {
            settings.setAutomaticCollection(false)
        }
        enableAfterPermission = false
    }

    LaunchedEffect(dataSource.foregroundPermissions) {
        granted = dataSource.hasPermissions()
        backgroundGranted = dataSource.hasBackgroundPermission()
        if (!granted && collectionState.automaticCollectionEnabled) {
            settings.setAutomaticCollection(false)
            PeriodicSyncWorker.cancel(context)
        }
    }

    ListItem(
        headlineContent = { Text("Health Connect") },
        supportingContent = {
            Column {
                Text(
                    when {
                        !dataSource.isAvailable() -> "Health Connect is not available on this device."
                        granted -> "Configured health data syncs every hour."
                        else -> "Permission is required for the configured health data types."
                    }
                )
                if (dataSource.backgroundReadEnabled && granted && !backgroundGranted) {
                    Text("Background access is not granted; Android may limit scheduled synchronization.")
                }
            }
        },
        trailingContent = {
            if (dataSource.isAvailable()) {
                Switch(
                    checked = collectionState.automaticCollectionEnabled,
                    enabled = dataSource.foregroundPermissions.isNotEmpty(),
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (granted) {
                                settings.setAutomaticCollection(true)
                                PeriodicSyncWorker.schedule(context)
                            } else {
                                enableAfterPermission = true
                                permissionLauncher.launch(dataSource.foregroundPermissions)
                            }
                        } else {
                            settings.setAutomaticCollection(false)
                            PeriodicSyncWorker.cancel(context)
                        }
                    }
                )
            }
        }
    )
}
