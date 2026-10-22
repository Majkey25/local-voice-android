package cz.localvoice.app

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ClipboardFallbackTest {
    @Test
    fun fallbackKeepsClipboardPrivateUntilExplicitCopyAndMarksItSensitive() {
        val controller = Robolectric.buildService(VoiceAccessibilityService::class.java).create()
        val service = controller.get()
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("existing", "keep me"))
        try {
            service.offerCopy("private transcript")
            assertEquals("keep me", clipboard.primaryClip?.getItemAt(0)?.text)
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("keep me", clipboard.primaryClip?.getItemAt(0)?.text)

            service.offerCopy("approved transcript")
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("approved transcript", clipboard.primaryClip?.getItemAt(0)?.text)
            assertTrue(clipboard.primaryClip?.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun pendingCopyExpiresAndIsDismissedWhenServiceIsInterrupted() {
        val controller = Robolectric.buildService(VoiceAccessibilityService::class.java).create()
        val service = controller.get()
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("existing", "keep me"))
        try {
            service.offerCopy("private transcript")
            val expired = ShadowAlertDialog.getLatestAlertDialog()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1))
            assertFalse(expired.isShowing)
            assertEquals("keep me", clipboard.primaryClip?.getItemAt(0)?.text)

            service.offerCopy("another transcript")
            val interrupted = ShadowAlertDialog.getLatestAlertDialog()
            service.onInterrupt()
            assertFalse(interrupted.isShowing)
            assertEquals("keep me", clipboard.primaryClip?.getItemAt(0)?.text)
        } finally {
            controller.destroy()
        }
    }
}
