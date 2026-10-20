package com.sesliplanlayici.app.core.data.db

import androidx.room.TypeConverter
import com.sesliplanlayici.app.core.domain.TaskSource
import com.sesliplanlayici.app.core.domain.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * java.time ↔ SQLite. Dates as epoch days and times as seconds of day keep ordering and range
 * queries numeric; enums are stored by name so reordering them never corrupts data.
 */
class Converters {
    @TypeConverter fun fromLocalDate(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun toLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun fromLocalTime(value: LocalTime?): Int? = value?.toSecondOfDay()
    @TypeConverter fun toLocalTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter fun fromStatus(value: TaskStatus): String = value.name
    @TypeConverter fun toStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter fun fromSource(value: TaskSource): String = value.name
    @TypeConverter fun toSource(value: String): TaskSource = TaskSource.valueOf(value)
}
