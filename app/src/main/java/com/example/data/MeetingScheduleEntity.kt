package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meeting_schedules")
data class MeetingScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val meetingCode: String,
    val hour: Int,
    val minute: Int,
    val daysOfWeekBitmask: Int,
    val isEnabled: Boolean = true,
    val autoMuteMic: Boolean = true,
    val autoTurnOffCamera: Boolean = true,
    val retryIntervalSeconds: Int = 15,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null
)
