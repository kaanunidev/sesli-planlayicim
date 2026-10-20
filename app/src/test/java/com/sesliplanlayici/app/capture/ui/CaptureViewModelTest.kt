package com.sesliplanlayici.app.capture.ui

import com.sesliplanlayici.app.capture.domain.ExtractionFailure
import com.sesliplanlayici.app.capture.domain.ExtractionOutcome
import com.sesliplanlayici.app.capture.domain.ExtractionResult
import com.sesliplanlayici.app.capture.domain.SpeechError
import com.sesliplanlayici.app.capture.domain.SpeechEvent
import com.sesliplanlayici.app.capture.domain.SpeechToText
import com.sesliplanlayici.app.capture.domain.TaskDraft
import com.sesliplanlayici.app.capture.domain.TaskExtractor
import com.sesliplanlayici.app.review.data.PendingDrafts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Each listen() call takes the next session; when they run out, it waits silently. */
    private class FakeSpeech(vararg sessions: List<SpeechEvent>) : SpeechToText {
        private val queue = ArrayDeque(sessions.toList())
        var listenCalls = 0

        override fun listen(): Flow<SpeechEvent> = flow {
            listenCalls++
            val events = queue.removeFirstOrNull() ?: awaitCancellation()
            events.forEach { emit(it) }
        }
    }

    private class FakeExtractor(vararg outcomes: ExtractionOutcome) : TaskExtractor {
        private val queue = ArrayDeque(outcomes.toList())
        val transcripts = mutableListOf<String>()

        override suspend fun extract(transcript: String): ExtractionOutcome {
            transcripts += transcript
            return queue.removeFirst()
        }
    }

    private val plan = ExtractionResult.Plan(listOf(TaskDraft("Maç izle", null, null, null, null)))
    private val pending = PendingDrafts()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }

    @Test
    fun segmentsAccumulateAndFinishSendsThem() = test {
        val speech = FakeSpeech(
            listOf(SpeechEvent.Partial("Yarın"), SpeechEvent.Final("Yarın maç var")),
            listOf(SpeechEvent.Final("annemi ara")),
            listOf(SpeechEvent.Partial("bir de")),
        )
        val extractor = FakeExtractor(ExtractionOutcome.Success(plan))
        val viewModel = CaptureViewModel(speech, extractor, pending)

        viewModel.startListening()
        runCurrent()
        assertEquals("Yarın maç var annemi ara bir de", viewModel.uiState.value.transcript)

        viewModel.finish()
        advanceUntilIdle()

        assertEquals(listOf("Yarın maç var annemi ara bir de"), extractor.transcripts)
        // A plan goes to the review screen; capture starts over and keeps no transcript.
        assertEquals(CaptureEvent.OpenReview, viewModel.events.first())
        assertEquals(plan.drafts, pending.take())
        assertEquals(CaptureUiState(), viewModel.uiState.value)
    }

    @Test
    fun addManuallyOpensReviewWithoutDrafts() = test {
        pending.put(plan.drafts)
        val viewModel = CaptureViewModel(FakeSpeech(), FakeExtractor(), pending)

        viewModel.addManually()

        assertEquals(CaptureEvent.OpenReview, viewModel.events.first())
        assertEquals(emptyList<TaskDraft>(), pending.take())
    }

    @Test
    fun silenceShowsNothingHeard() = test {
        val viewModel = CaptureViewModel(FakeSpeech(), FakeExtractor(), pending)

        viewModel.startListening()
        advanceTimeBy(CaptureViewModel.NOTHING_HEARD_TIMEOUT_MS + 1)

        assertEquals(CapturePhase.NothingHeard, viewModel.uiState.value.phase)
    }

    @Test
    fun noSpeechRestartsListening() = test {
        val speech = FakeSpeech(
            listOf(SpeechEvent.Failed(SpeechError.NoSpeech)),
            listOf(SpeechEvent.Final("Maç var")),
        )
        val viewModel = CaptureViewModel(speech, FakeExtractor(), pending)

        viewModel.startListening()
        runCurrent()

        assertEquals("Maç var", viewModel.uiState.value.transcript)
        assertEquals(CapturePhase.Listening, viewModel.uiState.value.phase)
    }

    @Test
    fun finishingWithNothingHeardDoesNotCallTheProxy() = test {
        val extractor = FakeExtractor()
        val viewModel = CaptureViewModel(FakeSpeech(), extractor, pending)

        viewModel.startListening()
        runCurrent()
        viewModel.finish()
        advanceUntilIdle()

        assertEquals(CapturePhase.NothingHeard, viewModel.uiState.value.phase)
        assertEquals(emptyList<String>(), extractor.transcripts)
    }

    @Test
    fun retryAfterFailureResendsTheSameTranscript() = test {
        val extractor = FakeExtractor(
            ExtractionOutcome.Failure(ExtractionFailure.Offline),
            ExtractionOutcome.Success(plan),
        )
        val viewModel = CaptureViewModel(FakeSpeech(listOf(SpeechEvent.Final("Maç var"))), extractor, pending)

        viewModel.startListening()
        runCurrent()
        viewModel.finish()
        advanceUntilIdle()
        assertEquals(ExtractionFailure.Offline, viewModel.uiState.value.failure)

        viewModel.retryExtraction()
        advanceUntilIdle()

        assertEquals(listOf("Maç var", "Maç var"), extractor.transcripts)
        assertNull(viewModel.uiState.value.failure)
        assertEquals(plan.drafts, pending.take())
    }

    @Test
    fun repeatedInterruptionsBecomeNetworkError() = test {
        val interrupted = listOf(SpeechEvent.Failed(SpeechError.Interrupted))
        val speech = FakeSpeech(interrupted, interrupted, interrupted, interrupted, interrupted)
        val viewModel = CaptureViewModel(speech, FakeExtractor(), pending)

        viewModel.startListening()
        advanceTimeBy(5_000)

        assertEquals(CapturePhase.Idle, viewModel.uiState.value.phase)
        assertEquals(SpeechError.Network, viewModel.uiState.value.error)
        assertEquals(CaptureViewModel.MAX_INTERRUPTIONS + 1, speech.listenCalls)
    }

    @Test
    fun errorAfterSpeechStillSendsWhatWasHeard() = test {
        val speech = FakeSpeech(listOf(SpeechEvent.Partial("Maç var"), SpeechEvent.Failed(SpeechError.Network)))
        val extractor = FakeExtractor(ExtractionOutcome.Success(ExtractionResult.OffTopic))
        val viewModel = CaptureViewModel(speech, extractor, pending)

        viewModel.startListening()
        advanceUntilIdle()

        assertEquals(listOf("Maç var"), extractor.transcripts)
        assertEquals(CapturePhase.Result, viewModel.uiState.value.phase)
        assertEquals(ExtractionResult.OffTopic, viewModel.uiState.value.result)
    }

    @Test
    fun resetDropsEverything() = test {
        val viewModel = CaptureViewModel(FakeSpeech(listOf(SpeechEvent.Final("Maç var"))), FakeExtractor(), pending)

        viewModel.startListening()
        runCurrent()
        viewModel.reset()

        assertEquals(CaptureUiState(), viewModel.uiState.value)
    }
}
