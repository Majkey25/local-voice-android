package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertEquals

class BubblePreferencesTest {
    @Test
    fun acceptsSupportedSizeAndOpacity() {
        assertEquals(BubblePreferences(115, 40), BubblePreferences.from(115, 40))
    }

    @Test
    fun rejectsUnsupportedPersistedValuesToDefaults() {
        assertEquals(BubblePreferences(), BubblePreferences.from(999, -1))
    }
}
