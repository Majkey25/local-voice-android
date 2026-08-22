package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpeechGateTest {
    private val gate = SpeechGate()

    @Test
    fun rejectsSilence() {
        assertFailsWith<IllegalArgumentException> {
            gate.trim(FloatArray(16_000), 16_000)
        }
    }

    @Test
    fun rejectsSeparatedNoiseBursts() {
        val samples = FloatArray(16_000)
        repeat(8) { burst ->
            val start = burst * 640
            for (index in start until start + 320) samples[index] = 0.1f
        }

        assertFailsWith<IllegalArgumentException> {
            gate.trim(samples, 16_000)
        }
    }

    @Test
    fun trimsSilenceAroundContiguousSpeech() {
        val samples = FloatArray(32_000)
        for (index in 8_000 until 24_000) {
            samples[index] = if (index % 2 == 0) 0.1f else -0.1f
        }

        assertEquals(19_840, gate.trim(samples, 16_000).size)
    }

    @Test
    fun rejectsInvalidSamples() {
        assertFailsWith<IllegalArgumentException> { gate.trim(FloatArray(1), 0) }
        assertFailsWith<IllegalArgumentException> { gate.trim(floatArrayOf(Float.NaN), 16_000) }
    }
}
