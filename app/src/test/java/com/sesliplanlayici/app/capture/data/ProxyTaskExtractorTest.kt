package com.sesliplanlayici.app.capture.data

import com.sesliplanlayici.app.capture.domain.ExtractionFailure
import com.sesliplanlayici.app.capture.domain.ExtractionOutcome
import com.sesliplanlayici.app.capture.domain.ExtractionResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyTaskExtractorTest {

    // 21:40 in Istanbul, the "now" of docs/AI_CONTRACT.md.
    private val clock = Clock.fixed(Instant.parse("2026-10-11T18:40:00Z"), ZoneId.of("Europe/Istanbul"))

    private val requests = mutableListOf<HttpRequestData>()

    private fun extractor(
        baseUrl: String = "https://proxy.test/",
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ProxyTaskExtractor {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json() }
        }
        return ProxyTaskExtractor(client, AiResponseParser(), clock, baseUrl)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun sendsTheContractRequest() = runTest {
        extractor { json("""{"input_kind":"empty","tasks":[]}""") }.extract("Yarın sabah onda maç var")

        val request = requests.single()
        assertEquals("https://proxy.test/v1/extract", request.url.toString())
        val sent = Json.decodeFromString<AiExtractionRequestDto>(request.body.toByteArray().decodeToString())
        assertEquals(
            AiExtractionRequestDto(
                transcript = "Yarın sabah onda maç var",
                now = "2026-10-11T21:40:00+03:00",
                timezone = "Europe/Istanbul",
                locale = "tr-TR",
            ),
            sent,
        )
    }

    @Test
    fun validAnswerBecomesPlan() = runTest {
        val outcome = extractor {
            json("""{"input_kind":"plan","tasks":[{"title":"Maç izle","date":"2026-10-12","time":"10:00"}]}""")
        }.extract("Yarın sabah onda maç var")

        val result = (outcome as ExtractionOutcome.Success).result
        assertEquals(listOf("Maç izle"), (result as ExtractionResult.Plan).drafts.map { it.title })
    }

    @Test
    fun offTopicIsPassedThrough() = runTest {
        val outcome = extractor { json("""{"input_kind":"off_topic","tasks":[]}""") }.extract("Fıkra anlat")

        assertEquals(ExtractionOutcome.Success(ExtractionResult.OffTopic), outcome)
    }

    @Test
    fun brokenBodyIsBadResponse() = runTest {
        val outcome = extractor { json("Elbette! İşte görevler") }.extract("Maç var")

        assertEquals(ExtractionOutcome.Failure(ExtractionFailure.BadResponse), outcome)
    }

    @Test
    fun tooManyRequestsIsRateLimited() = runTest {
        val outcome = extractor { json("""{"error":"rate_limited"}""", HttpStatusCode.TooManyRequests) }.extract("Maç")

        assertEquals(ExtractionOutcome.Failure(ExtractionFailure.RateLimited), outcome)
    }

    @Test
    fun proxyErrorsAreServerErrors() = runTest {
        listOf(HttpStatusCode.BadRequest, HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway).forEach {
            val outcome = extractor { _ -> json("""{"error":"x"}""", it) }.extract("Maç")
            assertEquals(it.toString(), ExtractionOutcome.Failure(ExtractionFailure.ServerError), outcome)
        }
    }

    @Test
    fun connectionFailureIsOffline() = runTest {
        val outcome = extractor { throw IOException("Unable to resolve host") }.extract("Maç")

        assertEquals(ExtractionOutcome.Failure(ExtractionFailure.Offline), outcome)
    }

    @Test
    fun missingBaseUrlSendsNothing() = runTest {
        val outcome = extractor(baseUrl = "") { json("{}") }.extract("Maç")

        assertEquals(ExtractionOutcome.Failure(ExtractionFailure.NotConfigured), outcome)
        assertTrue(requests.isEmpty())
    }
}
