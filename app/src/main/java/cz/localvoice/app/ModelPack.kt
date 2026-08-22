package cz.localvoice.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ModelFile(
    val name: String,
    val url: String,
    val size: Long,
    val sha256: String,
)

object ModelPack {
    const val version = "android-multilingual-v3"

    val files = listOf(
        ModelFile(
            name = "whisper-encoder.int8.onnx",
            url = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/65176e2deb88badc814a94058666cadccc29b61c/tiny-encoder.int8.onnx",
            size = 12_937_772,
            sha256 = "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434",
        ),
        ModelFile(
            name = "whisper-decoder.int8.onnx",
            url = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/65176e2deb88badc814a94058666cadccc29b61c/tiny-decoder.int8.onnx",
            size = 89_855_401,
            sha256 = "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925",
        ),
        ModelFile(
            name = "whisper-tokens.txt",
            url = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/65176e2deb88badc814a94058666cadccc29b61c/tiny-tokens.txt",
            size = 816_730,
            sha256 = "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126",
        ),
        ModelFile(
            name = "semantic-qwen3-0.6b.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/8414150f2e9dcc82449bcc9c5abc404b399a4d06/qwen3_0_6b_mixed_int4.litertlm",
            size = 497_664_000,
            sha256 = "b1baab462f6be49d70eada79d715c2c52cd9ece0cad00bddf6a2c097d23498e9",
        ),
    )

    val totalBytes: Long = files.sumOf(ModelFile::size)

    fun directory(context: Context): File = File(context.filesDir, "models/$version")

    fun file(context: Context, name: String): File = File(directory(context), name)

    fun isReady(context: Context): Boolean {
        val directory = directory(context)
        if (File(directory, "ready").readTextOrNull()?.trim() != version) return false
        return files.all { file(context, it.name).length() == it.size }
    }

    suspend fun download(context: Context, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val directory = directory(context).apply { mkdirs() }
        var complete = 0L
        files.forEach { model ->
            val destination = File(directory, model.name)
            if (!destination.matches(model)) {
                downloadFile(model, destination) { current ->
                    onProgress(complete + current, totalBytes)
                }
            }
            complete += model.size
            onProgress(complete, totalBytes)
        }
        File(directory, "ready").writeText(version)
    }

    private fun downloadFile(model: ModelFile, destination: File, onProgress: (Long) -> Unit) {
        val partial = File(destination.parentFile, "${destination.name}.part")
        if (partial.matches(model)) {
            require(partial.renameTo(destination)) { "Could not finalize model download" }
            onProgress(model.size)
            return
        }
        if (partial.length() >= model.size) partial.delete()
        val existingBytes = partial.length()
        val connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LocalVoiceAndroid/0.1")
            if (existingBytes > 0) setRequestProperty("Range", "bytes=$existingBytes-")
        }
        try {
            require(connection.responseCode in 200..299) { "Download failed: HTTP ${connection.responseCode}" }
            val append = existingBytes > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (!append) partial.delete()
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = if (append) existingBytes else 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written)
                    }
                }
            }
            if (!partial.matches(model)) {
                partial.delete()
                error("Downloaded model failed size or SHA-256 verification")
            }
            require(partial.renameTo(destination)) { "Could not finalize model download" }
        } finally {
            connection.disconnect()
        }
    }

    private fun File.matches(model: ModelFile): Boolean =
        isFile && length() == model.size && sha256() == model.sha256

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()
}
