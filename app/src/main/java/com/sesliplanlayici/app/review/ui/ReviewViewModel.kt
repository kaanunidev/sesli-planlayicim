package com.sesliplanlayici.app.review.ui

import androidx.lifecycle.ViewModel
import com.sesliplanlayici.app.capture.domain.TaskDraft
import com.sesliplanlayici.app.core.domain.TaskSource
import com.sesliplanlayici.app.review.data.PendingDrafts
import com.sesliplanlayici.app.review.domain.ReviewItem
import com.sesliplanlayici.app.review.domain.ReviewRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ReviewUiState(
    val items: List<ReviewItem> = emptyList(),
    /** Card whose edit dialog is open. */
    val editingId: Long? = null,
    /** The card being edited was just added and disappears if the dialog is cancelled. */
    val editingIsNew: Boolean = false,
    /** Set once the user changes anything, to confirm before throwing the edits away. */
    val hasChanges: Boolean = false,
    val now: LocalDateTime,
) {
    val today: LocalDate get() = now.toLocalDate()
    val canSave: Boolean get() = ReviewRules.canSaveAll(items)
    val fromVoice: Boolean get() = items.any { it.source == TaskSource.VOICE }
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    pendingDrafts: PendingDrafts,
    clock: Clock,
) : ViewModel() {

    private var nextId = 0L

    private val _uiState: MutableStateFlow<ReviewUiState>
    val uiState: StateFlow<ReviewUiState>

    init {
        val drafts = pendingDrafts.take()
        val now = LocalDateTime.now(clock)
        val initial = if (drafts.isEmpty()) {
            // "Elle ekle": start with one empty card and its edit dialog open.
            val item = newManualItem()
            ReviewUiState(items = listOf(item), editingId = item.id, editingIsNew = true, now = now)
        } else {
            ReviewUiState(items = drafts.map { ReviewItem(nextId++, it, TaskSource.VOICE) }, now = now)
        }
        _uiState = MutableStateFlow(initial)
        uiState = _uiState.asStateFlow()
    }

    fun setDate(id: Long, date: LocalDate) = updateDraft(id) { it.copy(date = date) }

    /** `null` clears the time: the task becomes an all-day task. */
    fun setTime(id: Long, time: LocalTime?) = updateDraft(id) { it.copy(time = time) }

    fun startEditing(id: Long) = _uiState.update { it.copy(editingId = id, editingIsNew = false) }

    /** Cancel: a brand-new card that was never filled in is removed again. */
    fun cancelEditing() = _uiState.update { state ->
        val items = if (state.editingIsNew) state.items.filterNot { it.id == state.editingId } else state.items
        state.copy(items = items, editingId = null, editingIsNew = false)
    }

    /**
     * Applies the edit dialog: title, day and time together. Ignored when the title is blank; the
     * dialog keeps its save button disabled in that case.
     */
    fun saveEdit(id: Long, title: String, date: LocalDate?, time: LocalTime?) {
        val cleanTitle = title.trim().take(ReviewRules.MAX_TITLE_LENGTH)
        if (cleanTitle.isEmpty()) return
        updateDraft(id) { it.copy(title = cleanTitle, date = date, time = time) }
        _uiState.update { it.copy(editingId = null, editingIsNew = false) }
    }

    fun delete(id: Long) = _uiState.update { state ->
        state.copy(
            items = state.items.filterNot { it.id == id },
            editingId = null,
            editingIsNew = false,
            hasChanges = true,
        )
    }

    fun addManual() {
        val item = newManualItem()
        _uiState.update { it.copy(items = it.items + item, editingId = item.id, editingIsNew = true, hasChanges = true) }
    }

    private fun newManualItem() = ReviewItem(
        id = nextId++,
        draft = TaskDraft(title = "", date = null, time = null, durationMinutes = null, reminderText = null),
        source = TaskSource.MANUAL,
    )

    private fun updateDraft(id: Long, change: (TaskDraft) -> TaskDraft) = _uiState.update { state ->
        state.copy(
            items = state.items.map { if (it.id == id) it.copy(draft = change(it.draft)) else it },
            hasChanges = true,
        )
    }
}
