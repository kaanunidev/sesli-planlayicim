package com.sesliplanlayici.app.capture.data

import com.sesliplanlayici.app.capture.domain.ExtractionResult
import com.sesliplanlayici.app.capture.domain.TaskDraft
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.serialization.json.Json

/** Outcome of reading the proxy's response body. */
sealed interface AiResponseParse {
    data class Valid(val result: ExtractionResult) : AiResponseParse

    /** The body is not the JSON shape we expect; the UI offers manual entry instead. */
    data object Malformed : AiResponseParse
}

/**
 * Turns the AI's JSON into drafts without trusting it: see docs/AI_CONTRACT.md → "Doğrulama".
 * Never throws; anything unusable becomes [AiResponseParse.Malformed] or is dropped/cleared.
 */
class AiResponseParser @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        // An explicit null for a field with a default ("tasks": null) falls back to the default.
        coerceInputValues = true
    }

    fun parse(body: String): AiResponseParse {
        val response = try {
            json.decodeFromString<AiExtractionResponseDto>(body)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            return AiResponseParse.Malformed
        }

        val drafts = response.tasks.mapNotNull(::toDraft)
        val result = when (response.inputKind) {
            INPUT_KIND_OFF_TOPIC -> ExtractionResult.OffTopic
            INPUT_KIND_EMPTY -> ExtractionResult.NothingPlanned
            // "plan", missing or unknown: decide by what is left after validation.
            else -> if (drafts.isEmpty()) ExtractionResult.NothingPlanned else ExtractionResult.Plan(drafts)
        }
        return AiResponseParse.Valid(result)
    }

    private fun toDraft(dto: AiTaskDto): TaskDraft? {
        val title = dto.title?.trim().orEmpty()
        if (title.isEmpty()) return null

        return TaskDraft(
            title = title.take(MAX_TITLE_LENGTH),
            date = dto.date?.let(::parseDate),
            time = dto.time?.let(::parseTime),
            durationMinutes = dto.durationMinutes?.takeIf { it > 0 },
            reminderText = dto.reminderText?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_REMINDER_LENGTH),
        )
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value.trim())
    } catch (e: DateTimeParseException) {
        null
    }

    private fun parseTime(value: String): LocalTime? = try {
        LocalTime.parse(value.trim(), TIME_FORMAT)
    } catch (e: DateTimeParseException) {
        null
    }

    private companion object {
        const val INPUT_KIND_EMPTY = "empty"
        const val INPUT_KIND_OFF_TOPIC = "off_topic"
        const val MAX_TITLE_LENGTH = 60
        const val MAX_REMINDER_LENGTH = 120

        // Contract says "HH:mm"; a missing leading zero ("9:00") is accepted too.
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
    }
}
