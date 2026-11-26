package com.example.data

import java.util.Calendar

object MeetingCodeParser {
    private val STANDARD_CODE_REGEX = Regex("([a-z]{3}-[a-z]{4}-[a-z]{3})")
    private val COMPACT_CODE_REGEX = Regex("^[a-z]{10}$")
    private val NICKNAME_REGEX = Regex("^[a-zA-Z0-9_-]{3,40}$")

    fun normalizeMeetCode(rawInput: String): String? {
        val trimmed = rawInput.trim().lowercase()
        if (trimmed.isEmpty()) return null

        val withoutQuery = trimmed.substringBefore('?').substringBefore('#').trimEnd('/')
        val segment = if (withoutQuery.contains("meet.google.com/")) {
            withoutQuery.substringAfter("meet.google.com/").substringAfterLast('/')
        } else {
            withoutQuery
        }

        val standardMatch = STANDARD_CODE_REGEX.find(segment)
        if (standardMatch != null) {
            return standardMatch.groupValues[1]
        }

        val compactOnly = segment.replace("-", "").replace(" ", "")
        if (COMPACT_CODE_REGEX.matches(compactOnly)) {
            return "${compactOnly.substring(0, 3)}-${compactOnly.substring(3, 7)}-${compactOnly.substring(7, 10)}"
        }

        if (NICKNAME_REGEX.matches(segment)) {
            return segment
        }
        return null
    }

    fun toMeetUrl(codeOrUrl: String): String {
        val normalized = normalizeMeetCode(codeOrUrl) ?: codeOrUrl.trim()
        return "https://meet.google.com/$normalized"
    }

    fun formatTime12Hour(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "%d:%02d %s".format(displayHour, minute, amPm)
    }

    /**
     * Bitmask: bit 0 = Monday (Calendar.MONDAY=2), ..., bit 6 = Sunday (Calendar.SUNDAY=1)
     */
    val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val DAY_SHORT_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

    fun calendarDayToBitIndex(calendarDay: Int): Int = when (calendarDay) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }

    fun isDaySelected(bitmask: Int, dayIndex: Int): Boolean = (bitmask and (1 shl dayIndex)) != 0

    fun toggleDay(bitmask: Int, dayIndex: Int): Int = bitmask xor (1 shl dayIndex)

    fun formatDaysSummary(bitmask: Int): String {
        if (bitmask == 0) return "One-time (Next occurrence)"
        if (bitmask == 0b1111111) return "Every day"
        if (bitmask == 0b0011111) return "Weekdays (Mon–Fri)"
        if (bitmask == 0b1100000) return "Weekends"
        val selected = (0..6).filter { isDaySelected(bitmask, it) }.map { DAY_LABELS[it] }
        return selected.joinToString(", ")
    }

    fun computeNextTriggerMillis(
        hour: Int,
        minute: Int,
        daysBitmask: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val base = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (daysBitmask == 0) {
            val candidate = (base.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            if (candidate.timeInMillis <= nowMillis) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
            return candidate.timeInMillis
        }

        for (offset in 0..7) {
            val candidate = (base.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val dayBit = calendarDayToBitIndex(candidate.get(Calendar.DAY_OF_WEEK))
            if (isDaySelected(daysBitmask, dayBit) && candidate.timeInMillis > nowMillis) {
                return candidate.timeInMillis
            }
        }

        val fallback = (base.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return fallback.timeInMillis
    }

    fun formatCountdown(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = (targetMillis - nowMillis).coerceAtLeast(0L)
        val totalMinutes = diff / 60_000L
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "in ${days}d ${hours}h"
            hours > 0 -> "in ${hours}h ${minutes}m"
            minutes > 0 -> "in ${minutes}m"
            else -> "in < 1m"
        }
    }
}
