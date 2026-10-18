package com.sesliplanlayici.app.capture.data

import android.speech.SpeechRecognizer
import com.sesliplanlayici.app.capture.domain.SpeechError
import org.junit.Assert.assertEquals
import org.junit.Test

// SpeechRecognizer error codes are compile-time constants, so no Android runtime is needed.
class SpeechErrorMappingTest {

    @Test
    fun silenceIsNoSpeech() {
        assertEquals(SpeechError.NoSpeech, speechErrorFromCode(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(SpeechError.NoSpeech, speechErrorFromCode(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    }

    @Test
    fun connectionProblemsAreNetwork() {
        assertEquals(SpeechError.Network, speechErrorFromCode(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(SpeechError.Network, speechErrorFromCode(SpeechRecognizer.ERROR_SERVER))
    }

    @Test
    fun missingTurkishIsLanguageUnavailable() {
        assertEquals(SpeechError.LanguageUnavailable, speechErrorFromCode(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
    }

    @Test
    fun permissionErrorIsMapped() {
        assertEquals(SpeechError.NoPermission, speechErrorFromCode(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
    }

    @Test
    fun unknownCodeIsOther() {
        assertEquals(SpeechError.Other, speechErrorFromCode(9999))
    }
}
