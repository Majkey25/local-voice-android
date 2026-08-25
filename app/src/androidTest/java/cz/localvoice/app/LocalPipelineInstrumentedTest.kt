package cz.localvoice.app

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocalPipelineInstrumentedTest {
    @Test
    fun legacyProfileSurvivesLanguageRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences(
            VoiceAccessibilityService.PREFERENCES,
            android.content.Context.MODE_PRIVATE,
        )
        val dictionary = listOf(DictionaryEntry("lokal vojs", "Local Voice"))
        val snippets = listOf(TextSnippet("podpis", "S pozdravem"))
        preferences.edit()
            .clear()
            .putString("language_tag", "cs-CZ")
            .putString("style", UserSettings.CUSTOM)
            .putString("writing_sample", "Legacy Czech writing sample")
            .putString("cleanup_cs-cz", CleanupLevel.LIGHT.name)
            .putString("dictionary_cs-cz", Personalization.encodeDictionary(dictionary))
            .putString("snippets_cs-cz", Personalization.encodeSnippets(snippets))
            .commit()

        try {
            val initialCzech = UserSettings.load(context)
            assertEquals(UserSettings.CUSTOM, initialCzech.style)
            assertEquals("Legacy Czech writing sample", initialCzech.writingSample)

            UserSettings.save(context, UserSettings.load(context, "en-US"), onboardingDone = false)
            val returnedCzech = UserSettings.load(context, "cs-CZ")

            assertEquals(UserSettings.CUSTOM, returnedCzech.style)
            assertEquals("Legacy Czech writing sample", returnedCzech.writingSample)
            assertEquals(CleanupLevel.LIGHT, returnedCzech.cleanup)
            assertEquals(dictionary, returnedCzech.dictionary)
            assertEquals(snippets, returnedCzech.snippets)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun localSemanticEngineAppliesLatestCorrection() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prerequisitesReady = ModelPack.isReady(context) && SemanticEngine.isAvailable(context)
        if (Build.MODEL == "SM-S938B" && Build.VERSION.SDK_INT == 36) {
            assertTrue("Release device model pack or semantic engine is unavailable", prerequisitesReady)
        } else {
            assumeTrue("Non-release device lacks safe model capacity", prerequisitesReady)
        }
        val engine = SemanticEngine(context)
        try {
            val plan = engine.edit(
                transcript = "The meeting is at five, actually at six.",
                selection = "",
                profile = UserProfile("en-US", UserSettings.BALANCED, ""),
            )
            val text = plan.text.lowercase(Locale.US)
            assertEquals(EditAction.INSERT, plan.action)
            assertTrue("six" in text)
            assertFalse("five" in text)

            val czech = engine.edit(
                transcript = "Schůzka je v pět, vlastně v šest.",
                selection = "",
                profile = UserProfile("cs-CZ", UserSettings.BALANCED, ""),
            ).text.lowercase(Locale.forLanguageTag("cs-CZ"))
            assertTrue("šest" in czech)
            assertFalse("pět" in czech)
        } finally {
            engine.close()
        }
    }
}
