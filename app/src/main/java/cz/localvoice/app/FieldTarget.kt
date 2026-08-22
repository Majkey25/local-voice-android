package cz.localvoice.app

import android.text.InputType

data class FieldFacts(
    val packageName: String,
    val windowId: Int,
    val viewId: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val editable: Boolean,
    val enabled: Boolean,
    val visible: Boolean,
    val password: Boolean,
    val inputType: Int,
)

data class FieldSnapshot(
    val facts: FieldFacts,
    val selectedText: String,
) {
    companion object {
        fun from(facts: FieldFacts): FieldSnapshot {
            require(facts.text.length <= MAX_FIELD_CHARS) { "Field is too long" }
            require(facts.selectionStart in 0..facts.selectionEnd && facts.selectionEnd <= facts.text.length) {
                "Invalid field selection"
            }
            return FieldSnapshot(
                facts = facts,
                selectedText = facts.text.substring(facts.selectionStart, facts.selectionEnd),
            )
        }
    }
}

object FieldTargetPolicy {
    fun isSupported(facts: FieldFacts, excludedPackages: Set<String>): Boolean {
        val inputClass = facts.inputType and InputType.TYPE_MASK_CLASS
        val variation = facts.inputType and InputType.TYPE_MASK_VARIATION
        val passwordVariation = inputClass == InputType.TYPE_CLASS_TEXT && when (variation) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            -> true
            else -> false
        }
        return facts.editable && facts.enabled && facts.visible && !facts.password &&
            !passwordVariation && facts.packageName !in excludedPackages &&
            inputClass == InputType.TYPE_CLASS_TEXT
    }

    fun isFresh(snapshot: FieldSnapshot, current: FieldFacts): Boolean = snapshot.facts == current
}

internal const val MAX_FIELD_CHARS = 100_000
