package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextEditorTest {
    @Test
    fun insertsAtCursorWithoutReplacingOtherText() {
        val result = TextEditor.apply(
            original = "Ahoj světe",
            selectionStart = 5,
            selectionEnd = 5,
            plan = EditPlan(EditAction.INSERT, "krásný ", "dictation"),
        )

        assertEquals("Ahoj krásný světe", result?.text)
        assertEquals(12, result?.cursor)
    }

    @Test
    fun replacesOnlyExplicitSelection() {
        val result = TextEditor.apply(
            original = "Přijdu v pět.",
            selectionStart = 7,
            selectionEnd = 12,
            plan = EditPlan(EditAction.REPLACE_SELECTION, "v šest", "correction"),
        )

        assertEquals("Přijdu v šest.", result?.text)
    }

    @Test
    fun blocksInsertWhenSelectionWouldBeDestroyed() {
        assertNull(
            TextEditor.apply(
                original = "Citlivý text",
                selectionStart = 0,
                selectionEnd = 7,
                plan = EditPlan(EditAction.INSERT, "Nový", "unsafe"),
            ),
        )
    }

    @Test
    fun blocksResultAboveFieldLimit() {
        assertNull(
            TextEditor.apply(
                original = "a".repeat(MAX_FIELD_CHARS),
                selectionStart = MAX_FIELD_CHARS,
                selectionEnd = MAX_FIELD_CHARS,
                plan = EditPlan(EditAction.INSERT, "b", "too long"),
            ),
        )
    }
}
