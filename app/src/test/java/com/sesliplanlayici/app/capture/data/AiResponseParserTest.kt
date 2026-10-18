package com.sesliplanlayici.app.capture.data

import com.sesliplanlayici.app.capture.domain.DraftField
import com.sesliplanlayici.app.capture.domain.ExtractionResult
import com.sesliplanlayici.app.capture.domain.TaskDraft
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiResponseParserTest {

    private val parser = AiResponseParser()

    private fun parse(body: String) = parser.parse(body)

    private fun plan(body: String): List<TaskDraft> {
        val parsed = parse(body)
        val result = (parsed as AiResponseParse.Valid).result
        return (result as ExtractionResult.Plan).drafts
    }

    private fun result(body: String): ExtractionResult = (parse(body) as AiResponseParse.Valid).result

    // --- Valid responses

    @Test
    fun contractExampleBecomesThreeDrafts() {
        val drafts = plan(
            """
            {"input_kind":"plan","tasks":[
              {"title":"Maç izle","date":"2026-10-12","time":"10:00","duration_minutes":120,
               "reminder_text":"Seninle konuştuğumuz maç birazdan başlıyor, unutma!","missing":[]},
              {"title":"Market alışverişi","date":"2026-10-12","time":"15:30","duration_minutes":null,
               "reminder_text":"Market zamanı geldi, listeni unutma","missing":[]},
              {"title":"Annemi ara","date":"2026-10-12","time":null,"duration_minutes":null,
               "reminder_text":"Annemi aramayı unutma","missing":["time"]}
            ]}
            """,
        )

        assertEquals(3, drafts.size)
        assertEquals(
            TaskDraft(
                title = "Maç izle",
                date = LocalDate.of(2026, 10, 12),
                time = LocalTime.of(10, 0),
                durationMinutes = 120,
                reminderText = "Seninle konuştuğumuz maç birazdan başlıyor, unutma!",
            ),
            drafts[0],
        )
        assertEquals(LocalTime.of(15, 30), drafts[1].time)
        assertEquals(setOf(DraftField.TIME), drafts[2].missing)
    }

    @Test
    fun noDayGivesDraftWithoutDate() {
        val draft = plan("""{"input_kind":"plan","tasks":[{"title":"Annemi ara","date":null,"time":null}]}""").single()

        assertEquals(setOf(DraftField.DATE, DraftField.TIME), draft.missing)
    }

    @Test
    fun missingListFromAiIsIgnored() {
        // The AI claims nothing is missing, but there is no time.
        val draft = plan("""{"tasks":[{"title":"Annemi ara","date":"2026-10-12","time":null,"missing":[]}]}""").single()

        assertEquals(setOf(DraftField.TIME), draft.missing)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val draft = plan("""{"input_kind":"plan","confidence":0.9,"tasks":[{"title":"Maç izle","emoji":"x"}]}""").single()

        assertEquals("Maç izle", draft.title)
    }

    // --- Malformed responses

    @Test
    fun notJsonIsMalformed() {
        assertEquals(AiResponseParse.Malformed, parse("Elbette! İşte görevlerin:"))
    }

    @Test
    fun emptyBodyIsMalformed() {
        assertEquals(AiResponseParse.Malformed, parse(""))
    }

    @Test
    fun jsonArrayIsMalformed() {
        assertEquals(AiResponseParse.Malformed, parse("""[{"title":"Maç izle"}]"""))
    }

    @Test
    fun wrongFieldTypeIsMalformed() {
        assertEquals(AiResponseParse.Malformed, parse("""{"tasks":"Maç izle"}"""))
    }

    // --- Field validation

    @Test
    fun blankTitleDropsOnlyThatTask() {
        val drafts = plan("""{"tasks":[{"title":"  "},{"title":null},{"title":"Annemi ara"}]}""")

        assertEquals(listOf("Annemi ara"), drafts.map { it.title })
    }

    @Test
    fun invalidDateAndTimeBecomeEmpty() {
        val draft = plan("""{"tasks":[{"title":"Dişçi","date":"2026-13-40","time":"25:99"}]}""").single()

        assertNull(draft.date)
        assertNull(draft.time)
    }

    @Test
    fun timeWithoutLeadingZeroIsAccepted() {
        assertEquals(LocalTime.of(9, 0), plan("""{"tasks":[{"title":"Koşu","time":"9:00"}]}""").single().time)
    }

    @Test
    fun nonPositiveDurationBecomesEmpty() {
        val drafts = plan("""{"tasks":[{"title":"A","duration_minutes":0},{"title":"B","duration_minutes":-30}]}""")

        assertEquals(listOf(null, null), drafts.map { it.durationMinutes })
    }

    @Test
    fun longTitleAndReminderAreTrimmed() {
        val longTitle = "a".repeat(80)
        val longReminder = "b".repeat(200)

        val draft = plan("""{"tasks":[{"title":"  $longTitle  ","reminder_text":"$longReminder"}]}""").single()

        assertEquals(60, draft.title.length)
        assertEquals(120, draft.reminderText?.length)
    }

    @Test
    fun blankReminderBecomesEmpty() {
        assertNull(plan("""{"tasks":[{"title":"Annemi ara","reminder_text":"   "}]}""").single().reminderText)
    }

    // --- input_kind

    @Test
    fun offTopicIgnoresAnyTasks() {
        assertEquals(ExtractionResult.OffTopic, result("""{"input_kind":"off_topic","tasks":[{"title":"Şiir yaz"}]}"""))
    }

    @Test
    fun emptyKindMeansNothingPlanned() {
        assertEquals(ExtractionResult.NothingPlanned, result("""{"input_kind":"empty","tasks":[]}"""))
    }

    @Test
    fun planWithOnlyInvalidTasksMeansNothingPlanned() {
        assertEquals(ExtractionResult.NothingPlanned, result("""{"input_kind":"plan","tasks":[{"title":""}]}"""))
    }

    @Test
    fun unknownKindWithTasksIsPlan() {
        assertEquals(listOf("Maç izle"), plan("""{"input_kind":"maybe","tasks":[{"title":"Maç izle"}]}""").map { it.title })
    }

    @Test
    fun missingKindAndNullTasksIsNothingPlanned() {
        assertEquals(ExtractionResult.NothingPlanned, result("""{"tasks":null}"""))
    }
}
