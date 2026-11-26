package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meeting_schedules ORDER BY hour ASC, minute ASC")
    fun observeAllSchedules(): Flow<List<MeetingScheduleEntity>>

    @Query("SELECT * FROM meeting_schedules WHERE isEnabled = 1")
    suspend fun getEnabledSchedules(): List<MeetingScheduleEntity>

    @Query("SELECT * FROM meeting_schedules WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: Long): MeetingScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: MeetingScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: MeetingScheduleEntity)

    @Query("DELETE FROM meeting_schedules WHERE id = :id")
    suspend fun deleteScheduleById(id: Long)

    @Query("UPDATE meeting_schedules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setScheduleEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE meeting_schedules SET lastTriggeredAt = :timestamp WHERE id = :id")
    suspend fun markTriggered(id: Long, timestamp: Long)

    @Query("SELECT * FROM join_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeRecentLogs(): Flow<List<JoinLogEntity>>

    @Insert
    suspend fun insertLog(log: JoinLogEntity)

    @Query("DELETE FROM join_logs")
    suspend fun clearLogs()
}
