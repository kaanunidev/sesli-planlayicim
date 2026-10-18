package com.sesliplanlayici.app.capture.domain

import java.time.LocalDate
import java.time.LocalTime

/** Fields the user may still need to fill in on the review screen. */
enum class DraftField { DATE, TIME }

/**
 * A task extracted from speech that the user has not confirmed yet.
 *
 * [date] must be picked by the user before saving; [time] may stay empty (an all-day task).
 */
data class TaskDraft(
    val title: String,
    val date: LocalDate?,
    val time: LocalTime?,
    val durationMinutes: Int?,
    val reminderText: String?,
) {
    // Derived from the values instead of trusting the AI's own "missing" list.
    val missing: Set<DraftField>
        get() = buildSet {
            if (date == null) add(DraftField.DATE)
            if (time == null) add(DraftField.TIME)
        }
}

/** What the AI made of one voice session. */
sealed interface ExtractionResult {
    /** At least one task was found. */
    data class Plan(val drafts: List<TaskDraft>) : ExtractionResult

    /** The user talked about their day but there is nothing to do ("Bugün hiçbir şey yapmayacağım"). */
    data object NothingPlanned : ExtractionResult

    /** The speech had nothing to do with planning the day. */
    data object OffTopic : ExtractionResult
}
