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
    fun offersEverySupportedLanguageBeyondDeviceLocales() {
        assertEquals(
            listOf("en-US", "cs-CZ", "de-DE", "fr-FR", "es-ES"),
            UserSettings.availableLanguageTags(
                systemTags = listOf("en-US"),
                keyboardTags = emptyList(),
            ),
        )
    }

    @Test
    fun keepsSupportedDeviceLanguagesFirstWithoutDuplicates() {
        assertEquals(
            listOf("de-DE", "cs-CZ", "en-US", "fr-FR", "es-ES"),
            UserSettings.availableLanguageTags(
                systemTags = listOf("it-IT", "de-DE"),
                keyboardTags = listOf("cs_CZ", "de_DE"),
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

    @Test
    fun styleLabelsUseEnglishUiCopy() {
        assertEquals("Natural", UserSettings.styleName(UserSettings.CASUAL))
        assertEquals("Balanced", UserSettings.styleName(UserSettings.BALANCED))
        assertEquals("Professional", UserSettings.styleName(UserSettings.PROFESSIONAL))
        assertEquals("My style", UserSettings.styleName(UserSettings.CUSTOM))
        assertEquals("Verbatim", UserSettings.styleName(UserSettings.VERBATIM))
    }
}
