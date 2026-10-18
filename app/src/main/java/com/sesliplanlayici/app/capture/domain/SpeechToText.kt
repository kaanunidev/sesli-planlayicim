package com.sesliplanlayici.app.capture.domain

import kotlinx.coroutines.flow.Flow

/** Speech recognition, hidden behind an interface so the ViewModel does not touch Android APIs. */
interface SpeechToText {
    /**
     * Runs one recognition session. Emits partial text while the user speaks and completes after
     * [SpeechEvent.Final] or [SpeechEvent.Failed]. Cancelling the collection stops listening.
     */
    fun listen(): Flow<SpeechEvent>
}

sealed interface SpeechEvent {
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Failed(val error: SpeechError) : SpeechEvent
}

enum class SpeechError {
    /** Silence or nothing recognizable; the session simply ended. */
    NoSpeech,

    /** The recognition service dropped the session (seen after several silent sessions in a row). */
    Interrupted,
    Network,
    LanguageUnavailable,
    NotAvailable,
    Busy,
    NoPermission,
    Other;

    /** Errors after which listening continues with a new session. */
    val isRecoverable: Boolean get() = this == NoSpeech || this == Interrupted
}

/** Joins recognized segments into one transcript; blank segments are skipped. */
fun appendSegment(transcript: String, segment: String): String {
    val cleaned = segment.trim()
    return when {
        cleaned.isEmpty() -> transcript
        transcript.isEmpty() -> cleaned
        else -> "$transcript $cleaned"
    }
}
