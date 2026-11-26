package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MeetPilotApplication
import com.example.automation.AutoJoinCoordinator
import com.example.automation.AutoJoinSession
import com.example.automation.DeviceReadinessHelper
import com.example.automation.DeviceReadinessStatus
import com.example.data.JoinLogEntity
import com.example.data.MeetingCodeParser
import com.example.data.MeetingScheduleEntity
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MeetPilotUiState(
    val schedules: List<MeetingScheduleEntity> = emptyList(),
    val logs: List<JoinLogEntity> = emptyList(),
    val activeSession: AutoJoinSession = AutoJoinSession(),
    val preferences: UserPreferences = UserPreferences(),
    val readiness: DeviceReadinessStatus = DeviceReadinessStatus()
)

class MeetPilotViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MeetPilotApplication
    private val repository = app.repository
    private val settings = app.settingsDataStore

    private val readinessFlow = MutableStateFlow(DeviceReadinessHelper.inspect(app))

    val uiState: StateFlow<MeetPilotUiState> = combine(
        repository.allSchedules,
        repository.recentLogs,
        AutoJoinCoordinator.session,
        settings.preferencesFlow,
        readinessFlow
    ) { schedules, logs, session, prefs, readiness ->
        MeetPilotUiState(
            schedules = schedules,
            logs = logs,
            activeSession = session,
            preferences = prefs,
            readiness = readiness
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MeetPilotUiState(readiness = readinessFlow.value)
    )

    fun refreshReadiness() {
        readinessFlow.value = DeviceReadinessHelper.inspect(app)
    }

    fun saveSchedule(
        existingId: Long?,
        title: String,
        rawCode: String,
        hour: Int,
        minute: Int,
        daysBitmask: Int,
        autoMuteMic: Boolean,
        autoTurnOffCamera: Boolean,
        retryIntervalSeconds: Int
    ): String? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return "Please enter a class or meeting name."
        }
        val normalizedCode = MeetingCodeParser.normalizeMeetCode(rawCode)
            ?: return "Enter a valid Google Meet code (e.g. abc-defg-hij) or Meet link."

        viewModelScope.launch {
            val entity = MeetingScheduleEntity(
                id = existingId ?: 0L,
                title = cleanTitle,
                meetingCode = normalizedCode,
                hour = hour,
                minute = minute,
                daysOfWeekBitmask = daysBitmask,
                isEnabled = true,
                autoMuteMic = autoMuteMic,
                autoTurnOffCamera = autoTurnOffCamera,
                retryIntervalSeconds = retryIntervalSeconds
            )
            repository.saveSchedule(entity)
        }
        return null
    }

    fun toggleSchedule(schedule: MeetingScheduleEntity, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleSchedule(schedule, enabled)
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            repository.deleteSchedule(id)
        }
    }

    fun startJoinNow(schedule: MeetingScheduleEntity) {
        AutoJoinCoordinator.startSession(
            context = app,
            scheduleId = schedule.id,
            title = schedule.title,
            meetingCode = schedule.meetingCode,
            autoMuteMic = schedule.autoMuteMic,
            autoTurnOffCamera = schedule.autoTurnOffCamera,
            retryIntervalSeconds = schedule.retryIntervalSeconds
        )
    }

    fun stopActiveAutoJoin() {
        AutoJoinCoordinator.stopSession(app, reason = "Stopped manually in MeetPilot")
    }

    fun dismissConnectedBanner() {
        AutoJoinCoordinator.dismissConnectedBanner()
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun setAlertSound(enabled: Boolean) {
        viewModelScope.launch { settings.setAlertSound(enabled) }
    }

    fun setAlertVibrate(enabled: Boolean) {
        viewModelScope.launch { settings.setAlertVibrate(enabled) }
    }

    fun setDefaultAutoMute(enabled: Boolean) {
        viewModelScope.launch { settings.setDefaultAutoMute(enabled) }
    }

    fun setDefaultAutoCamOff(enabled: Boolean) {
        viewModelScope.launch { settings.setDefaultAutoCamOff(enabled) }
    }

    fun setDefaultRetrySeconds(seconds: Int) {
        viewModelScope.launch { settings.setDefaultRetrySeconds(seconds) }
    }

    fun sendTestConnectionAlert() {
        val prefs = uiState.value.preferences
        app.notificationManager.notifyConnectionEstablished(
            title = "Sample Lecture",
            meetingCode = "abc-defg-hij",
            playSound = prefs.alertSoundOnConnect,
            vibrate = prefs.alertVibrateOnConnect,
            isTest = true
        )
    }
}
