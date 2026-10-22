package cz.localvoice.app

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelPackTest {
    @Test
    fun packHasPinnedBoundedFiles() {
        assertEquals(4, ModelPack.files.size)
        assertEquals(601_273_903L, ModelPack.totalBytes)
        ModelPack.files.forEach {
            assertEquals(64, it.sha256.length)
            check(it.url.startsWith("https://"))
            check(it.size in 1..500_000_000)
        }
    }

    @Test
    fun rejectsOversizedDeclaredAndChunkedResponsesWithoutKeepingUnsafePartials() {
        for (chunked in listOf(false, true)) {
            withResponse("model-extra", chunked = chunked) { model, destination ->
                assertFailsWith<IllegalArgumentException> { ModelPack.downloadFile(model, destination) {} }
                assertFalse(destination.exists())
                assertFalse(File(destination.parentFile, "${destination.name}.part").exists())
            }
        }
    }

    @Test
    fun validDownloadAndResumeStillVerifyAndFinalizeTheModel() {
        withResponse("model") { model, destination ->
            ModelPack.downloadFile(model, destination) {}
            assertContentEquals("model".toByteArray(), destination.readBytes())
        }
        withResponse("del", range = "bytes 2-4/5") { model, destination ->
            File(destination.parentFile, "${destination.name}.part").writeText("mo")
            ModelPack.downloadFile(model, destination) {}
            assertContentEquals("model".toByteArray(), destination.readBytes())
            assertFalse(File(destination.parentFile, "${destination.name}.part").exists())
        }
    }

    @Test
    fun rejectsWrongResumeRangeBeforeAppending() {
        withResponse("del", range = "bytes 1-3/5") { model, destination ->
            File(destination.parentFile, "${destination.name}.part").writeText("mo")
            val error = assertFailsWith<IllegalArgumentException> { ModelPack.downloadFile(model, destination) {} }
            assertEquals("Invalid model download range", error.message)
            assertFalse(destination.exists())
        }
    }

    @Test
    fun temporaryServerFailurePreservesPartialForRetry() {
        withResponse("unavailable", status = 503) { model, destination ->
            val partial = File(destination.parentFile, "${destination.name}.part").apply { writeText("mo") }
            assertFailsWith<IllegalStateException> { ModelPack.downloadFile(model, destination) {} }
            assertEquals("mo", partial.readText())
            assertFalse(destination.exists())
        }
    }

    private fun withResponse(
        body: String,
        range: String? = null,
        chunked: Boolean = false,
        status: Int = if (range == null) 200 else 206,
        check: (ModelFile, File) -> Unit,
    ) {
        val directory = Files.createTempDirectory("model-download").toFile()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 5_000 }
        val executor = Executors.newSingleThreadExecutor()
        val response = executor.submit {
            server.accept().use { socket ->
                socket.soTimeout = 5_000
                val request = socket.getInputStream().bufferedReader()
                val headers = generateSequence { request.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                if (range != null) assertTrue(headers.any { it.equals("Range: bytes=2-", ignoreCase = true) })
                val payload = buildString {
                    append("HTTP/1.1 $status Test\r\nConnection: close\r\n")
                    if (range != null) append("Content-Range: $range\r\n")
                    if (chunked) {
                        append("Transfer-Encoding: chunked\r\n\r\n${body.length.toString(16)}\r\n$body\r\n0\r\n\r\n")
                    } else {
                        append("Content-Length: ${body.toByteArray().size}\r\n\r\n$body")
                    }
                }
                socket.getOutputStream().write(payload.toByteArray())
            }
        }
        try {
            val digest = MessageDigest.getInstance("SHA-256").digest("model".toByteArray())
                .joinToString("") { "%02x".format(it) }
            val model = ModelFile("fixture.model", "http://127.0.0.1:${server.localPort}/model", 5, digest)
            check(model, File(directory, model.name))
            response.get(5, TimeUnit.SECONDS)
        } finally {
            server.close()
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }
}
