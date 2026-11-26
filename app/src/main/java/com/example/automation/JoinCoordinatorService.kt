package com.example.automation

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.MeetPilotApplication
import com.example.notifications.MeetNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class JoinCoordinatorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as MeetPilotApplication
        val session = AutoJoinCoordinator.session.value

        val notification = app.notificationManager.buildOngoingStatusNotification(session)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            MeetNotificationManager.NOTIFICATION_ID_STATUS,
            notification,
            serviceType
        )

        if (!session.phase.isActive()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startMonitoringLoop()
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            var lastLaunchTimeMs = 0L

            while (isActive) {
                val session = AutoJoinCoordinator.session.value
                if (!session.phase.isActive()) {
                    stopSelf()
                    break
                }

                val now = System.currentTimeMillis()
                val intervalMs = session.retryIntervalSeconds * 1000L

                when (session.phase) {
                    AutoJoinPhase.LAUNCHING_MEET -> {
                        lastLaunchTimeMs = now
                        val launched = launchViaAccessibilityOrContext(session.meetingCode)
                        AutoJoinCoordinator.onMeetLaunched(applicationContext, launched)
                    }

                    AutoJoinPhase.RETRYING -> {
                        if (now - lastLaunchTimeMs >= 2_500L) {
                            lastLaunchTimeMs = now
                            val launched = launchViaAccessibilityOrContext(session.meetingCode)
                            AutoJoinCoordinator.onMeetLaunched(applicationContext, launched)
                        }
                    }

                    AutoJoinPhase.LOOKING_FOR_JOIN_BUTTON -> {
                        MeetAutoJoinAccessibilityService.activeInstance?.requestImmediateScan()
                        if (now - lastLaunchTimeMs >= intervalMs) {
                            AutoJoinCoordinator.onRetryRequested(
                                applicationContext,
                                "Pre-join button not completed yet — reopening Meet"
                            )
                        }
                    }

                    AutoJoinPhase.WAITING_FOR_ADMISSION -> {
                        // Let Google Meet stay in waiting room, periodically nudge accessibility scan
                        MeetAutoJoinAccessibilityService.activeInstance?.requestImmediateScan()
                    }

                    else -> Unit
                }

                delay(2_000L)
            }
        }
    }

    private fun launchViaAccessibilityOrContext(meetingCode: String): Boolean {
        val accessibilityService = MeetAutoJoinAccessibilityService.activeInstance
        val targetContext = accessibilityService ?: applicationContext
        return MeetLauncher.launchMeet(targetContext, meetingCode)
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
