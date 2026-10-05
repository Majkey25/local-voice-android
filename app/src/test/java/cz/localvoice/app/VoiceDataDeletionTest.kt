package cz.localvoice.app

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDataDeletionTest {
    @Test
    fun removesReferenceAndPartialRecordingAndAllowsRepeatedDeletion() {
        val directory = Files.createTempDirectory("voice-deletion").toFile()
        try {
            val reference = File(directory, "reference.wav").apply { writeText("audio") }
            val partial = File(directory, "reference.wav.part").apply { writeText("partial") }
            VoiceProfileStore.deleteFiles(reference)
            assertFalse(reference.exists())
            assertFalse(partial.exists())
            VoiceProfileStore.deleteFiles(reference)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun reportsFailureInsteadOfPretendingTheVoiceDataWasDeleted() {
        val directory = Files.createTempDirectory("voice-deletion-failure").toFile()
        try {
            val reference = File(directory, "reference.wav").apply { mkdir() }
            val retained = File(reference, "retained").apply { writeText("audio") }
            assertThrows(IOException::class.java) { VoiceProfileStore.deleteFiles(reference) }
            assertTrue(retained.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
