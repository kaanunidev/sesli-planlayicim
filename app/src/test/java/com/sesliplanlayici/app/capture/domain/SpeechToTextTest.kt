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
    fun onlySilenceKeepsListening() {
        assertTrue(SpeechError.NoSpeech.isRecoverable)
        SpeechError.entries.filter { it != SpeechError.NoSpeech }.forEach { assertFalse(it.name, it.isRecoverable) }
    }
}
