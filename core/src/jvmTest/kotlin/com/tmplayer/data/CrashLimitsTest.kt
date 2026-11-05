package com.tmplayer.data

import io.sentry.SentryEvent
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLimitsTest {

    @get:Rule
    val dir = TemporaryFolder()

    private var now = 1_760_000_000_000L

    private fun limits() = CrashLimits(dir.root.resolve("crash/limits.json")) { now }

    /** A crash thrown from [method] of the app's own code, under a frame of the framework's. */
    private fun crash(method: String, type: String = "IllegalStateException", line: Int = 42) = SentryEvent().apply {
        exceptions = listOf(SentryException().apply {
            this.type = type
            module = "java.lang"
            stacktrace = SentryStackTrace(listOf(
                SentryStackFrame().apply { module = "android.os.Looper"; function = "loop"; isInApp = false },
                SentryStackFrame().apply { module = "com.tmplayer.Home"; function = method; lineno = line; isInApp = true },
                SentryStackFrame().apply { module = "kotlin.PreconditionsKt"; function = "check"; isInApp = false },
            ))
        })
    }

    @Test
    fun `the same crash goes once a day, and again the next day`() {
        assertTrue(limits().admit(crash("build")))
        // A new instance, as the next start would make: the count is on the disk.
        assertFalse("the same crash the same day", limits().admit(crash("build")))
        assertTrue("another crash still goes", limits().admit(crash("draw")))

        now += 24L * 60 * 60 * 1000
        assertTrue(limits().admit(crash("build")))
    }

    @Test
    fun `a burst of twelve sends ten`() {
        val limits = limits()
        val sent = (1..12).map { limits.admit(crash("method$it")) }
        assertEquals(List(10) { true } + listOf(false, false), sent)

        now += 24L * 60 * 60 * 1000
        assertTrue("a new day, a new ten", limits.admit(crash("method11")))
    }

    @Test
    fun `the same crash is its type and the top frame of the app's own code`() {
        assertEquals("java.lang IllegalStateException com.tmplayer.Home:build:42", CrashLimits.fingerprint(crash("build")))
        assertNotEquals(CrashLimits.fingerprint(crash("build")), CrashLimits.fingerprint(crash("build", type = "NullPointerException")))
        assertNotEquals(CrashLimits.fingerprint(crash("build")), CrashLimits.fingerprint(crash("build", line = 43)))
        assertEquals(null, CrashLimits.fingerprint(SentryEvent()))
    }

    @Test
    fun `a broken count file starts the day afresh`() {
        val file = dir.root.resolve("crash/limits.json")
        file.parentFile.mkdirs()
        file.writeText("{ half")
        assertTrue(limits().admit(crash("build")))
    }
}
