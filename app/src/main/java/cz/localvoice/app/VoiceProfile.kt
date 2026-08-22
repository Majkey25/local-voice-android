package cz.localvoice.app

import android.content.Context
import androidx.core.content.edit
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.abs
import kotlin.math.sqrt

enum class VoiceConsent(val value: String) {
    OWN_VOICE("own_voice"),
    EXPLICIT_PERMISSION("explicit_permission"),
}

object VoiceProfileStore {
    fun referenceFile(context: Context): File = File(context.filesDir, "voice_profiles/default/reference.wav")

    fun save(context: Context, samples: FloatArray, consent: VoiceConsent) {
        VoiceSampleValidator.validate(samples, AudioCapture.SAMPLE_RATE)
        val destination = referenceFile(context)
        val directory = requireNotNull(destination.parentFile)
        require(directory.isDirectory || directory.mkdirs()) { "Could not create voice profile directory" }
        val temporary = File(directory, "reference.wav.part")
        WaveFile.write(temporary, samples, AudioCapture.SAMPLE_RATE)
        runCatching {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }.getOrElse {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        context.getSharedPreferences(VoiceAccessibilityService.PREFERENCES, Context.MODE_PRIVATE).edit {
            putString("voice_consent", consent.value)
            putLong("voice_consent_at", System.currentTimeMillis())
        }
    }

    fun delete(context: Context) {
        val reference = referenceFile(context)
        reference.delete()
        reference.parentFile?.let { File(it, "reference.wav.part").delete() }
        context.getSharedPreferences(VoiceAccessibilityService.PREFERENCES, Context.MODE_PRIVATE).edit {
            remove("voice_consent")
            remove("voice_consent_at")
        }
    }
}

object VoiceSampleValidator {
    fun validate(samples: FloatArray, sampleRate: Int) {
        require(sampleRate > 0 && samples.size in sampleRate * 10..sampleRate * 30) {
            "Voice sample must be 10 to 30 seconds"
        }
        val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
        require(rms >= 0.005) { "Voice sample is too quiet" }
        val clippedRatio = samples.count { abs(it) >= 0.99f }.toDouble() / samples.size
        require(clippedRatio < 0.02) { "Voice sample is distorted" }
    }
}

object WaveFile {
    fun write(file: File, samples: FloatArray, sampleRate: Int) {
        require(sampleRate > 0) { "Invalid sample rate" }
        val dataBytes = samples.size * Short.SIZE_BYTES
        DataOutputStream(BufferedOutputStream(file.outputStream())).use { output ->
            output.writeBytes("RIFF")
            output.writeLittleEndian(36 + dataBytes)
            output.writeBytes("WAVEfmt ")
            output.writeLittleEndian(16)
            output.writeLittleEndian(1, Short.SIZE_BYTES)
            output.writeLittleEndian(1, Short.SIZE_BYTES)
            output.writeLittleEndian(sampleRate)
            output.writeLittleEndian(sampleRate * Short.SIZE_BYTES)
            output.writeLittleEndian(Short.SIZE_BYTES, Short.SIZE_BYTES)
            output.writeLittleEndian(16, Short.SIZE_BYTES)
            output.writeBytes("data")
            output.writeLittleEndian(dataBytes)
            samples.forEach { sample ->
                val pcm = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
                output.writeLittleEndian(pcm, Short.SIZE_BYTES)
            }
        }
    }

    private fun DataOutputStream.writeLittleEndian(value: Int, bytes: Int = Int.SIZE_BYTES) {
        repeat(bytes) { writeByte(value ushr (it * 8)) }
    }
}
