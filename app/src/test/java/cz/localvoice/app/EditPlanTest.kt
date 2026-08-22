package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EditPlanTest {
    @Test
    fun parsesJsonAfterQwenThinkingText() {
        val response = """
            <think>short private reasoning</think>
            {"action":"insert","text":"V šest.","reason":"latest correction"}
        """.trimIndent()

        assertEquals("V šest.", EditPlan.parse(response, hasSelection = false).text)
    }

    @Test
    fun rejectsReplacementWithoutSelection() {
        assertFailsWith<IllegalArgumentException> {
            EditPlan.parse(
                """{"action":"replace_selection","text":"Nový","reason":"rewrite"}""",
                hasSelection = false,
            )
        }
    }

    @Test
    fun rawFallbackNeverOverwritesSelection() {
        assertEquals(
            EditAction.COPY_ONLY,
            EditPlan.rawFallback("Původní", hasSelection = true, reason = "Model není dostupný").action,
        )
        assertEquals(
            EditAction.INSERT,
            EditPlan.rawFallback("Původní", hasSelection = false, reason = "Model není dostupný").action,
        )
    }

    @Test
    fun rawFallbackKeepsExplicitBoundedReason() {
        val reason = "x".repeat(200)

        val plan = EditPlan.rawFallback(" Ahoj ", hasSelection = false, reason = reason)

        assertEquals("Ahoj", plan.text)
        assertEquals(160, plan.reason.length)
    }

    @Test
    fun rawFallbackRejectsBlankText() {
        assertFailsWith<IllegalArgumentException> {
            EditPlan.rawFallback("  ", hasSelection = false, reason = "Model není dostupný")
        }
    }
}
