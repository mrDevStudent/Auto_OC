package com.example

import com.example.data.MeetingCodeParser
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeetingCodeParserTest {

    @Test
    fun normalizeMeetCode_acceptsCodeAndUrls() {
        assertEquals("abc-defg-hij", MeetingCodeParser.normalizeMeetCode("abc-defg-hij"))
        assertEquals("abc-defg-hij", MeetingCodeParser.normalizeMeetCode("ABC-DEFG-HIJ"))
        assertEquals("abc-defg-hij", MeetingCodeParser.normalizeMeetCode("abcdefghij"))
        assertEquals(
            "abc-defg-hij",
            MeetingCodeParser.normalizeMeetCode("https://meet.google.com/abc-defg-hij?authuser=0")
        )
        assertEquals(
            "abc-defg-hij",
            MeetingCodeParser.normalizeMeetCode("meet.google.com/abc-defg-hij/")
        )
        assertNull(MeetingCodeParser.normalizeMeetCode("   "))
    }

    @Test
    fun computeNextTriggerMillis_selectsMondayFivePm() {
        // Thursday 2026-11-26 09:00 AM local time
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 26, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val mondayBitmask = 1 shl 0 // Monday

        val nextMillis = MeetingCodeParser.computeNextTriggerMillis(
            hour = 17,
            minute = 0,
            daysBitmask = mondayBitmask,
            nowMillis = nowCal.timeInMillis
        )

        val resultCal = Calendar.getInstance().apply { timeInMillis = nextMillis }
        assertEquals(Calendar.MONDAY, resultCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(17, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
        assertEquals(2026, resultCal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, resultCal.get(Calendar.MONTH))
        assertEquals(30, resultCal.get(Calendar.DAY_OF_MONTH))
    }
}
