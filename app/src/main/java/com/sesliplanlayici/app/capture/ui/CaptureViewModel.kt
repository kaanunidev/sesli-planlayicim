package com.sesliplanlayici.app.capture.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesliplanlayici.app.capture.domain.SpeechError
import com.sesliplanlayici.app.capture.domain.SpeechEvent
import com.sesliplanlayici.app.capture.domain.SpeechToText
import com.sesliplanlayici.app.capture.domain.appendSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CapturePhase {
    Idle,
    Listening,

    /** Nothing was heard for a while; the user is asked to check the microphone. */
    NothingHeard,
    Finished,
}

data class CaptureUiState(
    val phase: CapturePhase = CapturePhase.Idle,
    /** Text from finished recognition sessions. */
    val committedText: String = "",
    /** Text of the session still in progress; may still change. */
    val partialText: String = "",
    val error: SpeechError? = null,
) {
    val transcript: String get() = appendSegment(committedText, partialText)
}

/**
 * Keeps listening until the user taps "Bitir": the platform recognizer stops after a short
 * silence, so a new session starts whenever one ends without a real error.
 * If nothing at all is heard for [NOTHING_HEARD_TIMEOUT_MS], listening stops and the user is asked
 * to check the microphone. The transcript lives only in memory (AGENTS.md rule 3).
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val speechToText: SpeechToText,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private var listeningJob: Job? = null

    fun startListening() {
        if (listeningJob?.isActive == true) return
        _uiState.value = CaptureUiState(phase = CapturePhase.Listening)
        listeningJob = viewModelScope.launch session@{
            val watchdog = launch {
                delay(NOTHING_HEARD_TIMEOUT_MS)
                if (_uiState.value.transcript.isEmpty()) {
                    _uiState.value = CaptureUiState(phase = CapturePhase.NothingHeard)
                    this@session.cancel()
                }
            }
            listenUntilStopped()
            watchdog.cancel()
        }
    }

    /** "Bitir": keep whatever was heard, including the unfinished sentence. */
    fun finish() {
        listeningJob?.cancel()
        _uiState.update {
            it.copy(phase = CapturePhase.Finished, committedText = it.transcript, partialText = "")
        }
    }

    /** "İptal" / "Vazgeç" / "Tekrar konuş": drop the transcript. */
    fun reset() {
        listeningJob?.cancel()
        _uiState.value = CaptureUiState()
    }

    private suspend fun listenUntilStopped() {
        var interruptionsInARow = 0
        while (true) {
            var keepListening = false
            speechToText.listen().collect { event ->
                when (event) {
                    is SpeechEvent.Partial -> {
                        interruptionsInARow = 0
                        _uiState.update { it.copy(partialText = event.text) }
                    }
                    is SpeechEvent.Final -> {
                        interruptionsInARow = 0
                        _uiState.update {
                            it.copy(committedText = appendSegment(it.committedText, event.text), partialText = "")
                        }
                        keepListening = true
                    }
                    is SpeechEvent.Failed -> when {
                        event.error == SpeechError.Interrupted && ++interruptionsInARow > MAX_INTERRUPTIONS ->
                            // Dropped again and again: most likely a real connection problem.
                            stopWithError(SpeechError.Network)
                        event.error.isRecoverable -> keepListening = true
                        else -> stopWithError(event.error)
                    }
                }
            }
            if (!keepListening) return
            if (interruptionsInARow > 0) delay(INTERRUPTION_BACKOFF_MS)
        }
    }

    private fun stopWithError(error: SpeechError) {
        _uiState.update {
            val transcript = it.transcript
            // Keep what was heard so far; with nothing heard, go back to the start screen.
            val phase = if (transcript.isEmpty()) CapturePhase.Idle else CapturePhase.Finished
            it.copy(phase = phase, committedText = transcript, partialText = "", error = error)
        }
    }

    private companion object {
        const val NOTHING_HEARD_TIMEOUT_MS = 10_000L
        const val MAX_INTERRUPTIONS = 3
        const val INTERRUPTION_BACKOFF_MS = 500L
    }
}
