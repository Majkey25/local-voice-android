package cz.localvoice.app

import org.json.JSONObject

enum class EditAction(val wireName: String) {
    INSERT("insert"),
    REPLACE_SELECTION("replace_selection"),
    COPY_ONLY("copy_only"),
}

data class EditPlan(
    val action: EditAction,
    val text: String,
    val reason: String,
) {
    companion object {
        fun parse(response: String, hasSelection: Boolean): EditPlan {
            val start = response.indexOf('{')
            val end = response.lastIndexOf('}')
            require(start >= 0 && end > start) { "Semantic model did not return JSON" }
            val value = JSONObject(response.substring(start, end + 1))
            val action = EditAction.entries.firstOrNull {
                it.wireName == value.getString("action")
            } ?: error("Unknown edit action")
            val text = value.getString("text").trim()
            val reason = value.optString("reason", "semantic edit").take(160)
            require(text.isNotEmpty() && text.length <= 50_000) { "Invalid edit text" }
            require(action != EditAction.REPLACE_SELECTION || hasSelection) {
                "replace_selection requires selected text"
            }
            require(action != EditAction.INSERT || !hasSelection) {
                "insert cannot overwrite selected text"
            }
            return EditPlan(action, text, reason)
        }

        fun rawFallback(text: String, hasSelection: Boolean, reason: String): EditPlan {
            val normalized = text.trim()
            require(normalized.isNotEmpty() && normalized.length <= 50_000) { "Invalid fallback text" }
            return EditPlan(
                action = if (hasSelection) EditAction.COPY_ONLY else EditAction.INSERT,
                text = normalized,
                reason = reason.take(160),
            )
        }
    }
}
