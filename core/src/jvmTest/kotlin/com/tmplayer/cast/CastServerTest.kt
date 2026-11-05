package com.tmplayer.cast

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket

class CastServerTest {

    private val loopback = InetAddress.getLoopbackAddress()
    private val server = CastServer(loopback).apply { start() }
    private val bytes = ByteArray(300_000) { (it * 31 + 7).toByte() }

    @After
    fun stop() = server.stop()

    private class Reply(val code: Int, val headers: Map<String, String>, val body: ByteArray)

    private fun request(path: String, vararg headers: String, method: String = "GET"): Reply =
        Socket(loopback, server.boundPort).use { s ->
            val head = buildString {
                append("$method $path HTTP/1.1\r\nHost: test\r\nConnection: close\r\n")
                headers.forEach { append(it).append("\r\n") }
                append("\r\n")
            }
            s.getOutputStream().write(head.toByteArray())
            s.getOutputStream().flush()
            readReply(s.getInputStream(), method == "HEAD")
        }

    private fun readReply(input: InputStream, head: Boolean): Reply {
        fun line(): String {
            val sb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0 || b == '\n'.code) break
                if (b != '\r'.code) sb.append(b.toChar())
            }
            return sb.toString()
        }
        val status = line().split(' ')[1].toInt()
        val headers = HashMap<String, String>()
        while (true) {
            val h = line()
            if (h.isEmpty()) break
            headers[h.substringBefore(':').trim().lowercase()] = h.substringAfter(':').trim()
        }
        val length = headers["content-length"]?.toInt()
        val body = if (head) ByteArray(0) else if (length != null) {
            val out = ByteArray(length)
            var at = 0
            while (at < length) {
                val n = input.read(out, at, length - at)
                if (n < 0) break
                at += n
            }
            out
        } else {
            val out = ByteArrayOutputStream()
            input.copyTo(out)
            out.toByteArray()
        }
        return Reply(status, headers, body)
    }

    private fun path(name: String) = "/${server.token}/$name"

    @Test
    fun `serves the whole file with ranges offered`() {
        server.publish("video.mp4", CastResource.Ranged(BytesCastSource(bytes), "video/mp4"))
        val r = request(path("video.mp4"))
        assertEquals(200, r.code)
        assertEquals("bytes", r.headers["accept-ranges"])
        assertEquals("video/mp4", r.headers["content-type"])
        assertEquals("*", r.headers["access-control-allow-origin"])
        assertArrayEquals(bytes, r.body)
    }

    @Test
    fun `answers a range with 206 and exactly those bytes`() {
        server.publish("video.mp4", CastResource.Ranged(BytesCastSource(bytes), "video/mp4"))
        val r = request(path("video.mp4"), "Range: bytes=1000-1999")
        assertEquals(206, r.code)
        assertEquals("bytes 1000-1999/300000", r.headers["content-range"])
        assertArrayEquals(bytes.copyOfRange(1000, 2000), r.body)
    }

    @Test
    fun `an open range and a suffix range reach the end`() {
        server.publish("video.mp4", CastResource.Ranged(BytesCastSource(bytes), "video/mp4"))
        val open = request(path("video.mp4"), "Range: bytes=299000-")
        assertEquals(206, open.code)
        assertArrayEquals(bytes.copyOfRange(299_000, 300_000), open.body)
        val suffix = request(path("video.mp4"), "Range: bytes=-500")
        assertEquals("bytes 299500-299999/300000", suffix.headers["content-range"])
        assertArrayEquals(bytes.copyOfRange(299_500, 300_000), suffix.body)
    }

    @Test
    fun `a range past the end is 416`() {
        server.publish("video.mp4", CastResource.Ranged(BytesCastSource(bytes), "video/mp4"))
        val r = request(path("video.mp4"), "Range: bytes=300000-")
        assertEquals(416, r.code)
        assertEquals("bytes */300000", r.headers["content-range"])
    }

    @Test
    fun `head answers the length without a body`() {
        server.publish("video.mp4", CastResource.Ranged(BytesCastSource(bytes), "video/mp4"))
        val r = request(path("video.mp4"), method = "HEAD")
        assertEquals(200, r.code)
        assertEquals("300000", r.headers["content-length"])
    }

    @Test
    fun `without the session token nothing is found`() {
        server.publish("video.mp4", CastResource.Ranged(BytesCastSource(bytes), "video/mp4"))
        assertEquals(404, request("/video.mp4").code)
        assertEquals(404, request("/wrongtoken/video.mp4").code)
        assertEquals(404, request(path("other.mp4")).code)
    }

    @Test
    fun `tokens are long and different every session`() {
        val a = CastServer.newToken()
        val b = CastServer.newToken()
        assertTrue(a.length >= 24)
        assertTrue(a != b)
    }

    @Test
    fun `subtitles are served as text with cors`() {
        server.publish("subs.vtt", CastResource.Text("WEBVTT\n\n", WebVtt.MIME))
        val r = request(path("subs.vtt"))
        assertEquals(200, r.code)
        assertEquals("text/vtt", r.headers["content-type"])
        assertEquals("*", r.headers["access-control-allow-origin"])
        assertEquals("WEBVTT\n\n", String(r.body))
    }

    @Test
    fun `a preflight is answered`() {
        server.publish("subs.vtt", CastResource.Text("WEBVTT\n\n", WebVtt.MIME))
        val r = request(path("subs.vtt"), method = "OPTIONS")
        assertEquals(204, r.code)
        assertTrue(r.headers["access-control-allow-headers"]!!.contains("Range"))
    }

    @Test
    fun `a stream gets its query and ends with the connection`() {
        server.publish("live.mp4", CastResource.Stream("video/mp4") { query, out -> out.write("from ${query["t"]}".toByteArray()) })
        val r = request(path("live.mp4") + "?t=4000")
        assertEquals(200, r.code)
        assertNull(r.headers["content-length"])
        assertEquals("from 4000", String(r.body))
    }

    @Test
    fun `keeps the connection open for the next range`() {
        server.publish("video.mp4", CastResource.Ranged(BytesCastSource(bytes), "video/mp4"))
        Socket(loopback, server.boundPort).use { s ->
            val out = s.getOutputStream()
            out.write("GET ${path("video.mp4")} HTTP/1.1\r\nRange: bytes=0-9\r\n\r\n".toByteArray())
            out.flush()
            val first = readReply(s.getInputStream(), head = false)
            out.write("GET ${path("video.mp4")} HTTP/1.1\r\nRange: bytes=10-19\r\n\r\n".toByteArray())
            out.flush()
            val second = readReply(s.getInputStream(), head = false)
            assertArrayEquals(bytes.copyOfRange(0, 10), first.body)
            assertArrayEquals(bytes.copyOfRange(10, 20), second.body)
        }
    }

    @Test
    fun `ranges across split pieces read every piece`() {
        // Three pieces laid end to end, as a video cut into .001, .002, .003 is played.
        val pieces = listOf(bytes.copyOfRange(0, 100_000), bytes.copyOfRange(100_000, 100_001), bytes.copyOfRange(100_001, 300_000))
        val joined = JoinedTestSource(pieces.map(::BytesCastSource))
        server.publish("joined.mkv", CastResource.Ranged(joined, "video/x-matroska"))
        val across = request(path("joined.mkv"), "Range: bytes=99990-100010")
        assertEquals("bytes 99990-100010/300000", across.headers["content-range"])
        assertArrayEquals(bytes.copyOfRange(99_990, 100_011), across.body)
        assertArrayEquals(bytes, request(path("joined.mkv")).body)
    }

    /** The joining the platforms do (JoinedDataSource, JoinedStreamBytes), over [PieceMap]. */
    private class JoinedTestSource(private val pieces: List<CastSource>) : CastSource {
        private val map = com.tmplayer.player.PieceMap(pieces.map { it.length })
        override val length: Long get() = map.total
        override fun open(position: Long): CastReader {
            var at = position
            var reader: CastReader? = null
            var readerIndex = -1
            return object : CastReader {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    val spot = map.locate(at) ?: return -1
                    if (readerIndex != spot.index) {
                        reader?.close()
                        reader = pieces[spot.index].open(spot.offset)
                        readerIndex = spot.index
                    }
                    val wanted = minOf(length.toLong(), map.sizes[spot.index] - spot.offset).toInt()
                    val n = reader!!.read(buffer, offset, wanted)
                    if (n > 0) at += n
                    return n
                }

                override fun close() {
                    reader?.close()
                }
            }
        }
    }

    @Test
    fun `parses ranges`() {
        assertEquals(ByteRange.Span(0, 99), ByteRange.parse("bytes=0-99", 1000))
        assertEquals(ByteRange.Span(900, 999), ByteRange.parse("bytes=900-5000", 1000))
        assertEquals(ByteRange.Span(990, 999), ByteRange.parse("bytes=-10", 1000))
        assertEquals(ByteRange.Unsatisfiable, ByteRange.parse("bytes=1000-", 1000))
        assertEquals(ByteRange.Unsatisfiable, ByteRange.parse("bytes=50-10", 1000))
        assertNull(ByteRange.parse("bytes=0-1,5-6", 1000))
        assertNull(ByteRange.parse("items=0-1", 1000))
    }

    @Test
    fun `only local network addresses may ask`() {
        assertTrue(CastServer.isLocalNetwork(InetAddress.getByName("192.168.1.20")))
        assertTrue(CastServer.isLocalNetwork(InetAddress.getByName("10.0.0.5")))
        assertTrue(CastServer.isLocalNetwork(InetAddress.getByName("172.16.3.4")))
        assertTrue(!CastServer.isLocalNetwork(InetAddress.getByName("8.8.8.8")))
    }

    @Test
    fun `picks the address on the receiver's subnet`() {
        val wifi = LanAddress.Candidate("wlan0", InetAddress.getByName("192.168.1.10"), 24)
        val vpn = LanAddress.Candidate("tun0", InetAddress.getByName("10.8.0.2"), 24)
        val docker = LanAddress.Candidate("docker0", InetAddress.getByName("172.17.0.1"), 16)
        assertEquals(wifi.address, LanAddress.pick(listOf(vpn, docker, wifi), InetAddress.getByName("192.168.1.55")))
        assertEquals(wifi.address, LanAddress.pick(listOf(docker, wifi), null))
    }
}
