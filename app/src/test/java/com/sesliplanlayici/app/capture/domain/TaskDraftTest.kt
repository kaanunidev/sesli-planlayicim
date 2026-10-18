package com.sesliplanlayici.app.capture.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskDraftTest {

    private fun draft(date: LocalDate?, time: LocalTime?) = TaskDraft(
        title = "Annemi ara",
        date = date,
        time = time,
        durationMinutes = null,
        reminderText = null,
    )

    @Test
    fun nothingMissingWhenDateAndTimeAreSet() {
        assertEquals(emptySet<DraftField>(), draft(LocalDate.of(2026, 10, 12), LocalTime.of(10, 0)).missing)
    }

    @Test
    fun onlyTimeMissingForAllDayTask() {
        assertEquals(setOf(DraftField.TIME), draft(LocalDate.of(2026, 10, 12), null).missing)
    }

    @Test
    fun bothMissingWhenNoDayWasSaid() {
        assertEquals(setOf(DraftField.DATE, DraftField.TIME), draft(null, null).missing)
    }
}
