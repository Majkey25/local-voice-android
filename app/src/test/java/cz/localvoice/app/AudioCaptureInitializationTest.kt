package cz.localvoice.app

import android.Manifest
import android.app.ActivityManager
import android.media.AudioRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AudioCaptureInitializationTest {
    @Test
    fun `CPU capacity requires both total and available RAM thresholds`() {
        val manager = RuntimeEnvironment.getApplication().getSystemService(ActivityManager::class.java)
        val gib = 1_024L * 1_024 * 1_024
        for ((total, available, expected) in listOf(
            Triple(6 * gib - 1, 3 * gib, false), Triple(6 * gib, 3 * gib - 1, false),
            Triple(6 * gib, 3 * gib, true), Triple(8 * gib, 4 * gib, true),
        )) {
            shadowOf(manager).setMemoryInfo(ActivityManager.MemoryInfo().apply {
                totalMem = total
                availMem = available
            })
            assertEquals(expected, hasCpuCapacity(RuntimeEnvironment.getApplication()))
        }
    }

    @Test
    fun `microphone initialization failures allow a later start`() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.cancel()
        var minBuffer = 0
        val capture = AudioCapture(120) { minBuffer }
        try {
            for (buffer in listOf(0, AudioRecord.ERROR_BAD_VALUE)) {
                minBuffer = buffer
                assertFailsWith<IllegalStateException> { capture.start(context, scope) {} }
                assertFalse(capture.isRunning)
            }
            minBuffer = 8_000
            shadowOf(context).denyPermissions(Manifest.permission.RECORD_AUDIO)
            assertFailsWith<IllegalStateException> { capture.start(context, scope) {} }
            assertFalse(capture.isRunning)
            shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
            capture.start(context, scope) {}
            assertTrue(capture.isRunning)
            assertFailsWith<IllegalStateException> { capture.start(context, scope) {} }
        } finally {
            capture.cancel()
            scope.cancel()
        }
        assertFalse(capture.isRunning)
    }
}
