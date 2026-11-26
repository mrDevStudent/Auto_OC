package com.example.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.example.MeetPilotApplication
import com.example.automation.AutoJoinCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MeetingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULE_ID, -1L)
        if (scheduleId <= 0L) return

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MeetPilot:AlarmWakeLock"
        )
        wakeLock?.acquire(15_000L)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MeetPilotApplication
                val schedule = app.repository.getScheduleById(scheduleId)
                if (schedule != null && schedule.isEnabled) {
                    AutoJoinCoordinator.startSession(
                        context = app,
                        scheduleId = schedule.id,
                        title = schedule.title,
                        meetingCode = schedule.meetingCode,
                        autoMuteMic = schedule.autoMuteMic,
                        autoTurnOffCamera = schedule.autoTurnOffCamera,
                        retryIntervalSeconds = schedule.retryIntervalSeconds
                    )
                    app.repository.markTriggeredAndRescheduleIfRepeating(schedule.id)
                }
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
                pendingResult.finish()
            }
        }
    }
}
