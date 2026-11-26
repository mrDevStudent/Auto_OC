package com.example.automation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.MeetPilotApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object AutoJoinCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _session = MutableStateFlow(AutoJoinSession())
    val session: StateFlow<AutoJoinSession> = _session.asStateFlow()

    @Volatile
    var lastMeetWindowSeenMillis: Long = 0L

    @Volatile
    var accountSwitchAttempts: Int = 0

    @Volatile
    var camDisableAttempts: Int = 0

    @Volatile
    var micDisableAttempts: Int = 0

    fun isSessionActive(): Boolean = _session.value.phase.isActive()

    fun touchMeetWindowSeen() {
        lastMeetWindowSeenMillis = System.currentTimeMillis()
    }

    fun startSession(
        context: Context,
        scheduleId: Long?,
        title: String,
        meetingCode: String,
        preferredGmail: String = "",
        autoMuteMic: Boolean,
        autoTurnOffCamera: Boolean,
        retryIntervalSeconds: Int
    ) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        lastMeetWindowSeenMillis = now
        accountSwitchAttempts = 0
        camDisableAttempts = 0
        micDisableAttempts = 0

        val cleanEmail = preferredGmail.trim().lowercase()
        val accessibilityReady = DeviceReadinessHelper.isAccessibilityServiceEnabled(appContext)
        val initialMessage = if (!accessibilityReady) {
            "Accessibility OFF — enable MeetPilot Auto-Join in Setup for hands-free join"
        } else if (cleanEmail.isNotEmpty()) {
            "Opening Google Meet ($meetingCode) as $cleanEmail"
        } else {
            "Opening Google Meet ($meetingCode)"
        }

        val newSession = AutoJoinSession(
            scheduleId = scheduleId,
            title = title,
            meetingCode = meetingCode,
            preferredGmail = cleanEmail,
            phase = AutoJoinPhase.LAUNCHING_MEET,
            attemptCount = 1,
            autoMuteMic = autoMuteMic,
            autoTurnOffCamera = autoTurnOffCamera,
            retryIntervalSeconds = retryIntervalSeconds.coerceIn(8, 120),
            startedAtMillis = now,
            lastActionMessage = initialMessage,
            lastUpdatedMillis = now
        )
        _session.value = newSession

        logEvent(
            appContext,
            scheduleId = scheduleId,
            title = title,
            meetingCode = meetingCode,
            status = "STARTED",
            message = if (accessibilityReady) {
                "Auto-join started (Attempt #1)" +
                    if (cleanEmail.isNotEmpty()) " • Gmail: $cleanEmail" else ""
            } else {
                "Opened Meet, but Auto-Join Accessibility Service is disabled in Setup"
            }
        )

        val serviceIntent = Intent(appContext, JoinCoordinatorService::class.java)
        ContextCompat.startForegroundService(appContext, serviceIntent)
    }

    fun stopSession(context: Context, reason: String = "Stopped by user") {
        val appContext = context.applicationContext
        val current = _session.value
        if (current.phase == AutoJoinPhase.IDLE && current.title.isEmpty()) {
            appContext.stopService(Intent(appContext, JoinCoordinatorService::class.java))
            return
        }

        if (current.phase.isActive()) {
            logEvent(
                appContext,
                scheduleId = current.scheduleId,
                title = current.title,
                meetingCode = current.meetingCode,
                status = "STOPPED",
                message = reason
            )
        }

        _session.value = AutoJoinSession(phase = AutoJoinPhase.IDLE)
        val app = appContext as MeetPilotApplication
        app.notificationManager.cancelOngoingStatus()
        appContext.stopService(Intent(appContext, JoinCoordinatorService::class.java))
    }

    fun dismissConnectedBanner() {
        if (_session.value.phase == AutoJoinPhase.CONNECTED) {
            _session.value = AutoJoinSession(phase = AutoJoinPhase.IDLE)
        }
    }

    fun onMeetLaunched(context: Context, launched: Boolean) {
        val current = _session.value
        if (!current.phase.isActive()) return
        val accessibilityReady = DeviceReadinessHelper.isAccessibilityServiceEnabled(context)
        val nextPhase = if (launched) AutoJoinPhase.LOOKING_FOR_JOIN_BUTTON else AutoJoinPhase.RETRYING
        val msg = when {
            !launched -> "Could not launch Google Meet yet; will retry"
            !accessibilityReady -> "Meet opened — enable Auto-Join Accessibility in Setup"
            else -> "Preparing pre-join (account, camera/mic, Join)..."
        }
        updateSession(context, nextPhase, msg)
    }

    fun onAccountSwitched(context: Context, detail: String) {
        val current = _session.value
        if (!current.phase.isActive()) return
        updateSession(context, AutoJoinPhase.LOOKING_FOR_JOIN_BUTTON, detail)
    }

    fun onJoinButtonTapped(context: Context, buttonLabel: String) {
        val current = _session.value
        if (!current.phase.isActive()) return
        val wasAlreadyWaiting = current.phase == AutoJoinPhase.WAITING_FOR_ADMISSION
        updateSession(
            context,
            AutoJoinPhase.WAITING_FOR_ADMISSION,
            "Tapped \"$buttonLabel\" — waiting for admission"
        )
        if (!wasAlreadyWaiting) {
            logEvent(
                context,
                scheduleId = current.scheduleId,
                title = current.title,
                meetingCode = current.meetingCode,
                status = "WAITING",
                message = "Tapped \"$buttonLabel\" — waiting for host to admit you"
            )
        }
    }

    fun onWaitingForAdmission(context: Context) {
        val current = _session.value
        if (!current.phase.isActive()) return
        val wasAlreadyWaiting = current.phase == AutoJoinPhase.WAITING_FOR_ADMISSION
        updateSession(
            context,
            AutoJoinPhase.WAITING_FOR_ADMISSION,
            "In waiting room — monitoring until accepted"
        )
        if (!wasAlreadyWaiting) {
            logEvent(
                context,
                scheduleId = current.scheduleId,
                title = current.title,
                meetingCode = current.meetingCode,
                status = "WAITING",
                message = "Waiting for someone in the meeting to let you in"
            )
        }
    }

    fun onRetryRequested(context: Context, reason: String) {
        val current = _session.value
        if (!current.phase.isActive()) return
        camDisableAttempts = 0
        micDisableAttempts = 0
        val nextAttempt = current.attemptCount + 1
        val updated = current.copy(
            phase = AutoJoinPhase.RETRYING,
            attemptCount = nextAttempt,
            lastActionMessage = reason,
            lastUpdatedMillis = System.currentTimeMillis()
        )
        _session.value = updated
        val app = context.applicationContext as MeetPilotApplication
        app.notificationManager.updateOngoingStatus(updated)

        logEvent(
            context,
            scheduleId = current.scheduleId,
            title = current.title,
            meetingCode = current.meetingCode,
            status = "RETRYING",
            message = "Attempt #$nextAttempt: $reason"
        )
    }

    fun onConnected(context: Context) {
        val appContext = context.applicationContext
        val current = _session.value
        if (!current.phase.isActive() || current.phase == AutoJoinPhase.CONNECTED) return

        val now = System.currentTimeMillis()
        val connectedSession = current.copy(
            phase = AutoJoinPhase.CONNECTED,
            lastActionMessage = "Connected to Google Meet!",
            lastUpdatedMillis = now
        )
        _session.value = connectedSession

        val app = appContext as MeetPilotApplication
        app.notificationManager.cancelOngoingStatus()

        scope.launch {
            val prefs = app.settingsDataStore.preferencesFlow.first()
            app.notificationManager.notifyConnectionEstablished(
                title = current.title,
                meetingCode = current.meetingCode,
                playSound = prefs.alertSoundOnConnect,
                vibrate = prefs.alertVibrateOnConnect,
                isTest = false
            )
            app.repository.appendLog(
                scheduleId = current.scheduleId,
                title = current.title,
                meetingCode = current.meetingCode,
                status = "CONNECTED",
                message = "Admitted and connected after ${current.attemptCount} attempt(s)"
            )
        }

        appContext.stopService(Intent(appContext, JoinCoordinatorService::class.java))
    }

    private fun updateSession(context: Context, phase: AutoJoinPhase, message: String) {
        val current = _session.value
        if (!current.phase.isActive()) return
        val updated = current.copy(
            phase = phase,
            lastActionMessage = message,
            lastUpdatedMillis = System.currentTimeMillis()
        )
        _session.value = updated
        val app = context.applicationContext as MeetPilotApplication
        app.notificationManager.updateOngoingStatus(updated)
    }

    private fun logEvent(
        context: Context,
        scheduleId: Long?,
        title: String,
        meetingCode: String,
        status: String,
        message: String
    ) {
        val app = context.applicationContext as MeetPilotApplication
        scope.launch {
            app.repository.appendLog(
                scheduleId = scheduleId,
                title = title,
                meetingCode = meetingCode,
                status = status,
                message = message
            )
        }
    }
}
