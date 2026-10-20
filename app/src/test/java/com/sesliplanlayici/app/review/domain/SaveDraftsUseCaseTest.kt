package com.sesliplanlayici.app.review.domain

import com.sesliplanlayici.app.capture.domain.TaskDraft
import com.sesliplanlayici.app.core.domain.Task
import com.sesliplanlayici.app.core.domain.TaskRepository
import com.sesliplanlayici.app.core.domain.TaskSource
import com.sesliplanlayici.app.core.domain.TaskStatus
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveDraftsUseCaseTest {

    private val instant = Instant.parse("2026-10-11T18:40:00Z")
    private val clock = Clock.fixed(instant, ZoneId.of("Europe/Istanbul"))
    private val tomorrow = LocalDate.of(2026, 10, 12)

    private class FakeRepository(private val fail: Boolean = false) : TaskRepository {
        val saved = mutableListOf<List<Task>>()

        override suspend fun addAll(tasks: List<Task>): Result<Unit> {
            if (fail) return Result.failure(IOException("disk full"))
            saved += tasks
            return Result.success(Unit)
        }
    }

    private fun item(draft: TaskDraft, source: TaskSource = TaskSource.VOICE) = ReviewItem(0, draft, source)

    @Test
    fun draftsBecomePlannedTasks() = runTest {
        val repository = FakeRepository()
        val useCase = SaveDraftsUseCase(repository, clock)

        val result = useCase(
            listOf(
                item(TaskDraft("Maç izle", tomorrow, LocalTime.of(10, 0), 120, "Maç saati geldi")),
                item(TaskDraft("Fatura öde", tomorrow, null, null, null), TaskSource.MANUAL),
            ),
        )

        assertEquals(SaveDraftsResult.Saved(2), result)
        assertEquals(
            Task(
                title = "Maç izle",
                date = tomorrow,
                time = LocalTime.of(10, 0),
                durationMinutes = 120,
                reminderText = "Maç saati geldi",
                status = TaskStatus.PLANNED,
                carriedOverCount = 0,
                source = TaskSource.VOICE,
                createdAt = instant,
                completedAt = null,
            ),
            repository.saved.single()[0],
        )
        val manual = repository.saved.single()[1]
        assertEquals(TaskSource.MANUAL, manual.source)
        assertEquals(null, manual.time)
    }

    @Test
    fun aCardWithoutDaySavesNothing() = runTest {
        val repository = FakeRepository()

        val result = SaveDraftsUseCase(repository, clock)(
            listOf(item(TaskDraft("Maç izle", tomorrow, null, null, null)), item(TaskDraft("Dişçi", null, null, null, null))),
        )

        assertEquals(SaveDraftsResult.MissingDate, result)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun emptyListSavesNothing() = runTest {
        assertEquals(SaveDraftsResult.MissingDate, SaveDraftsUseCase(FakeRepository(), clock)(emptyList()))
    }

    @Test
    fun storageFailureIsReported() = runTest {
        val result = SaveDraftsUseCase(FakeRepository(fail = true), clock)(
            listOf(item(TaskDraft("Maç izle", tomorrow, null, null, null))),
        )

        assertEquals(SaveDraftsResult.Failed, result)
    }
}
