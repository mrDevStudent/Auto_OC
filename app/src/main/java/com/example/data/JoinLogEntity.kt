package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "join_logs")
data class JoinLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scheduleId: Long? = null,
    val meetingTitle: String,
    val meetingCode: String,
    val status: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
