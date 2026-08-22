package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
