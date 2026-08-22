package cz.localvoice.app

import android.text.InputType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldTargetTest {
    private val text = FieldFacts(
        packageName = "com.example.notes",
        windowId = 7,
        viewId = "editor",
        left = 10,
        top = 20,
        right = 400,
        bottom = 220,
        text = "Ahoj",
        selectionStart = 4,
        selectionEnd = 4,
        editable = true,
        enabled = true,
        visible = true,
        password = false,
        inputType = InputType.TYPE_CLASS_TEXT,
    )

    @Test
    fun supportsPlainTextTarget() {
        assertTrue(FieldTargetPolicy.isSupported(text, emptySet()))
    }

    @Test
    fun rejectsSensitiveInputTypes() {
        assertFalse(FieldTargetPolicy.isSupported(text.copy(password = true), emptySet()))
        assertFalse(
            FieldTargetPolicy.isSupported(
                text.copy(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
                emptySet(),
            ),
        )
        assertFalse(
            FieldTargetPolicy.isSupported(
                text.copy(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
                emptySet(),
            ),
        )
        assertFalse(
            FieldTargetPolicy.isSupported(
                text.copy(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD),
                emptySet(),
            ),
        )
        assertFalse(FieldTargetPolicy.isSupported(text.copy(inputType = InputType.TYPE_CLASS_NUMBER), emptySet()))
        assertFalse(FieldTargetPolicy.isSupported(text.copy(inputType = InputType.TYPE_CLASS_PHONE), emptySet()))
    }

    @Test
    fun rejectsUnavailableOrExcludedTarget() {
        assertFalse(FieldTargetPolicy.isSupported(text.copy(editable = false), emptySet()))
        assertFalse(FieldTargetPolicy.isSupported(text.copy(enabled = false), emptySet()))
        assertFalse(FieldTargetPolicy.isSupported(text.copy(visible = false), emptySet()))
        assertFalse(FieldTargetPolicy.isSupported(text, setOf("com.example.notes")))
    }

    @Test
    fun invalidatesChangedTarget() {
        val snapshot = FieldSnapshot.from(text)

        assertTrue(FieldTargetPolicy.isFresh(snapshot, text))
        assertFalse(FieldTargetPolicy.isFresh(snapshot, text.copy(packageName = "com.other")))
        assertFalse(FieldTargetPolicy.isFresh(snapshot, text.copy(windowId = 8)))
        assertFalse(FieldTargetPolicy.isFresh(snapshot, text.copy(viewId = "subject")))
        assertFalse(FieldTargetPolicy.isFresh(snapshot, text.copy(text = "Ahoj!")))
        assertFalse(FieldTargetPolicy.isFresh(snapshot, text.copy(selectionStart = 0, selectionEnd = 0)))
    }

    @Test
    fun rejectsInvalidSnapshotSelection() {
        assertFailsWith<IllegalArgumentException> {
            FieldSnapshot.from(text.copy(selectionStart = 5, selectionEnd = 5))
        }
        assertFailsWith<IllegalArgumentException> {
            FieldSnapshot.from(text.copy(selectionStart = 3, selectionEnd = 2))
        }
    }
}
