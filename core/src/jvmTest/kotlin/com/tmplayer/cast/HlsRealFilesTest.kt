package com.tmplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * The HLS cast of real Matroska files made with ffmpeg, checked by ffmpeg itself over HTTP from
 * the cast server: the master's length and renditions, every segment decoded end to end with no
 * timestamp complaint, a seek into the middle, each soundtrack, and the subtitles as WebVTT.
 *
 * Runs only when TM_CAST_MEDIA names a folder holding the files (h264aac.mkv: H.264 and AAC;
 * hevcac3.mkv: HEVC, two AC-3 soundtracks and a SubRip track) and ffmpeg is on the path; the
 * playlists and segments are also written under build/hls-real for a look with ffplay.
 */
class HlsRealFilesTest {

    private val dir = System.getenv("TM_CAST_MEDIA")?.let(::File)

    private fun run(vararg args: String): Pair<Int, String> {
        val p = ProcessBuilder(*args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(120, TimeUnit.SECONDS)
        return p.exitValue() to out
    }

    private fun serve(name: String, block: (base: String, hls: MkvHls) -> Unit) {
        val file = dir!!.resolve(name)
        assumeTrue("$file missing", file.exists())
        val source = FileCastSource(file)
        val probe = MediaProbe.of(source, name)
        val audio = CastFormats.hlsAudio(probe.audio, ReceiverCaps.GOOGLE_TV, canConvert = false)
        val hls = MkvHls.prepare(source, probe, audio, audio.firstOrNull()?.number)
        val server = CastServer(InetAddress.getLoopbackAddress(), allowed = { true })
        server.start()
        try {
            server.publish("hls1", CastResource.Tree(hls::resolve))
            val base = server.urlFor("hls1")
            save(name.substringBefore('.'), base, hls)
            block(base, hls)
        } finally {
            server.stop()
        }
    }

    /** Every playlist and segment on the disk, as the receiver would fetch them. */
    private fun save(label: String, base: String, hls: MkvHls) {
        val out = File("build/hls-real/$label").apply { deleteRecursively(); mkdirs() }
        val names = ArrayList<String>()
        names += listOf("master.m3u8", "v.m3u8", "v-init.mp4")
        names += hls.segments.indices.map { "v-$it.m4s" }
        for (r in hls.renditions) {
            names += r.uri
            val prefix = r.uri.removeSuffix(".m3u8")
            if (r.kind == HlsRendition.Kind.Audio) {
                names += "$prefix-init.mp4"
                names += hls.segments.indices.map { "$prefix-$it.m4s" }
            } else {
                names += hls.segments.indices.map { "$prefix-$it.vtt" }
            }
        }
        for (n in names) out.resolve(n).writeBytes(URL("$base/$n").readBytes())
    }

    private fun ffmpegReady(): Boolean = dir != null && runCatching { run("ffprobe", "-version").first == 0 }.getOrDefault(false)

    private fun duration(url: String): Double =
        run("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", url).second.trim().lines().last().toDouble()

    private fun videoPackets(url: String): Int =
        run("ffprobe", "-v", "error", "-select_streams", "v:0", "-count_packets", "-show_entries", "stream=nb_read_packets", "-of", "csv=p=0", url)
            .second.trim().lines().last().trim().toInt()

    /**
     * A seek as a receiver makes one: the init segment and segment [k] alone, fetched and decoded
     * with nothing before them. They must decode, and start at the segment's own time.
     */
    private fun startsAlone(base: String, hls: MkvHls, prefix: String, k: Int) {
        val file = File.createTempFile("seg", ".mp4")
        try {
            file.writeBytes(URL("$base/$prefix-init.mp4").readBytes() + URL("$base/$prefix-$k.m4s").readBytes())
            val log = decodes("-i", file.path)
            assertTrue(log, "error" !in log.lowercase())
            val first = run("ffprobe", "-v", "error", "-show_entries", "packet=pts_time", "-of", "csv=p=0", file.path).second.lines()
                .mapNotNull { it.trim().toDoubleOrNull() }.min()
            assertEquals(hls.segments[k].startNs / 1e9, first, 0.25)
        } finally {
            file.delete()
        }
    }

    private fun decodes(vararg input: String): String {
        val (code, out) = run("ffmpeg", "-nostdin", "-v", "warning", *input, "-f", "null", "-")
        assertEquals(out, 0, code)
        return out
    }

    @Test
    fun `h264 and aac plays end to end and from the middle`() {
        assumeTrue(ffmpegReady())
        serve("h264aac.mkv") { base, hls ->
            val original = dir!!.resolve("h264aac.mkv").path
            assertEquals(duration(original), duration("$base/master.m3u8"), 0.5)
            assertEquals(videoPackets(original), videoPackets("$base/v.m3u8"))
            val log = decodes("-i", "$base/master.m3u8", "-map", "0:v", "-map", "0:a")
            assertTrue(log, "Non-monotonic" !in log && "Non-monotonous" !in log && "error" !in log.lowercase())
            assertTrue(hls.segments.size >= 5)
            startsAlone(base, hls, "v", 3)
            startsAlone(base, hls, "a2", 3)
        }
    }

    @Test
    fun `a file written to a pipe has no cues, so the plan falls back to the rewrap`() {
        assumeTrue(ffmpegReady())
        val file = dir!!.resolve("nocues.mkv")
        assumeTrue(file.exists())
        val source = FileCastSource(file)
        val probe = MediaProbe.of(source, file.name)
        val plan = CastFormats.plan(probe, ReceiverCaps.GOOGLE_TV)
        assertTrue(plan.routes.first() is CastRoute.Hls)
        assertTrue(plan.routes[1] is CastRoute.Remux)
        val failed = runCatching { MkvHls.prepare(source, probe, listOf(HlsAudio(2, false)), 2) }
        assertTrue(failed.exceptionOrNull() is java.io.IOException)
    }

    @Test
    fun `hevc with two ac3 soundtracks and subtitles gives every rendition`() {
        assumeTrue(ffmpegReady())
        serve("hevcac3.mkv") { base, hls ->
            val original = dir!!.resolve("hevcac3.mkv").path
            assertEquals(duration(original), duration("$base/master.m3u8"), 0.5)
            assertEquals(videoPackets(original), videoPackets("$base/v.m3u8"))
            val master = URL("$base/master.m3u8").readText()
            assertTrue(master, "CODECS=\"hev1." in master && ",ac-3\"" in master)
            assertEquals(2, hls.renditions.count { it.kind == HlsRendition.Kind.Audio })
            for (r in hls.renditions.filter { it.kind == HlsRendition.Kind.Audio }) {
                val log = decodes("-i", "$base/${r.uri}")
                assertTrue(log, "error" !in log.lowercase())
                assertEquals(40.0, duration("$base/${r.uri}"), 0.5)
            }
            val log = decodes("-i", "$base/master.m3u8", "-map", "0:v", "-map", "0:a:0")
            assertTrue(log, "Non-monotonic" !in log && "Non-monotonous" !in log)
            startsAlone(base, hls, "v", 4)
            startsAlone(base, hls, "a3", 4)
            val text = hls.renditions.single { it.kind == HlsRendition.Kind.Text }
            val (code, srt) = run("ffmpeg", "-nostdin", "-v", "error", "-i", "$base/${text.uri}", "-f", "srt", "-")
            assertEquals(srt, 0, code)
            assertTrue(srt, "00:00:01,000 --> 00:00:03,000" in srt && "Hello" in srt && "Second line" in srt && "Third" in srt)
        }
    }
}
