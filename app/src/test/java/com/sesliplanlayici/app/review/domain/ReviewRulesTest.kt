package com.sesliplanlayici.app.review.domain

import com.sesliplanlayici.app.capture.domain.TaskDraft
import com.sesliplanlayici.app.core.domain.TaskSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRulesTest {

    private val today = LocalDate.of(2026, 10, 11)
    private val now = LocalDateTime.of(today, LocalTime.of(21, 40))

    private fun draft(date: LocalDate?, time: LocalTime? = null) = TaskDraft("İş", date, time, null, null)

    private fun item(date: LocalDate?) = ReviewItem(0, draft(date), TaskSource.VOICE)

    // --- canSaveAll

    @Test
    fun emptyListCannotBeSaved() {
        assertFalse(ReviewRules.canSaveAll(emptyList()))
    }

    @Test
    fun aCardWithoutDayBlocksSaving() {
        assertFalse(ReviewRules.canSaveAll(listOf(item(today), item(null))))
    }

    @Test
    fun everyCardWithDayCanBeSavedEvenWithoutTime() {
        assertTrue(ReviewRules.canSaveAll(listOf(item(today), item(today.plusDays(1)))))
    }

    // --- isInPast

    @Test
    fun yesterdayIsPast() {
        assertTrue(ReviewRules.isInPast(draft(today.minusDays(1)), now))
    }

    @Test
    fun earlierTimeTodayIsPast() {
        assertTrue(ReviewRules.isInPast(draft(today, LocalTime.of(10, 0)), now))
    }

    @Test
    fun laterTimeTodayIsNotPast() {
        assertFalse(ReviewRules.isInPast(draft(today, LocalTime.of(22, 0)), now))
    }

    @Test
    fun allDayTaskTodayIsNotPast() {
        assertFalse(ReviewRules.isInPast(draft(today), now))
    }

    @Test
    fun tomorrowAndNoDayAreNotPast() {
        assertFalse(ReviewRules.isInPast(draft(today.plusDays(1), LocalTime.of(0, 0)), now))
        assertFalse(ReviewRules.isInPast(draft(null), now))
    }

    @Test
    fun exactlyNowIsNotPast() {
        assertFalse(ReviewRules.isInPast(draft(today, LocalTime.of(21, 40)), now))
    }

    @Test
    fun justAfterMidnightYesterdaysLateTaskIsPast() {
        val afterMidnight = LocalDateTime.of(today.plusDays(1), LocalTime.of(0, 5))
        assertTrue(ReviewRules.isInPast(draft(today, LocalTime.of(23, 30)), afterMidnight))
    }

    // --- dayLabel

    @Test
    fun todayAndTomorrowAreNamed() {
        assertEquals(DayLabel.Today(today), dayLabel(today, today))
        assertEquals(DayLabel.Tomorrow(today.plusDays(1)), dayLabel(today.plusDays(1), today))
        assertEquals(DayLabel.Other(today.plusDays(2)), dayLabel(today.plusDays(2), today))
    }

    @Test
    fun tomorrowAcrossMonthAndYearEnds() {
        assertEquals(
            DayLabel.Tomorrow(LocalDate.of(2026, 11, 1)),
            dayLabel(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 31)),
        )
        assertEquals(
            DayLabel.Tomorrow(LocalDate.of(2027, 1, 1)),
            dayLabel(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31)),
        )
    }

    @Test
    fun yesterdayIsJustADay() {
        assertEquals(DayLabel.Other(today.minusDays(1)), dayLabel(today.minusDays(1), today))
    }
}
