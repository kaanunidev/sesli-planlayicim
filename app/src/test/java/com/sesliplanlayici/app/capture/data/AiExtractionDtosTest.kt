package com.sesliplanlayici.app.capture.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiExtractionDtosTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesContractExample() {
        val body = """
            {
              "input_kind": "plan",
              "tasks": [
                {"title": "Maç izle", "date": "2026-10-12", "time": "10:00", "duration_minutes": 120,
                 "reminder_text": "Seninle konuştuğumuz maç birazdan başlıyor, unutma!", "missing": []},
                {"title": "Annemi ara", "date": "2026-10-12", "time": null, "duration_minutes": null,
                 "reminder_text": "Annemi aramayı unutma", "missing": ["time"]}
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<AiExtractionResponseDto>(body)

        assertEquals("plan", response.inputKind)
        assertEquals(2, response.tasks.size)
        assertEquals(120, response.tasks[0].durationMinutes)
        assertNull(response.tasks[1].time)
        assertEquals(listOf("time"), response.tasks[1].missing)
    }

    @Test
    fun missingFieldsFallBackToDefaults() {
        val response = json.decodeFromString<AiExtractionResponseDto>("""{"tasks":[{}]}""")

        assertNull(response.inputKind)
        assertEquals(AiTaskDto(), response.tasks.single())
    }

    @Test
    fun requestUsesContractFieldNames() {
        val request = AiExtractionRequestDto(
            transcript = "Yarın sabah onda maç var",
            now = "2026-10-11T21:40:00+03:00",
            timezone = "Europe/Istanbul",
            locale = "tr-TR",
        )

        assertEquals(
            """{"transcript":"Yarın sabah onda maç var","now":"2026-10-11T21:40:00+03:00","timezone":"Europe/Istanbul","locale":"tr-TR"}""",
            json.encodeToString(AiExtractionRequestDto.serializer(), request),
        )
    }
}
