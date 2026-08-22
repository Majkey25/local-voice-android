package cz.localvoice.app

import kotlin.math.sqrt

class SpeechGate(
    private val minimumRms: Double = 0.005,
    private val minimumSpeechMs: Int = 160,
    private val paddingMs: Int = 120,
) {
    init {
        require(minimumRms > 0 && minimumSpeechMs > 0 && paddingMs >= 0) {
            "Invalid speech gate configuration"
        }
    }

    fun trim(samples: FloatArray, sampleRate: Int): FloatArray {
        require(sampleRate > 0 && samples.isNotEmpty()) { "Invalid audio" }
        require(samples.all { it.isFinite() }) { "Invalid audio samples" }
        val frameSize = (sampleRate * FRAME_MS / 1_000).coerceAtLeast(1)
        val requiredFrames = (minimumSpeechMs + FRAME_MS - 1) / FRAME_MS
        var runStart = -1
        var runFrames = 0
        var speechStart = -1
        var lastActiveEnd = -1
        for (start in samples.indices step frameSize) {
            val end = minOf(start + frameSize, samples.size)
            var energy = 0.0
            for (index in start until end) energy += samples[index] * samples[index]
            if (sqrt(energy / (end - start)) >= minimumRms) {
                if (runStart < 0) runStart = start
                runFrames++
                if (speechStart >= 0) {
                    lastActiveEnd = end
                } else if (runFrames >= requiredFrames) {
                    speechStart = runStart
                    lastActiveEnd = end
                }
            } else {
                runStart = -1
                runFrames = 0
            }
        }
        require(speechStart >= 0) { "No speech detected" }
        val padding = sampleRate * paddingMs / 1_000
        return samples.copyOfRange(
            (speechStart - padding).coerceAtLeast(0),
            (lastActiveEnd + padding).coerceAtMost(samples.size),
        )
    }

    private companion object {
        const val FRAME_MS = 20
    }
}
