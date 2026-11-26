package com.example.automation

enum class AutoJoinPhase {
    IDLE,
    LAUNCHING_MEET,
    LOOKING_FOR_JOIN_BUTTON,
    WAITING_FOR_ADMISSION,
    RETRYING,
    CONNECTED,
    STOPPED;

    fun isActive(): Boolean = this == LAUNCHING_MEET ||
        this == LOOKING_FOR_JOIN_BUTTON ||
        this == WAITING_FOR_ADMISSION ||
        this == RETRYING
}

data class AutoJoinSession(
    val scheduleId: Long? = null,
    val title: String = "",
    val meetingCode: String = "",
    val phase: AutoJoinPhase = AutoJoinPhase.IDLE,
    val attemptCount: Int = 0,
    val autoMuteMic: Boolean = true,
    val autoTurnOffCamera: Boolean = true,
    val retryIntervalSeconds: Int = 15,
    val startedAtMillis: Long = 0L,
    val lastActionMessage: String = "",
    val lastUpdatedMillis: Long = 0L
)
