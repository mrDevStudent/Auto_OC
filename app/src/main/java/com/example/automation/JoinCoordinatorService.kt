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
                val accessibilityEnabled = DeviceReadinessHelper.isAccessibilityServiceEnabled(applicationContext)

                when (session.phase) {
                    AutoJoinPhase.LAUNCHING_MEET -> {
                        lastLaunchTimeMs = now
                        val launched = launchViaAccessibilityOrContext(
                            session.meetingCode,
                            session.preferredGmail
                        )
                        AutoJoinCoordinator.onMeetLaunched(applicationContext, launched)
                    }

                    AutoJoinPhase.RETRYING -> {
                        if (now - lastLaunchTimeMs >= 2_500L) {
                            lastLaunchTimeMs = now
                            val launched = launchViaAccessibilityOrContext(
                                session.meetingCode,
                                session.preferredGmail
                            )
                            AutoJoinCoordinator.onMeetLaunched(applicationContext, launched)
                        }
                    }

                    AutoJoinPhase.LOOKING_FOR_JOIN_BUTTON -> {
                        if (accessibilityEnabled) {
                            MeetAutoJoinAccessibilityService.activeInstance?.requestImmediateScan()
                            if (now - lastLaunchTimeMs >= intervalMs) {
                                AutoJoinCoordinator.onRetryRequested(
                                    applicationContext,
                                    "Pre-join not completed yet — reopening Google Meet"
                                )
                            }
                        }
                    }

                    AutoJoinPhase.WAITING_FOR_ADMISSION -> {
                        if (accessibilityEnabled) {
                            MeetAutoJoinAccessibilityService.activeInstance?.requestImmediateScan()
                            val staleLimitMs = maxOf(intervalMs, 25_000L)
                            if (now - AutoJoinCoordinator.lastMeetWindowSeenMillis >= staleLimitMs) {
                                AutoJoinCoordinator.onRetryRequested(
                                    applicationContext,
                                    "Meet waiting room became inactive — rejoining"
                                )
                            }
                        }
                    }

                    else -> Unit
                }

                delay(1_000L)
            }
        }
    }

    private fun launchViaAccessibilityOrContext(
        meetingCode: String,
        preferredGmail: String
    ): Boolean {
        val accessibilityService = MeetAutoJoinAccessibilityService.activeInstance
        val targetContext = accessibilityService ?: applicationContext
        return MeetLauncher.launchMeet(targetContext, meetingCode, preferredGmail)
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
