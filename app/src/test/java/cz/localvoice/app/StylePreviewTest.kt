package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StylePreviewTest {
    @Test
    fun mergesSystemAndKeyboardLanguagesInStableOrder() {
        assertEquals(
            listOf("cs-CZ", "en-US", "de-DE"),
            UserSettings.mergeLanguageTags(
                systemTags = listOf("cs-CZ", "en-US"),
                keyboardTags = listOf("en_US", "de_DE", ""),
            ),
        )
    }

    @Test
    fun tierOneLanguagesHaveThreeDistinctExamples() {
        listOf("cs", "en", "de", "fr", "es").forEach { language ->
            val previews = UserSettings.stylePreviews(language)
            assertEquals(3, previews.size)
            assertEquals(3, previews.map(StylePreview::example).distinct().size)
            assertTrue(previews.all { it.example.isNotBlank() })
        }
    }
}
