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

    @Test
    fun `failed initialization keeps its error when closing an uninitialized engine also fails`() {
        for (cpuOnly in listOf(false, true)) {
            val created = mutableListOf<TestEngine>()
            val error = IllegalStateException("CPU initialization failed")
            val closeError = IllegalStateException("Engine is not initialized.")
            val thrown = assertFailsWith<IllegalStateException> {
                initializeSemanticEngine(cpuOnly, { true }, {
                    TestEngine(it, closeError).also(created::add)
                }) {
                    if (it.cpu) throw error else error("GPU initialization failed")
                }
            }
            assertSame(error, thrown)
            assertEquals(listOf(closeError), thrown.suppressed.toList())
            assertEquals(if (cpuOnly) listOf(true) else listOf(false, true), created.map { it.cpu })
            assertTrue(created.none { it.closed })
        }
    }

    @Test
    fun `constructor failure propagates without initialization or fallback`() {
        for (cpuOnly in listOf(false, true)) {
            val requested = mutableListOf<Boolean>()
            val error = IllegalStateException("Engine construction failed")
            val thrown = assertFailsWith<IllegalStateException> {
                initializeSemanticEngine<TestEngine>(cpuOnly, { true }, {
                    requested += it
                    throw error
                }) { error("Unconstructed engine must not initialize") }
            }
            assertSame(error, thrown)
            assertEquals(listOf(cpuOnly), requested)
        }
    }

    private class TestEngine(val cpu: Boolean, val closeError: Throwable? = null) : AutoCloseable {
        var closed = false
        override fun close() {
            closeError?.let { throw it }
            closed = true
        }
    }
}
