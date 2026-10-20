package com.sesliplanlayici.app.review.ui

import com.sesliplanlayici.app.capture.domain.TaskDraft
import com.sesliplanlayici.app.core.domain.Task
import com.sesliplanlayici.app.core.domain.TaskRepository
import com.sesliplanlayici.app.core.domain.TaskSource
import com.sesliplanlayici.app.review.data.PendingDrafts
import com.sesliplanlayici.app.review.domain.SaveDraftsUseCase
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    // 2026-10-11 21:40 in Istanbul.
    private val clock = Clock.fixed(Instant.parse("2026-10-11T18:40:00Z"), ZoneId.of("Europe/Istanbul"))
    private val tomorrow = LocalDate.of(2026, 10, 12)

    private val match = TaskDraft("Maç izle", tomorrow, LocalTime.of(10, 0), 120, null)
    private val callMom = TaskDraft("Annemi ara", null, null, null, null)

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository(var fail: Boolean = false) : TaskRepository {
        val saved = mutableListOf<List<Task>>()

        override suspend fun addAll(tasks: List<Task>): Result<Unit> {
            if (fail) return Result.failure(IOException("disk full"))
            saved += tasks
            return Result.success(Unit)
        }
    }

    private val repository = FakeRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(vararg drafts: TaskDraft): ReviewViewModel {
        val pending = PendingDrafts().apply { put(drafts.toList()) }
        return ReviewViewModel(pending, clock, SaveDraftsUseCase(repository, clock))
    }

    private val ReviewViewModel.state get() = uiState.value

    @Test
    fun saveAllStoresTheCardsAndReportsTheCount() = runTest(dispatcher) {
        val vm = viewModel(match, callMom)
        vm.setDate(vm.state.items[1].id, tomorrow)

        vm.saveAll()
        advanceUntilIdle()

        assertEquals(ReviewEvent.Saved(2), vm.events.first())
        assertEquals(listOf("Maç izle", "Annemi ara"), repository.saved.single().map { it.title })
    }

    @Test
    fun saveAllDoesNothingWhileADayIsMissing() = runTest(dispatcher) {
        val vm = viewModel(match, callMom)

        vm.saveAll()
        advanceUntilIdle()

        assertTrue(repository.saved.isEmpty())
        assertFalse(vm.state.isSaving)
    }

    @Test
    fun doubleTapSavesOnce() = runTest(dispatcher) {
        val vm = viewModel(match)

        vm.saveAll()
        vm.saveAll()
        advanceUntilIdle()

        assertEquals(1, repository.saved.size)
    }

    @Test
    fun storageFailureKeepsTheCardsForAnotherTry() = runTest(dispatcher) {
        repository.fail = true
        val vm = viewModel(match)

        vm.saveAll()
        advanceUntilIdle()

        assertTrue(vm.state.saveFailed)
        assertFalse(vm.state.isSaving)
        assertEquals(listOf(match), vm.state.items.map { it.draft })

        repository.fail = false
        vm.saveAll()
        advanceUntilIdle()
        assertEquals(1, repository.saved.size)
        assertFalse(vm.state.saveFailed)
    }

    @Test
    fun draftsBecomeVoiceCardsAndArePickedUpOnce() {
        val pending = PendingDrafts().apply { put(listOf(match, callMom)) }
        val vm = ReviewViewModel(pending, clock, SaveDraftsUseCase(repository, clock))

        assertEquals(listOf(match, callMom), vm.state.items.map { it.draft })
        assertTrue(vm.state.items.all { it.source == TaskSource.VOICE })
        assertEquals(emptyList<TaskDraft>(), pending.take())
        assertFalse(vm.state.hasChanges)
    }

    @Test
    fun pickingTheMissingDayAllowsSaving() {
        val vm = viewModel(match, callMom)
        assertFalse(vm.state.canSave)

        vm.setDate(vm.state.items[1].id, tomorrow)

        assertTrue(vm.state.canSave)
        assertTrue(vm.state.hasChanges)
        assertEquals(tomorrow, vm.state.items[1].draft.date)
    }

    @Test
    fun timeCanBeSetAndCleared() {
        val vm = viewModel(match)
        val id = vm.state.items.single().id

        vm.setTime(id, LocalTime.of(11, 30))
        assertEquals(LocalTime.of(11, 30), vm.state.items.single().draft.time)

        vm.setTime(id, null)
        assertNull(vm.state.items.single().draft.time)
    }

    @Test
    fun editTrimsAndCapsTheTitle() {
        val vm = viewModel(match)
        val id = vm.state.items.single().id
        vm.startEditing(id)

        vm.saveEdit(id, "  " + "a".repeat(80) + "  ", tomorrow, LocalTime.of(10, 0))

        val draft = vm.state.items.single().draft
        assertEquals(60, draft.title.length)
        assertNull(vm.state.editingId)
    }

    @Test
    fun editChangesTitleDayAndTimeTogether() {
        val vm = viewModel(callMom)
        val id = vm.state.items.single().id
        vm.startEditing(id)

        vm.saveEdit(id, "Annemi ara", tomorrow, LocalTime.of(19, 0))

        val draft = vm.state.items.single().draft
        assertEquals(tomorrow, draft.date)
        assertEquals(LocalTime.of(19, 0), draft.time)
        assertTrue(vm.state.canSave)
    }

    @Test
    fun editCanClearTheTime() {
        val vm = viewModel(match)
        val id = vm.state.items.single().id

        vm.saveEdit(id, "Maç izle", tomorrow, null)

        assertNull(vm.state.items.single().draft.time)
    }

    @Test
    fun blankTitleIsNotSaved() {
        val vm = viewModel(match)
        val id = vm.state.items.single().id
        vm.startEditing(id)

        vm.saveEdit(id, "   ", null, null)

        // Nothing changes, not even the day and time passed along with the blank title.
        assertEquals(match, vm.state.items.single().draft)
        assertEquals(id, vm.state.editingId)
    }

    @Test
    fun deleteRemovesTheCard() {
        val vm = viewModel(match, callMom)

        vm.delete(vm.state.items[0].id)

        assertEquals(listOf("Annemi ara"), vm.state.items.map { it.draft.title })
        assertTrue(vm.state.hasChanges)
    }

    @Test
    fun manualCardOpensForEditingAndDisappearsWhenCancelled() {
        val vm = viewModel(match)

        vm.addManual()
        val added = vm.state.items.last()
        assertEquals(TaskSource.MANUAL, added.source)
        assertEquals(added.id, vm.state.editingId)
        assertTrue(vm.state.editingIsNew)

        vm.cancelEditing()

        assertEquals(listOf("Maç izle"), vm.state.items.map { it.draft.title })
        assertNull(vm.state.editingId)
    }

    @Test
    fun manualCardStaysOnceSaved() {
        val vm = viewModel(match)
        vm.addManual()
        val id = vm.state.items.last().id

        vm.saveEdit(id, "Fatura öde", tomorrow, null)
        vm.startEditing(id)
        vm.cancelEditing()

        assertEquals(listOf("Maç izle", "Fatura öde"), vm.state.items.map { it.draft.title })
    }

    @Test
    fun noDraftsMeansManualEntry() {
        val vm = viewModel()

        val item = vm.state.items.single()
        assertEquals(TaskSource.MANUAL, item.source)
        assertEquals(item.id, vm.state.editingId)
        assertFalse(vm.state.fromVoice)

        vm.cancelEditing()
        assertTrue(vm.state.items.isEmpty())
        assertFalse(vm.state.canSave)
    }
}
