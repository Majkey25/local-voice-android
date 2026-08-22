package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertTrue

class CalibrationPromptTest {
    @Test
    fun tierOnePromptsSupportRoughlyTwoMinutesOfReading() {
        listOf("cs", "en", "de", "fr", "es").forEach { language ->
            val words = UserSettings.calibrationPrompt(language).split(Regex("\\s+")).size

            assertTrue(words in 180..260, "$language prompt has $words words")
        }
    }
}
