package com.sesliplanlayici.app.core.data.db

import com.sesliplanlayici.app.core.domain.TaskSource
import com.sesliplanlayici.app.core.domain.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun datesRoundTripIncludingMonthAndYearEnds() {
        listOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 12, 31), LocalDate.of(2028, 2, 29)).forEach {
            assertEquals(it, converters.toLocalDate(converters.fromLocalDate(it)))
        }
    }

    @Test
    fun laterDatesSortAfterEarlierOnes() {
        val earlier = converters.fromLocalDate(LocalDate.of(2026, 10, 31)) ?: error("null")
        val later = converters.fromLocalDate(LocalDate.of(2026, 11, 1)) ?: error("null")
        assert(later > earlier)
    }

    @Test
    fun timesRoundTripFromMidnightToLastMinute() {
        listOf(LocalTime.MIDNIGHT, LocalTime.of(10, 0), LocalTime.of(23, 59)).forEach {
            assertEquals(it, converters.toLocalTime(converters.fromLocalTime(it)))
        }
    }

    @Test
    fun instantsRoundTripToTheMillisecond() {
        val instant = Instant.parse("2026-10-11T18:40:00.123Z")
        assertEquals(instant, converters.toInstant(converters.fromInstant(instant)))
    }

    @Test
    fun nullsStayNull() {
        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalTime(null))
        assertNull(converters.fromInstant(null))
    }

    @Test
    fun enumsAreStoredByName() {
        assertEquals("NOT_DONE", converters.fromStatus(TaskStatus.NOT_DONE))
        assertEquals(TaskSource.MANUAL, converters.toSource("MANUAL"))
    }
}
