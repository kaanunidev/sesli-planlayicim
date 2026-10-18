package com.sesliplanlayici.app.capture.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechToTextTest {

    @Test
    fun firstSegmentStartsTheTranscript() {
        assertEquals("Yarın maç var", appendSegment("", "  Yarın maç var "))
    }

    @Test
    fun segmentsAreJoinedWithOneSpace() {
        assertEquals("Yarın maç var annemi ara", appendSegment("Yarın maç var", "annemi ara"))
    }

    @Test
    fun blankSegmentIsSkipped() {
        assertEquals("Yarın maç var", appendSegment("Yarın maç var", "   "))
    }

    @Test
    fun onlySilenceAndInterruptionKeepListening() {
        val recoverable = setOf(SpeechError.NoSpeech, SpeechError.Interrupted)
        recoverable.forEach { assertTrue(it.name, it.isRecoverable) }
        SpeechError.entries.filter { it !in recoverable }.forEach { assertFalse(it.name, it.isRecoverable) }
    }
}
