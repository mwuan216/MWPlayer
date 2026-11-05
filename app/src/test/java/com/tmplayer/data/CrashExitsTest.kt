package com.tmplayer.data

import io.sentry.Hint
import io.sentry.NoOpLogger
import io.sentry.SentryEvent
import io.sentry.android.core.AnrV2Integration
import io.sentry.android.core.TombstoneIntegration
import io.sentry.protocol.DebugImage
import io.sentry.protocol.DebugMeta
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import io.sentry.protocol.SentryThread
import io.sentry.util.HintUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashExitsTest {

    @get:Rule
    val dir = TemporaryFolder()

    @Test
    fun `the exit's own moment is read from the ANR and tombstone hints, and from nothing else`() {
        val anr = HintUtils.createWithTypeCheckHint(AnrV2Integration.AnrV2Hint(0, NoOpLogger.getInstance(), 1_000L, true, false))
        val native = HintUtils.createWithTypeCheckHint(TombstoneIntegration.TombstoneHint(0, NoOpLogger.getInstance(), 2_000L, true))
        assertEquals(1_000L, CrashExits.exitTime(anr))
        assertEquals(2_000L, CrashExits.exitTime(native))
        assertNull(CrashExits.exitTime(Hint()))
        assertNull(CrashExits.exitTime(null))
    }

    @Test
    fun `a declined exit is never sent, switch or no switch, and a later one still can be`() {
        val answers = ExitAnswers(dir.root.resolve("crash/exits.properties"))
        answers.markDeclined(5_000L)

        // Read again from the disk, as the next start would.
        val next = ExitAnswers(dir.root.resolve("crash/exits.properties"))
        assertEquals(5_000L, next.declinedUpTo)
        assertEquals("declining counts as asked", 5_000L, next.askedUpTo)

        // The switch turned on later: what was declined, and anything before it, stays unsent.
        assertFalse(CrashExits.allows(5_000L, next.declinedUpTo, accepted = null, enabled = true))
        assertFalse(CrashExits.allows(4_000L, next.declinedUpTo, accepted = null, enabled = true))
        // Not even if it were somehow the one agreed to.
        assertFalse(CrashExits.allows(5_000L, next.declinedUpTo, accepted = 5_000L, enabled = false))
        // A newer exit goes with the switch on, and with it off only when agreed to.
        assertTrue(CrashExits.allows(6_000L, next.declinedUpTo, accepted = null, enabled = true))
        assertFalse(CrashExits.allows(6_000L, next.declinedUpTo, accepted = null, enabled = false))
        assertTrue(CrashExits.allows(6_000L, next.declinedUpTo, accepted = 6_000L, enabled = false))
        assertFalse("only the exit agreed to", CrashExits.allows(7_000L, next.declinedUpTo, accepted = 6_000L, enabled = false))
    }

    @Test
    fun `sending marks an exit asked without declining it, and nothing moves back`() {
        val file = dir.root.resolve("crash/exits.properties")
        val answers = ExitAnswers(file)
        assertEquals(Long.MIN_VALUE, answers.askedUpTo)
        assertEquals(Long.MIN_VALUE, answers.declinedUpTo)

        answers.markAsked(8_000L)
        answers.markAsked(7_000L)
        answers.markDeclined(3_000L)
        val read = ExitAnswers(file)
        assertEquals(8_000L, read.askedUpTo)
        assertEquals(3_000L, read.declinedUpTo)
        assertTrue(CrashExits.allows(8_000L, read.declinedUpTo, accepted = null, enabled = true))
    }

    @Test
    fun `a native crash report keeps its debug images and thread names, and loses registers and paths`() {
        val event = SentryEvent().apply {
            message = Message().apply { formatted = "Abort message: '/storage/emulated/0/Movies/Holiday.mkv' failed" }
            exceptions = listOf(SentryException().apply {
                type = "SIGSEGV"
                value = "Segfault"
                stacktrace = SentryStackTrace(listOf(SentryStackFrame().apply { `package` = "libtdjni.so" })).apply {
                    registers = mapOf("x0" to "0x0")
                }
            })
            threads = listOf(SentryThread().apply {
                name = "TDLib thread"
                stacktrace = SentryStackTrace(listOf(SentryStackFrame())).apply { registers = mapOf("pc" to "0x1") }
            })
            debugMeta = DebugMeta().apply {
                images = listOf(DebugImage().apply { type = "elf"; codeFile = "libtdjni.so"; debugId = "abc" })
            }
        }

        val scrubbed = CrashReportScrubber.scrub(event)
        assertNull(scrubbed.exceptions!!.single().stacktrace!!.registers)
        assertNull(scrubbed.threads!!.single().stacktrace!!.registers)
        assertEquals("TDLib thread", scrubbed.threads!!.single().name)
        assertEquals("abc", scrubbed.debugMeta!!.images!!.single().debugId)
        assertFalse(scrubbed.message!!.formatted!!.contains("Holiday"))
    }
}
