package com.sesliplanlayici.app.capture.data

import com.sesliplanlayici.app.capture.domain.ExtractionFailure
import com.sesliplanlayici.app.capture.domain.ExtractionOutcome
import com.sesliplanlayici.app.capture.domain.TaskExtractor
import com.sesliplanlayici.app.core.di.ProxyBaseUrl
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends the transcript to the AI proxy (docs/AI_CONTRACT.md) and validates the answer with
 * [AiResponseParser]. The transcript is never logged or stored.
 */
class ProxyTaskExtractor @Inject constructor(
    private val client: HttpClient,
    private val parser: AiResponseParser,
    private val clock: Clock,
    @ProxyBaseUrl private val baseUrl: String,
) : TaskExtractor {

    override suspend fun extract(transcript: String): ExtractionOutcome {
        if (baseUrl.isBlank()) return ExtractionOutcome.Failure(ExtractionFailure.NotConfigured)

        val now = ZonedDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
        val request = AiExtractionRequestDto(
            transcript = transcript,
            now = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            timezone = clock.zone.id,
            locale = LOCALE,
        )

        return withContext(Dispatchers.IO) {
            try {
                val response = client.post("${baseUrl.trimEnd('/')}$PATH") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                when (response.status) {
                    HttpStatusCode.OK -> when (val parsed = parser.parse(response.bodyAsText())) {
                        is AiResponseParse.Valid -> ExtractionOutcome.Success(parsed.result)
                        AiResponseParse.Malformed -> ExtractionOutcome.Failure(ExtractionFailure.BadResponse)
                    }
                    HttpStatusCode.TooManyRequests -> ExtractionOutcome.Failure(ExtractionFailure.RateLimited)
                    else -> ExtractionOutcome.Failure(ExtractionFailure.ServerError)
                }
            } catch (e: IOException) {
                // No connection, DNS failure or timeout (Ktor's timeout exceptions are IOExceptions).
                ExtractionOutcome.Failure(ExtractionFailure.Offline)
            }
        }
    }

    private companion object {
        const val PATH = "/v1/extract"
        const val LOCALE = "tr-TR"
    }
}
