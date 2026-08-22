package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecognitionCalibrationTest {
    @Test
    fun dictionaryCorrectsExactWordsAndPhrasesOnly() {
        val entries = listOf(DictionaryEntry("whisper flow", "Wispr Flow"))

        assertEquals("Používám Wispr Flow.", Personalization.applyDictionary("Používám whisper flow.", entries))
        assertEquals("whisper flowing", Personalization.applyDictionary("whisper flowing", entries))
    }

    @Test
    fun dictionaryWritesDollarAndBackslashLiterally() {
        assertEquals(
            "Cena je ${'$'}5 a cesta C:\\Data.",
            Personalization.applyDictionary(
                "Cena je pět dolarů a cesta data.",
                listOf(
                    DictionaryEntry("pět dolarů", "${'$'}5"),
                    DictionaryEntry("data", "C:\\Data"),
                ),
            ),
        )
    }

    @Test
    fun dictionaryDoesNotRewriteItsOwnOutput() {
        assertEquals(
            "Používám Wispr Flow.",
            Personalization.applyDictionary(
                "Používám vyspr flow.",
                listOf(
                    DictionaryEntry("vyspr flow", "Wispr Flow"),
                    DictionaryEntry("flow", "FlowX"),
                ),
            ),
        )
    }

    @Test
    fun calibrationSuggestsObservedSubstitution() {
        val report = RecognitionCalibration.analyze(
            reference = "Schůzka bude ve čtvrtek v šest.",
            transcript = "Schůzka bude ve čvrtek v šest.",
        )

        assertEquals(listOf(DictionaryEntry("čvrtek", "čtvrtek")), report.suggestions)
        assertTrue(report.baselineAccuracyPercent < 100)
    }

    @Test
    fun calibrationKeepsMultiwordNamesAsOneCorrection() {
        val report = RecognitionCalibration.analyze(
            reference = "Používám Local Voice každý den.",
            transcript = "Používám lokal vojs každý den.",
        )

        assertEquals(listOf(DictionaryEntry("lokal vojs", "Local Voice")), report.suggestions)
    }

    @Test
    fun calibrationRejectsLongMismatchPhrases() {
        val report = RecognitionCalibration.analyze(
            reference = "alpha beta gamma delta epsilon",
            transcript = "one two three four five",
        )

        assertTrue(report.suggestions.isEmpty())
    }

    @Test
    fun calibrationDoesNotTurnInsertionsOrDeletionsIntoDictionaryRules() {
        assertTrue(
            RecognitionCalibration.analyze(
                reference = "jedna dvě tři",
                transcript = "jedna navíc dvě tři",
            ).suggestions.isEmpty(),
        )
        assertTrue(
            RecognitionCalibration.analyze(
                reference = "jedna dvě tři",
                transcript = "jedna tři",
            ).suggestions.isEmpty(),
        )
    }

    @Test
    fun suggestedDictionaryImprovesSameSampleScore() {
        val report = RecognitionCalibration.analyze(
            reference = "Používám Wispr Flow každý den.",
            transcript = "Používám whisper flow každý den.",
        )

        assertTrue(report.correctedAccuracyPercent > report.baselineAccuracyPercent)
    }
}
