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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CapturePhase { Idle, Listening, Finished }

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
 * The transcript lives only in memory (AGENTS.md rule 3).
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
        listeningJob = viewModelScope.launch { listenUntilStopped() }
    }

    /** "Bitir": keep whatever was heard, including the unfinished sentence. */
    fun finish() {
        listeningJob?.cancel()
        _uiState.update {
            it.copy(phase = CapturePhase.Finished, committedText = it.transcript, partialText = "")
        }
    }

    /** "İptal" / "Tekrar konuş": drop the transcript. */
    fun reset() {
        listeningJob?.cancel()
        _uiState.value = CaptureUiState()
    }

    private suspend fun listenUntilStopped() {
        while (true) {
            var keepListening = false
            speechToText.listen().collect { event ->
                when (event) {
                    is SpeechEvent.Partial -> _uiState.update { it.copy(partialText = event.text) }
                    is SpeechEvent.Final -> {
                        _uiState.update {
                            it.copy(committedText = appendSegment(it.committedText, event.text), partialText = "")
                        }
                        keepListening = true
                    }
                    is SpeechEvent.Failed -> {
                        if (event.error.isRecoverable) {
                            keepListening = true
                        } else {
                            stopWithError(event.error)
                        }
                    }
                }
            }
            if (!keepListening) return
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
}
