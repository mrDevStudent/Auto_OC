package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.MeetingScheduleEntity
import com.example.ui.ActivityLogScreen
import com.example.ui.MeetPilotViewModel
import com.example.ui.ScheduleEditorSheet
import com.example.ui.SchedulesScreen
import com.example.ui.SetupGuideScreen
import com.example.ui.theme.MeetPilotTheme
import kotlinx.coroutines.launch

const val ROUTE_SCHEDULES = "schedules"
const val ROUTE_ACTIVITY = "activity"
const val ROUTE_SETUP = "setup"

private data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeetPilotTheme {
                MeetPilotApp()
            }
        }
    }
}

@Composable
fun MeetPilotApp(
    viewModel: MeetPilotViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_SCHEDULES

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showEditorSheet by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<MeetingScheduleEntity?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshReadiness()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !uiState.readiness.notificationsAllowed) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshReadiness()
    }

    val tabs = remember {
        listOf(
            NavTabItem(
                route = ROUTE_SCHEDULES,
                labelRes = R.string.nav_schedules,
                selectedIcon = Icons.Filled.CalendarMonth,
                unselectedIcon = Icons.Outlined.CalendarMonth,
                testTag = "nav_schedules"
            ),
            NavTabItem(
                route = ROUTE_ACTIVITY,
                labelRes = R.string.nav_activity,
                selectedIcon = Icons.Filled.History,
                unselectedIcon = Icons.Outlined.History,
                testTag = "nav_activity"
            ),
            NavTabItem(
                route = ROUTE_SETUP,
                labelRes = R.string.nav_setup,
                selectedIcon = Icons.Filled.Tune,
                unselectedIcon = Icons.Outlined.Tune,
                testTag = "nav_setup"
            )
        )
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                if (currentRoute == ROUTE_SCHEDULES) {
                    FloatingActionButton(
                        onClick = {
                            editingSchedule = null
                            showEditorSheet = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_schedule_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.action_add_schedule)
                        )
                    }
                }
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(tab.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val label = stringResource(tab.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_SCHEDULES,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_SCHEDULES) {
                        SchedulesScreen(
                            uiState = uiState,
                            onAddClick = {
                                editingSchedule = null
                                showEditorSheet = true
                            },
                            onEditClick = { schedule ->
                                editingSchedule = schedule
                                showEditorSheet = true
                            },
                            onToggleSchedule = { schedule, enabled ->
                                viewModel.toggleSchedule(schedule, enabled)
                            },
                            onDeleteSchedule = { id ->
                                viewModel.deleteSchedule(id)
                            },
                            onJoinNowClick = { schedule ->
                                viewModel.startJoinNow(schedule)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Auto-join started for ${schedule.title}")
                                }
                            },
                            onStopAutoJoin = {
                                viewModel.stopActiveAutoJoin()
                            },
                            onDismissConnected = {
                                viewModel.dismissConnectedBanner()
                            },
                            onNavigateToSetup = {
                                navigateToTab(ROUTE_SETUP)
                            }
                        )
                    }

                    composable(ROUTE_ACTIVITY) {
                        ActivityLogScreen(
                            logs = uiState.logs,
                            onClearLogs = { viewModel.clearLogs() }
                        )
                    }

                    composable(ROUTE_SETUP) {
                        SetupGuideScreen(
                            uiState = uiState,
                            onRefreshReadiness = { viewModel.refreshReadiness() },
                            onSetAlertSound = { viewModel.setAlertSound(it) },
                            onSetAlertVibrate = { viewModel.setAlertVibrate(it) },
                            onSetDefaultMute = { viewModel.setDefaultAutoMute(it) },
                            onSetDefaultCamOff = { viewModel.setDefaultAutoCamOff(it) },
                            onTestAlertNotification = {
                                viewModel.sendTestConnectionAlert()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (uiState.readiness.notificationsAllowed) {
                                            "Test connection alert sent!"
                                        } else {
                                            "Grant Notification permission above to preview alerts."
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEditorSheet) {
        ScheduleEditorSheet(
            existingSchedule = editingSchedule,
            defaults = uiState.preferences,
            onDismiss = {
                showEditorSheet = false
                editingSchedule = null
            },
            onSave = { existingId, title, rawCode, hour, minute, daysBitmask, mute, camOff, retrySec ->
                viewModel.saveSchedule(
                    existingId = existingId,
                    title = title,
                    rawCode = rawCode,
                    hour = hour,
                    minute = minute,
                    daysBitmask = daysBitmask,
                    autoMuteMic = mute,
                    autoTurnOffCamera = camOff,
                    retryIntervalSeconds = retrySec
                )
            }
        )
    }
}
