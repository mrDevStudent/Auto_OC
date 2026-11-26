package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.data.MeetingCodeParser
import com.example.data.MeetingScheduleEntity

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(schedule: MeetingScheduleEntity, fromMillis: Long = System.currentTimeMillis()) {
        if (!schedule.isEnabled || schedule.id == 0L) {
            cancel(schedule.id)
            return
        }

        val triggerAtMillis = MeetingCodeParser.computeNextTriggerMillis(
            hour = schedule.hour,
            minute = schedule.minute,
            daysBitmask = schedule.daysOfWeekBitmask,
            nowMillis = fromMillis
        )

        val alarmIntent = createAlarmPendingIntent(schedule.id, schedule.title, schedule.meetingCode)
        val showAppIntent = PendingIntent.getActivity(
            context,
            schedule.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canUseExact) {
            try {
                // setAlarmClock gets top priority on ColorOS / Oppo and wakes Doze reliably
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showAppIntent)
                alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)
                return
            } catch (_: SecurityException) {
                // Fallback below
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmIntent)
        }
    }

    fun cancel(scheduleId: Long) {
        if (scheduleId == 0L) return
        val existing = PendingIntent.getBroadcast(
            context,
            requestCodeFor(scheduleId),
            Intent(context, MeetingAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existing != null) {
            alarmManager.cancel(existing)
            existing.cancel()
        }
    }

    private fun createAlarmPendingIntent(
        scheduleId: Long,
        title: String,
        meetingCode: String
    ): PendingIntent {
        val intent = Intent(context, MeetingAlarmReceiver::class.java).apply {
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_MEETING_CODE, meetingCode)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(scheduleId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCodeFor(scheduleId: Long): Int = (40_000L + scheduleId).toInt()

    companion object {
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MEETING_CODE = "extra_meeting_code"
    }
}
