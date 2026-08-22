package cz.localvoice.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class DictionaryEntry(val spoken: String, val written: String)

data class TextSnippet(val trigger: String, val text: String)

enum class CleanupLevel { RAW, LIGHT, POLISHED }

object Personalization {
    fun upsertDictionary(entries: List<DictionaryEntry>, entry: DictionaryEntry): List<DictionaryEntry> {
        val normalized = normalize(entry)
        val result = entries.map(::normalize).filterNot { it.spoken == normalized.spoken } + normalized
        require(result.size <= MAX_DICTIONARY_ENTRIES) { "Dictionary is full" }
        return result
    }

    fun upsertSnippets(entries: List<TextSnippet>, entry: TextSnippet): List<TextSnippet> {
        val normalized = normalize(entry)
        val result = entries.map(::normalize).filterNot { it.trigger == normalized.trigger } + normalized
        require(result.size <= MAX_SNIPPETS) { "Snippet list is full" }
        return result
    }

    fun expandSnippet(transcript: String, snippets: List<TextSnippet>): String? {
        val trigger = transcript.trim().trimEnd('.', '!', '?').trim().lowercase(Locale.ROOT)
        return snippets.firstOrNull { normalize(it).trigger == trigger }?.text
    }

    fun applyDictionary(text: String, entries: List<DictionaryEntry>): String {
        val bySpoken = entries.map(::normalize).associateBy(DictionaryEntry::spoken)
        if (bySpoken.isEmpty()) return text
        val alternatives = bySpoken.keys
            .sortedByDescending(String::length)
            .joinToString("|") { Regex.escape(it) }
        val pattern = Regex(
            "(?<![\\p{L}\\p{N}])(?:$alternatives)(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE,
        )
        return pattern.replace(text) { match ->
            bySpoken[match.value.lowercase(Locale.ROOT)]?.written ?: match.value
        }
    }

    fun encodeDictionary(entries: List<DictionaryEntry>): String = JSONArray().apply {
        entries.map(::normalize).forEach { entry ->
            put(JSONObject().put("spoken", entry.spoken).put("written", entry.written))
        }
    }.toString()

    fun decodeDictionary(value: String): List<DictionaryEntry> = runCatching {
        val array = JSONArray(value)
        buildList {
            for (index in 0 until minOf(array.length(), MAX_DICTIONARY_ENTRIES)) {
                val item = array.getJSONObject(index)
                add(normalize(DictionaryEntry(item.getString("spoken"), item.getString("written"))))
            }
        }
    }.getOrDefault(emptyList())

    fun encodeSnippets(entries: List<TextSnippet>): String = JSONArray().apply {
        entries.map(::normalize).forEach { entry ->
            put(JSONObject().put("trigger", entry.trigger).put("text", entry.text))
        }
    }.toString()

    fun decodeSnippets(value: String): List<TextSnippet> = runCatching {
        val array = JSONArray(value)
        buildList {
            for (index in 0 until minOf(array.length(), MAX_SNIPPETS)) {
                val item = array.getJSONObject(index)
                add(normalize(TextSnippet(item.getString("trigger"), item.getString("text"))))
            }
        }
    }.getOrDefault(emptyList())

    private fun normalize(entry: DictionaryEntry): DictionaryEntry {
        val spoken = entry.spoken.trim().lowercase(Locale.ROOT)
        val written = entry.written.trim()
        require(spoken.isNotEmpty() && spoken.length <= 100) { "Invalid spoken form" }
        require(written.isNotEmpty() && written.length <= 200) { "Invalid written form" }
        return DictionaryEntry(spoken, written)
    }

    private fun normalize(entry: TextSnippet): TextSnippet {
        val trigger = entry.trigger.trim().lowercase(Locale.ROOT)
        val text = entry.text.trim()
        require(trigger.isNotEmpty() && trigger.length <= 60) { "Invalid snippet trigger" }
        require(text.isNotEmpty() && text.length <= 4_000) { "Invalid snippet text" }
        return TextSnippet(trigger, text)
    }

    private const val MAX_DICTIONARY_ENTRIES = 500
    private const val MAX_SNIPPETS = 100
}
