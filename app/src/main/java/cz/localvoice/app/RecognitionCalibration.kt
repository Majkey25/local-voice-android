package cz.localvoice.app

import java.util.Locale

data class CalibrationReport(
    val baselineAccuracyPercent: Int,
    val correctedAccuracyPercent: Int,
    val suggestions: List<DictionaryEntry>,
)

object RecognitionCalibration {
    fun analyze(reference: String, transcript: String): CalibrationReport {
        val expected = words(reference)
        val actual = words(transcript)
        require(expected.isNotEmpty() && actual.isNotEmpty()) { "Calibration text is empty" }
        val baseline = matrix(expected.map(Word::normalized), actual.map(Word::normalized))
        val suggestions = substitutions(expected, actual, baseline)
            .distinctBy(DictionaryEntry::spoken)
            .take(MAX_SUGGESTIONS)
        val corrected = words(Personalization.applyDictionary(transcript, suggestions))
        val correctedDistance = matrix(
            expected.map(Word::normalized),
            corrected.map(Word::normalized),
        ).last().last()
        return CalibrationReport(
            baselineAccuracyPercent = accuracy(baseline.last().last(), expected.size),
            correctedAccuracyPercent = accuracy(correctedDistance, expected.size),
            suggestions = suggestions,
        )
    }

    private fun substitutions(
        expected: List<Word>,
        actual: List<Word>,
        distance: Array<IntArray>,
    ): List<DictionaryEntry> = buildList {
        var row = expected.size
        var column = actual.size
        while (row > 0 || column > 0) {
            if (row > 0 && column > 0 && expected[row - 1].normalized == actual[column - 1].normalized) {
                row--
                column--
            } else if (row > 0 && column > 0 &&
                distance[row][column] == distance[row - 1][column - 1] + 1
            ) {
                add(DictionaryEntry(actual[column - 1].normalized, expected[row - 1].raw))
                row--
                column--
            } else if (row > 0 && distance[row][column] == distance[row - 1][column] + 1) {
                row--
            } else {
                column--
            }
        }
    }.asReversed()

    private fun matrix(expected: List<String>, actual: List<String>): Array<IntArray> {
        val result = Array(expected.size + 1) { IntArray(actual.size + 1) }
        for (row in result.indices) result[row][0] = row
        for (column in result[0].indices) result[0][column] = column
        for (row in 1..expected.size) for (column in 1..actual.size) {
            val substitution = if (expected[row - 1] == actual[column - 1]) 0 else 1
            result[row][column] = minOf(
                result[row - 1][column] + 1,
                result[row][column - 1] + 1,
                result[row - 1][column - 1] + substitution,
            )
        }
        return result
    }

    private fun words(text: String): List<Word> = WORD.findAll(text).map {
        Word(it.value, it.value.lowercase(Locale.ROOT))
    }.toList()

    private fun accuracy(distance: Int, words: Int): Int =
        ((1.0 - distance.toDouble() / words).coerceIn(0.0, 1.0) * 100).toInt()

    private data class Word(val raw: String, val normalized: String)

    private val WORD = Regex("""[\p{L}\p{N}]+(?:['’\-][\p{L}\p{N}]+)*""")
    private const val MAX_SUGGESTIONS = 24
}
