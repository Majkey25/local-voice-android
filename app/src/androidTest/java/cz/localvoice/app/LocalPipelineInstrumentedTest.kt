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
