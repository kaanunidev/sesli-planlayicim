package com.sesliplanlayici.app.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sesliplanlayici.app.core.domain.Task
import com.sesliplanlayici.app.core.domain.TaskSource
import com.sesliplanlayici.app.core.domain.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// Indexed by date: the Today screen and reminders always query one day.
@Entity(tableName = "tasks", indices = [Index("date")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int?,
    @ColumnInfo(name = "reminder_text") val reminderText: String?,
    val status: TaskStatus,
    @ColumnInfo(name = "carried_over_count") val carriedOverCount: Int,
    val source: TaskSource,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "completed_at") val completedAt: Instant?,
)

fun Task.toEntity() = TaskEntity(
    id = id,
    title = title,
    date = date,
    time = time,
    durationMinutes = durationMinutes,
    reminderText = reminderText,
    status = status,
    carriedOverCount = carriedOverCount,
    source = source,
    createdAt = createdAt,
    completedAt = completedAt,
)

fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    date = date,
    time = time,
    durationMinutes = durationMinutes,
    reminderText = reminderText,
    status = status,
    carriedOverCount = carriedOverCount,
    source = source,
    createdAt = createdAt,
    completedAt = completedAt,
)
