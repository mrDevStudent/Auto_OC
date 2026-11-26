package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.automation.DeviceReadinessHelper
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusWaiting

@Composable
fun SetupGuideScreen(
    uiState: MeetPilotUiState,
    onRefreshReadiness: () -> Unit,
    onSetAlertSound: (Boolean) -> Unit,
    onSetAlertVibrate: (Boolean) -> Unit,
    onSetDefaultMute: (Boolean) -> Unit,
    onSetDefaultCamOff: (Boolean) -> Unit,
    onTestAlertNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val readiness = uiState.readiness
    val prefs = uiState.preferences

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshReadiness()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "setup_header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Device Setup & Alerts",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Required permissions for hands-free Google Meet joining on ${readiness.manufacturerName} ${readiness.deviceModel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "readiness_summary") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "System Readiness",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${readiness.readyCount} / 5",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { readiness.readyCount / 5f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = if (readiness.coreReady) {
                                "Core auto-join services are active. You are ready for scheduled joining."
                            } else {
                                "Enable the checks below so MeetPilot can wake your screen, tap Join, and alert you."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item(key = "check_accessibility") {
                ReadinessCheckCard(
                    icon = Icons.Default.AccessibilityNew,
                    title = "Auto-Join Accessibility Service",
                    description = "Detects Google Meet's Join / Ask to join button, mutes mic/cam if chosen, retries on timeouts, and detects admission.",
                    isReady = readiness.accessibilityEnabled,
                    actionLabel = if (readiness.accessibilityEnabled) "Manage Service" else "Enable Service",
                    buttonTestTag = "setup_accessibility_button",
                    onAction = { DeviceReadinessHelper.openAccessibilitySettings(context) }
                )
            }

            item(key = "check_notifications") {
                ReadinessCheckCard(
                    icon = Icons.Default.NotificationsActive,
                    title = "Connection Alert Notifications",
                    description = "Alerts you with sound & vibration the moment someone accepts you into Google Meet, plus a persistent Stop button.",
                    isReady = readiness.notificationsAllowed,
                    actionLabel = if (readiness.notificationsAllowed) "Notification Settings" else "Allow Notifications",
                    buttonTestTag = "setup_notifications_button",
                    onAction = {
                        if (!readiness.notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            DeviceReadinessHelper.openNotificationSettings(context)
                        }
                    }
                )
            }

            item(key = "check_exact_alarm") {
                ReadinessCheckCard(
                    icon = Icons.Default.Alarm,
                    title = "Exact Schedule Alarms",
                    description = "Wakes your phone at the exact scheduled minute (e.g. Monday 5:00 PM) without Android Doze delay.",
                    isReady = readiness.exactAlarmAllowed,
                    actionLabel = if (readiness.exactAlarmAllowed) "Allowed" else "Grant Alarm Access",
                    buttonTestTag = "setup_exact_alarm_button",
                    onAction = { DeviceReadinessHelper.openExactAlarmSettings(context) }
                )
            }

            item(key = "check_battery") {
                ReadinessCheckCard(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Unrestricted Battery Usage",
                    description = "Prevents ColorOS / Android from sleeping MeetPilot while waiting for your scheduled class or host admission.",
                    isReady = readiness.batteryOptimizationIgnored,
                    actionLabel = if (readiness.batteryOptimizationIgnored) "Unrestricted" else "Disable Optimization",
                    buttonTestTag = "setup_battery_button",
                    onAction = { DeviceReadinessHelper.openBatteryOptimizationSettings(context) }
                )
            }

            item(key = "check_meet_app") {
                ReadinessCheckCard(
                    icon = Icons.Default.VideoCall,
                    title = "Google Meet App",
                    description = "Checks whether the official Google Meet app is installed for direct deep-link launching.",
                    isReady = readiness.googleMeetInstalled,
                    actionLabel = if (readiness.googleMeetInstalled) "Installed" else "Install Meet",
                    buttonTestTag = "setup_meet_app_button",
                    onAction = { DeviceReadinessHelper.openGoogleMeetStorePage(context) }
                )
            }

            item(key = "oppo_guide_card") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = "Oppo and ColorOS background guide",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (readiness.isOppoOrColorOs) {
                                    "Oppo ColorOS Reliability Checklist"
                                } else {
                                    "Oppo Reno 11 & OEM Background Checklist"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Text(
                            text = "1. Open App Info -> Battery usage -> enable Allow foreground activity & Allow background activity.\n" +
                                "2. Enable Allow auto-launch so scheduled meetings survive reboots.\n" +
                                "3. Turn off \"Pause app activity if unused\" in App Info -> Permissions.\n" +
                                "4. Optional: Lock MeetPilot in Recent Apps so ColorOS never clears it during long waiting rooms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = { DeviceReadinessHelper.openAppDetailsSettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_app_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open App Info",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Open MeetPilot App Info")
                        }
                    }
                }
            }

            item(key = "preferences_card") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Alert & Auto-Join Defaults",
                            style = MaterialTheme.typography.titleMedium
                        )

                        PreferenceSwitchRow(
                            title = "Sound alert when connected",
                            subtitle = "Plays notification chime once host admits you",
                            checked = prefs.alertSoundOnConnect,
                            onCheckedChange = onSetAlertSound
                        )

                        PreferenceSwitchRow(
                            title = "Vibrate when connected",
                            subtitle = "Double-pulse vibration alert on admission",
                            checked = prefs.alertVibrateOnConnect,
                            onCheckedChange = onSetAlertVibrate
                        )

                        PreferenceSwitchRow(
                            title = "Default auto-mute microphone",
                            subtitle = "Pre-enabled for new schedules",
                            checked = prefs.defaultAutoMuteMic,
                            onCheckedChange = onSetDefaultMute
                        )

                        PreferenceSwitchRow(
                            title = "Default auto-camera off",
                            subtitle = "Pre-enabled for new schedules",
                            checked = prefs.defaultAutoCamOff,
                            onCheckedChange = onSetDefaultCamOff
                        )

                        Button(
                            onClick = onTestAlertNotification,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("test_alert_notification_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Test connection alert",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Test Connection Alert Notification")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadinessCheckCard(
    icon: ImageVector,
    title: String,
    description: String,
    isReady: Boolean,
    actionLabel: String,
    buttonTestTag: String,
    onAction: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isReady) StatusConnected.copy(alpha = 0.4f) else StatusWaiting.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = (if (isReady) StatusConnected else StatusWaiting).copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (isReady) "Ready" else "Action needed",
                            modifier = Modifier.size(14.dp),
                            tint = if (isReady) StatusConnected else StatusWaiting
                        )
                        Text(
                            text = if (isReady) "Ready" else "Needed",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isReady) StatusConnected else StatusWaiting
                        )
                    }
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(buttonTestTag)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
