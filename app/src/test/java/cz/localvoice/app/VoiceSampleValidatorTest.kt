package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertFailsWith

class VoiceSampleValidatorTest {
    @Test
    fun acceptsClearReferenceAndRejectsSilenceOrClipping() {
        VoiceSampleValidator.validate(FloatArray(100) { if (it % 2 == 0) 0.1f else -0.1f }, sampleRate = 10)
        assertFailsWith<IllegalArgumentException> {
            VoiceSampleValidator.validate(FloatArray(100), sampleRate = 10)
        }
        assertFailsWith<IllegalArgumentException> {
            VoiceSampleValidator.validate(FloatArray(100) { 1f }, sampleRate = 10)
        }
    }
}
