package com.sesliplanlayici.app.review.data

import com.sesliplanlayici.app.capture.domain.TaskDraft
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands drafts from the capture screen to the review screen. In memory only: nothing is
 * persisted before the user saves. Used on the main thread.
 */
@Singleton
class PendingDrafts @Inject constructor() {
    private var drafts: List<TaskDraft> = emptyList()

    fun put(drafts: List<TaskDraft>) {
        this.drafts = drafts
    }

    /** Returns the drafts once and forgets them. */
    fun take(): List<TaskDraft> = drafts.also { drafts = emptyList() }
}
