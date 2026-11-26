package com.example.data

import com.example.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class MeetRepository(
    private val dao: MeetingDao,
    private val alarmScheduler: AlarmScheduler
) {
    val allSchedules: Flow<List<MeetingScheduleEntity>> = dao.observeAllSchedules()
    val recentLogs: Flow<List<JoinLogEntity>> = dao.observeRecentLogs()

    suspend fun saveSchedule(schedule: MeetingScheduleEntity): Long {
        val id = dao.upsertSchedule(schedule)
        val saved = schedule.copy(id = if (schedule.id == 0L) id else schedule.id)
        if (saved.isEnabled) {
            alarmScheduler.schedule(saved)
        } else {
            alarmScheduler.cancel(saved.id)
        }
        return saved.id
    }

    suspend fun toggleSchedule(schedule: MeetingScheduleEntity, enabled: Boolean) {
        dao.setScheduleEnabled(schedule.id, enabled)
        if (enabled) {
            alarmScheduler.schedule(schedule.copy(isEnabled = true))
        } else {
            alarmScheduler.cancel(schedule.id)
        }
    }

    suspend fun deleteSchedule(id: Long) {
        alarmScheduler.cancel(id)
        dao.deleteScheduleById(id)
    }

    suspend fun getScheduleById(id: Long): MeetingScheduleEntity? = dao.getScheduleById(id)

    suspend fun getEnabledSchedules(): List<MeetingScheduleEntity> = dao.getEnabledSchedules()

    suspend fun markTriggeredAndRescheduleIfRepeating(id: Long) {
        val now = System.currentTimeMillis()
        dao.markTriggered(id, now)
        val schedule = dao.getScheduleById(id) ?: return
        if (schedule.daysOfWeekBitmask == 0) {
            dao.setScheduleEnabled(id, false)
            alarmScheduler.cancel(id)
        } else if (schedule.isEnabled) {
            alarmScheduler.schedule(schedule, now + 61_000L)
        }
    }

    suspend fun rescheduleAllEnabled() {
        val enabled = dao.getEnabledSchedules()
        enabled.forEach { alarmScheduler.schedule(it) }
    }

    suspend fun appendLog(
        scheduleId: Long?,
        title: String,
        meetingCode: String,
        status: String,
        message: String
    ) {
        dao.insertLog(
            JoinLogEntity(
                scheduleId = scheduleId,
                meetingTitle = title,
                meetingCode = meetingCode,
                status = status,
                message = message
            )
        )
    }

    suspend fun clearLogs() {
        dao.clearLogs()
    }
}
