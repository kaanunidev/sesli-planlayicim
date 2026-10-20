package com.sesliplanlayici.app.core.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** A saved task (docs/DATA_MODEL.md). */
data class Task(
    val id: Long = 0,
    val title: String,
    val date: LocalDate,
    /** `null` = all-day task. */
    val time: LocalTime?,
    /** Filled by the AI; not shown in the UI (DECISIONS #16). */
    val durationMinutes: Int?,
    val reminderText: String?,
    val status: TaskStatus,
    /** How many times the task was moved to the next day. */
    val carriedOverCount: Int,
    val source: TaskSource,
    val createdAt: Instant,
    val completedAt: Instant?,
)

/** PLANNED → IN_PROGRESS → DONE / NOT_DONE. "Yarına taşı" is a date change, not a status. */
enum class TaskStatus { PLANNED, IN_PROGRESS, DONE, NOT_DONE }

interface TaskRepository {
    /** Saves all tasks in one transaction; either all are stored or none. */
    suspend fun addAll(tasks: List<Task>): Result<Unit>
}
