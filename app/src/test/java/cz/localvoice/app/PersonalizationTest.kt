package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PersonalizationTest {
    @Test
    fun dictionaryUpsertReplacesSameSpokenForm() {
        val entries = Personalization.upsertDictionary(
            entries = listOf(DictionaryEntry("Whisper Flow", "Whisper Flow")),
            entry = DictionaryEntry(" whisper flow ", "Wispr Flow"),
        )

        assertEquals(listOf(DictionaryEntry("whisper flow", "Wispr Flow")), entries)
    }

    @Test
    fun snippetExpandsOnlyAnExactSpokenTrigger() {
        val snippets = listOf(TextSnippet("moje adresa", "Křižíkova 12, Praha"))

        assertEquals("Křižíkova 12, Praha", Personalization.expandSnippet(" Moje adresa. ", snippets))
        assertNull(Personalization.expandSnippet("Pošli moji adresu.", snippets))
    }

    @Test
    fun codecsRoundTripBoundedEntries() {
        val dictionary = listOf(DictionaryEntry("vyspr flow", "Wispr Flow"))
        val snippets = listOf(TextSnippet("podpis", "S pozdravem,\nMatěj"))

        assertEquals(dictionary, Personalization.decodeDictionary(Personalization.encodeDictionary(dictionary)))
        assertEquals(snippets, Personalization.decodeSnippets(Personalization.encodeSnippets(snippets)))
    }

    @Test
    fun rejectsBlankOrOversizedPersonalization() {
        assertFailsWith<IllegalArgumentException> {
            Personalization.upsertDictionary(emptyList(), DictionaryEntry("", "Wispr Flow"))
        }
        assertFailsWith<IllegalArgumentException> {
            Personalization.upsertSnippets(emptyList(), TextSnippet("x".repeat(61), "text"))
        }
        assertFailsWith<IllegalArgumentException> {
            Personalization.upsertSnippets(emptyList(), TextSnippet("podpis", "x".repeat(4_001)))
        }
    }
}
