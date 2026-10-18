package com.sesliplanlayici.app.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

// Proves the serialization compiler plugin is wired up. Replace with real tests once the
// AI response validator lands.
class SerializationSetupTest {

    @Serializable
    private data class Sample(val title: String, val time: String? = null)

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun roundTripKeepsValues() {
        val sample = Sample(title = "Annemi ara", time = null)

        val decoded = json.decodeFromString<Sample>(json.encodeToString(sample))

        assertEquals(sample, decoded)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val decoded = json.decodeFromString<Sample>("""{"title":"Maç izle","time":"10:00","extra":1}""")

        assertEquals(Sample(title = "Maç izle", time = "10:00"), decoded)
    }
}
