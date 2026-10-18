package com.sesliplanlayici.app.capture.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire format of the AI proxy, see docs/AI_CONTRACT.md.

@Serializable
data class AiExtractionRequestDto(
    val transcript: String,
    // ISO-8601 with offset, e.g. "2026-10-11T21:40:00+03:00".
    val now: String,
    val timezone: String,
    val locale: String,
)

// Every field is optional: the response comes from an LLM and is validated before use.
@Serializable
data class AiExtractionResponseDto(
    // Kept as a string so unknown values do not fail decoding.
    @SerialName("input_kind") val inputKind: String? = null,
    val tasks: List<AiTaskDto> = emptyList(),
)

@Serializable
data class AiTaskDto(
    val title: String? = null,
    val date: String? = null,
    val time: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    @SerialName("reminder_text") val reminderText: String? = null,
    val missing: List<String> = emptyList(),
)
