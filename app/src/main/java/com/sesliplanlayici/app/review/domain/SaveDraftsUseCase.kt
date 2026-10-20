package com.sesliplanlayici.app.review.domain

import com.sesliplanlayici.app.core.domain.Task
import com.sesliplanlayici.app.core.domain.TaskRepository
import com.sesliplanlayici.app.core.domain.TaskStatus
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

sealed interface SaveDraftsResult {
    data class Saved(val count: Int) : SaveDraftsResult

    /** A card has no day; nothing was saved. The screen prevents this, the use case enforces it. */
    data object MissingDate : SaveDraftsResult

    /** Storage failed; nothing was saved. */
    data object Failed : SaveDraftsResult
}

/** Turns the reviewed cards into tasks and stores them all at once. */
class SaveDraftsUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(items: List<ReviewItem>): SaveDraftsResult {
        if (!ReviewRules.canSaveAll(items)) return SaveDraftsResult.MissingDate

        val createdAt = Instant.now(clock)
        val tasks = items.map { item ->
            val draft = item.draft
            Task(
                title = draft.title,
                // canSaveAll guarantees a day on every card.
                date = draft.date ?: return SaveDraftsResult.MissingDate,
                time = draft.time,
                durationMinutes = draft.durationMinutes,
                reminderText = draft.reminderText,
                status = TaskStatus.PLANNED,
                carriedOverCount = 0,
                source = item.source,
                createdAt = createdAt,
                completedAt = null,
            )
        }
        return repository.addAll(tasks).fold(
            onSuccess = { SaveDraftsResult.Saved(tasks.size) },
            onFailure = { SaveDraftsResult.Failed },
        )
    }
}
