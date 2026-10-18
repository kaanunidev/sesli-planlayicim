package com.sesliplanlayici.app.capture.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.sesliplanlayici.app.capture.domain.SpeechError
import com.sesliplanlayici.app.capture.domain.SpeechEvent
import com.sesliplanlayici.app.capture.domain.SpeechToText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/** [SpeechToText] on top of the platform [SpeechRecognizer]; audio is never stored. */
class AndroidSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechToText {

    override fun listen(): Flow<SpeechEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechEvent.Failed(SpeechError.NotAvailable))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.bestText()?.let { trySend(SpeechEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                trySend(SpeechEvent.Final(results.bestText().orEmpty()))
                close()
            }

            override fun onError(error: Int) {
                // Only the numeric code is logged, never the transcript.
                Log.d(TAG, "Recognizer error code=$error mapped=${speechErrorFromCode(error)}")
                trySend(SpeechEvent.Failed(speechErrorFromCode(error)))
                close()
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(recognizerIntent())

        awaitClose {
            recognizer.cancel()
            recognizer.destroy()
        }
    }
        // SpeechRecognizer must be used from the main thread.
        .flowOn(Dispatchers.Main)

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE_TAG)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

    private fun Bundle?.bestText(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private companion object {
        const val TAG = "SpeechToText"
        const val LANGUAGE_TAG = "tr-TR"
    }
}

internal fun speechErrorFromCode(code: Int): SpeechError = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    -> SpeechError.NoSpeech

    // Google's service sends this after ~7 silent sessions in a row even when online.
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SpeechError.Interrupted

    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER,
    -> SpeechError.Network

    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
    -> SpeechError.LanguageUnavailable

    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
    -> SpeechError.Busy

    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.NoPermission

    else -> SpeechError.Other
}
