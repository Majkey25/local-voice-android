package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioChunksTest {
    @Test
    fun coversLongAudioWithoutOverlapOrLoss() {
        assertEquals(
            listOf(0..28, 29..57, 58..60),
            AudioChunks.ranges(totalSamples = 61, maxSamples = 29),
        )
        assertEquals(emptyList(), AudioChunks.ranges(totalSamples = 0, maxSamples = 29))
    }
}
