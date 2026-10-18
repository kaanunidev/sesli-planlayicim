package com.sesliplanlayici.app.capture.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesliplanlayici.app.capture.domain.ExtractionFailure
import com.sesliplanlayici.app.capture.domain.ExtractionOutcome
import com.sesliplanlayici.app.capture.domain.ExtractionResult
import com.sesliplanlayici.app.capture.domain.SpeechError
import com.sesliplanlayici.app.capture.domain.SpeechEvent
import com.sesliplanlayici.app.capture.domain.SpeechToText
import com.sesliplanlayici.app.capture.domain.TaskExtractor
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

    /** Nothing was heard; the user is asked to check the microphone. */
    NothingHeard,

    /** The transcript is with the AI proxy. */
    Extracting,

    /** The proxy answered ([CaptureUiState.result]) or failed ([CaptureUiState.failure]). */
    Result,
}

data class CaptureUiState(
    val phase: CapturePhase = CapturePhase.Idle,
    /** Text from finished recognition sessions. */
    val committedText: String = "",
    /** Text of the session still in progress; may still change. */
    val partialText: String = "",
    /** Why listening stopped early, shown on the start screen. */
    val error: SpeechError? = null,
    val result: ExtractionResult? = null,
    val failure: ExtractionFailure? = null,
) {
    val transcript: String get() = appendSegment(committedText, partialText)
}

/**
 * Listens until the user taps "Bitir", then sends the transcript to the AI proxy.
 *
 * The platform recognizer stops after a short silence, so a new session starts whenever one ends
 * without a real error. If nothing at all is heard for [NOTHING_HEARD_TIMEOUT_MS], listening stops
 * and the user is asked to check the microphone. The transcript lives only in memory (AGENTS.md rule 3).
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val speechToText: SpeechToText,
    private val taskExtractor: TaskExtractor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private var listeningJob: Job? = null
    private var extractionJob: Job? = null

    fun startListening() {
        if (listeningJob?.isActive == true) return
        extractionJob?.cancel()
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

    /** "Bitir": keep whatever was heard, including the unfinished sentence, and send it. */
    fun finish() {
        listeningJob?.cancel()
        val transcript = _uiState.value.transcript
        if (transcript.isEmpty()) {
            _uiState.value = CaptureUiState(phase = CapturePhase.NothingHeard)
            return
        }
        _uiState.update { it.copy(committedText = transcript, partialText = "") }
        extract()
    }

    /** "Tekrar dene" after a failed request: same transcript, no need to speak again. */
    fun retryExtraction() {
        if (_uiState.value.committedText.isNotEmpty()) extract()
    }

    /** "İptal" / "Vazgeç" / "Tekrar konuş": drop the transcript. */
    fun reset() {
        listeningJob?.cancel()
        extractionJob?.cancel()
        _uiState.value = CaptureUiState()
    }

    private fun extract() {
        val transcript = _uiState.value.committedText
        _uiState.update { it.copy(phase = CapturePhase.Extracting, result = null, failure = null) }
        extractionJob?.cancel()
        extractionJob = viewModelScope.launch {
            val outcome = taskExtractor.extract(transcript)
            _uiState.update {
                when (outcome) {
                    is ExtractionOutcome.Success -> it.copy(phase = CapturePhase.Result, result = outcome.result)
                    is ExtractionOutcome.Failure -> it.copy(phase = CapturePhase.Result, failure = outcome.reason)
                }
            }
        }
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
        val transcript = _uiState.value.transcript
        if (transcript.isEmpty()) {
            _uiState.value = CaptureUiState(phase = CapturePhase.Idle, error = error)
        } else {
            // Something was heard before the error: use it rather than throwing it away.
            _uiState.update { it.copy(committedText = transcript, partialText = "") }
            extract()
        }
    }

    internal companion object {
        const val NOTHING_HEARD_TIMEOUT_MS = 10_000L
        const val MAX_INTERRUPTIONS = 3
        const val INTERRUPTION_BACKOFF_MS = 500L
    }
}
