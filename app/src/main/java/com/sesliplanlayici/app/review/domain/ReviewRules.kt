package com.sesliplanlayici.app.review.domain

import com.sesliplanlayici.app.capture.domain.TaskDraft
import com.sesliplanlayici.app.core.domain.TaskSource
import java.time.LocalDate
import java.time.LocalDateTime

/** A draft on the review screen; [id] keeps edits on the right card. */
data class ReviewItem(
    val id: Long,
    val draft: TaskDraft,
    val source: TaskSource,
)

object ReviewRules {
    const val MAX_TITLE_LENGTH = 60

    /** Saving needs at least one card, and every card needs a day (time stays optional). */
    fun canSaveAll(items: List<ReviewItem>): Boolean =
        items.isNotEmpty() && items.all { it.draft.date != null }

    /** A day before today, or today at a time that has passed. Shown as a warning, never blocks saving. */
    fun isInPast(draft: TaskDraft, now: LocalDateTime): Boolean {
        val date = draft.date ?: return false
        val today = now.toLocalDate()
        return when {
            date.isBefore(today) -> true
            date == today -> draft.time?.isBefore(now.toLocalTime()) ?: false
            else -> false
        }
    }
}

/** How a day is named on a chip: "Bugün, 11 Eki" / "Yarın, 12 Eki" / "Salı, 20 Eki". */
sealed interface DayLabel {
    val date: LocalDate

    data class Today(override val date: LocalDate) : DayLabel
    data class Tomorrow(override val date: LocalDate) : DayLabel
    data class Other(override val date: LocalDate) : DayLabel
}

fun dayLabel(date: LocalDate, today: LocalDate): DayLabel = when (date) {
    today -> DayLabel.Today(date)
    today.plusDays(1) -> DayLabel.Tomorrow(date)
    else -> DayLabel.Other(date)
}
