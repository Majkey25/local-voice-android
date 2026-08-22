package cz.localvoice.app

data class TextEditResult(val text: String, val cursor: Int)

object TextEditor {
    fun apply(
        original: String,
        selectionStart: Int,
        selectionEnd: Int,
        plan: EditPlan,
    ): TextEditResult? {
        val start = selectionStart.coerceIn(0, original.length)
        val end = selectionEnd.coerceIn(start, original.length)
        val hasSelection = end > start
        if (plan.action == EditAction.COPY_ONLY) return null
        if (plan.action == EditAction.REPLACE_SELECTION && !hasSelection) return null
        if (plan.action == EditAction.INSERT && hasSelection) return null
        if (original.length - (end - start) + plan.text.length > MAX_FIELD_CHARS) return null

        val text = original.replaceRange(start, end, plan.text)
        return TextEditResult(text, start + plan.text.length)
    }
}
