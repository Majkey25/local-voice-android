package cz.localvoice.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SemanticInitializationTest {
    @Test
    fun `GPU failure checks CPU capacity before construction and preserves raw fallback`() {
        val created = mutableListOf<TestEngine>()
        val error = IllegalStateException("GPU initialization failed")
        val thrown = assertFailsWith<IllegalStateException> {
            initializeSemanticEngine(false, { false }, { TestEngine(it).also(created::add) }) { throw error }
        }
        assertSame(error, thrown)
        assertEquals(listOf(false), created.map { it.cpu })
        assertTrue(created.single().closed)
        assertEquals("Saved dictation.", EditPlan.rawFallback("Saved dictation.", false, thrown.message!!).text)
    }

    @Test
    fun `GPU failure with CPU capacity closes GPU and returns initialized CPU`() {
        val created = mutableListOf<TestEngine>()
        val engine = initializeSemanticEngine(false, { true }, { TestEngine(it).also(created::add) }) {
            check(it.cpu) { "GPU initialization failed" }
        }
        assertEquals(listOf(false, true), created.map { it.cpu })
        assertTrue(created.first().closed)
        assertSame(created.last(), engine)
        assertFalse(engine.closed)
        engine.close()
    }

    @Test
    fun `failed CPU fallback closes both engines and propagates its error`() {
        val created = mutableListOf<TestEngine>()
        val error = IllegalStateException("CPU initialization failed")
        val thrown = assertFailsWith<IllegalStateException> {
            initializeSemanticEngine(false, { true }, { TestEngine(it).also(created::add) }) {
                if (it.cpu) throw error else error("GPU initialization failed")
            }
        }
        assertSame(error, thrown)
        assertEquals(listOf(false, true), created.map { it.cpu })
        assertTrue(created.all { it.closed })
    }

    @Test
    fun `direct CPU initialization keeps its capacity check and never retries itself`() {
        val created = mutableListOf<TestEngine>()
        assertFailsWith<IllegalArgumentException> {
            initializeSemanticEngine(true, { false }, { TestEngine(it).also(created::add) }) {}
        }
        assertTrue(created.isEmpty())
        assertFailsWith<IllegalStateException> {
            initializeSemanticEngine(true, { true }, { TestEngine(it).also(created::add) }) { error("CPU failed") }
        }
        assertEquals(1, created.size)
        assertTrue(created.single().closed)
    }

    private class TestEngine(val cpu: Boolean) : AutoCloseable {
        var closed = false
        override fun close() { closed = true }
    }
}
