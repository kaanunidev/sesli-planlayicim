package com.sesliplanlayici.app.capture.domain

/** Turns a transcript into tasks (through the AI proxy). */
interface TaskExtractor {
    suspend fun extract(transcript: String): ExtractionOutcome
}

sealed interface ExtractionOutcome {
    data class Success(val result: ExtractionResult) : ExtractionOutcome
    data class Failure(val reason: ExtractionFailure) : ExtractionOutcome
}

enum class ExtractionFailure {
    /** No connection or the request timed out. */
    Offline,

    /** Too many requests (proxy limit or the AI provider's quota). */
    RateLimited,

    /** The proxy rejected the request or failed on its side. */
    ServerError,

    /** The proxy answered, but not in the contract's shape. */
    BadResponse,

    /** The app was built without a proxy URL (local.properties → proxy.baseUrl). */
    NotConfigured,
}
