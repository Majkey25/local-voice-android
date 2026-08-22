package cz.localvoice.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WaveFileTest {
    @Test
    fun writesMonoPcm16Wave() {
        val file = Files.createTempFile("local-voice", ".wav").toFile()
        try {
            WaveFile.write(file, floatArrayOf(-1f, 0f, 1f), 16_000)
            val bytes = file.readBytes()

            assertContentEquals("RIFF".encodeToByteArray(), bytes.copyOfRange(0, 4))
            assertContentEquals("WAVE".encodeToByteArray(), bytes.copyOfRange(8, 12))
            assertEquals(50, bytes.size)
            assertEquals(6, littleEndianInt(bytes, 40))
        } finally {
            file.delete()
        }
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
