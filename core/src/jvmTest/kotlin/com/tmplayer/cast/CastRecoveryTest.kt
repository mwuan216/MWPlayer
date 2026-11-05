package com.tmplayer.cast

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException

class CastRecoveryTest {

    private val bytes = ByteArray(200_000) { (it * 13 + 5).toByte() }

    /** Time that moves only when the recovery sleeps, so the tests take no real time. */
    private var now = 0L
    private val sleeps = ArrayList<Long>()
    private var reconnects = 0

    private fun recovery(budgetMs: Long = 20_000) = CastRecovery(
        budgetMs = budgetMs,
        reconnect = { reconnects++ },
        clock = { now },
        sleep = { sleeps += it; now += it },
    )

    /**
     * [bytes], except that a read reaching [stallAt] fails as TDLib's wait does, [failures] times
     * (forever when null), each failure taking [failMs] of the clock as the real wait would.
     */
    private inner class StallingSource(
        private val stallAt: Long,
        private val failures: Int?,
        private val failMs: Long = 0,
    ) : CastSource {
        var failed = 0
        var opens = 0
        val askedAt = ArrayList<Long>()

        override val length: Long get() = bytes.size.toLong()

        override fun open(position: Long): CastReader {
            opens++
            var at = position
            return object : CastReader {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (at >= bytes.size) return -1
                    val stalled = failures == null || failed < failures
                    if (stalled && at + length > stallAt) {
                        // Whatever is before the stall comes through first, as from TDLib.
                        if (at < stallAt) {
                            val n = (stallAt - at).toInt()
                            System.arraycopy(bytes, at.toInt(), buffer, offset, n)
                            at += n
                            return n
                        }
                        failed++
                        now += failMs
                        throw IOException("Telegram stopped sending file 2370 at byte $at")
                    }
                    val n = minOf(length, bytes.size - at.toInt())
                    System.arraycopy(bytes, at.toInt(), buffer, offset, n)
                    at += n
                    return n
                }

                override fun close() = Unit
            }
        }

        override fun askAgain(position: Long) {
            askedAt += position
        }
    }

    private fun readAll(source: CastSource, from: Long = 0): ByteArray {
        val out = ByteArrayOutputStream()
        source.open(from).use { r ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = r.read(buf, 0, buf.size)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    @Test
    fun aStallThatEndsIsRiddenOut() {
        val source = StallingSource(stallAt = 100_000, failures = 3)
        val read = readAll(RecoveringCastSource(source, recovery()))
        assertArrayEquals(bytes, read)
        assertEquals(3, source.failed)
        // The first open, then one fresh open per failure, each at the byte it stopped at.
        assertEquals(4, source.opens)
        assertEquals(listOf(100_000L, 100_000L, 100_000L), source.askedAt)
        // Backing off: 0.5 s, then 1 s, then 2 s.
        assertEquals(listOf(500L, 1_000L, 2_000L), sleeps)
        // Telegram's connections reopened once for the stall, not on every try.
        assertEquals(1, reconnects)
    }

    @Test
    fun aStallThatNeverEndsFailsAfterTheBudget() {
        val source = StallingSource(stallAt = 50_000, failures = null)
        val reader = RecoveringCastSource(source, recovery(budgetMs = 20_000)).open(0)
        val buf = ByteArray(64 * 1024)
        assertEquals(50_000, reader.read(buf, 0, buf.size))
        try {
            reader.read(buf, 0, buf.size)
            fail("A stall that never ends must fail the read")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("stopped sending"))
        }
        // It kept trying until the budget was spent, and not much past it.
        assertTrue("gave up at $now", now in 20_000L..24_000L)
        assertTrue(sleeps.all { it <= CastRecovery.MAX_BACKOFF_MS })
        assertTrue(source.failed >= 5)
        assertEquals(1, reconnects)
    }

    @Test
    fun aSlowFailureStillGetsOneMoreTry() {
        // TDLib's own wait takes a minute to give up; the retry after it is not skipped for that.
        val source = StallingSource(stallAt = 10_000, failures = 1, failMs = 60_000)
        assertArrayEquals(bytes, readAll(RecoveringCastSource(source, recovery())))
        assertEquals(1, source.failed)
    }

    @Test
    fun bytesArrivingStartTheCountAgain() {
        // Two separate stalls, each shorter than the budget but together longer: both ridden out.
        var stalls = 0
        val inner = object : CastSource {
            override val length: Long get() = bytes.size.toLong()
            override fun open(position: Long): CastReader {
                var at = position
                return object : CastReader {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (at >= bytes.size) return -1
                        val n = minOf(length, 10_000, bytes.size - at.toInt())
                        if ((at == 40_000L && stalls < 4) || (at == 120_000L && stalls < 8)) {
                            stalls++
                            now += 3_000
                            throw IOException("Telegram stopped sending file 1 at byte $at")
                        }
                        System.arraycopy(bytes, at.toInt(), buffer, offset, n)
                        at += n
                        return n
                    }

                    override fun close() = Unit
                }
            }
        }
        assertArrayEquals(bytes, readAll(RecoveringCastSource(inner, recovery(budgetMs = 20_000))))
        assertEquals(8, stalls)
        assertTrue("both stalls took $now", now > 20_000)
    }

    @Test
    fun theEndOfTheFileIsNotAStall() {
        val inner = object : CastSource {
            override val length: Long get() = 10
            override fun open(position: Long): CastReader = object : CastReader {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw EOFException("Short read")
                override fun close() = Unit
            }
        }
        try {
            RecoveringCastSource(inner, recovery()).open(0).read(ByteArray(4), 0, 4)
            fail("An EOFException goes straight up")
        } catch (_: EOFException) {
        }
        assertTrue(sleeps.isEmpty())
        assertEquals(0, reconnects)
    }

    @Test
    fun theServerHandsOverTheWholeRangeAcrossAStall() {
        val server = CastServer(java.net.InetAddress.getLoopbackAddress()).apply { start() }
        try {
            val source = StallingSource(stallAt = 150_000, failures = 2)
            val url = server.publish("video.mp4", CastResource.Ranged(RecoveringCastSource(source, recovery()), "video/mp4"))
            val body = java.net.URI(url).toURL().openStream().use { it.readBytes() }
            assertArrayEquals(bytes, body)
            assertEquals(2, source.failed)
        } finally {
            server.stop()
        }
    }
}
