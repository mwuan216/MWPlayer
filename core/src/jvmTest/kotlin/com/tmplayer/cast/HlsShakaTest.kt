package com.tmplayer.cast

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * An HLS cast played the way the Default Media Receiver plays it since Google moved its HLS to
 * Shaka Player: Shaka in headless Chromium, against the cast server, the page reporting what it
 * found. Chromium here takes no Dolby (MSE says no to ec-3 and ac-3), as the Mi TV Stick of the
 * device test did, so the soundtracks it drops are the ones that stick dropped.
 *
 * Runs only with TM_CAST_MEDIA holding h264eac3subs.mkv (H.264, an E-AC-3 5.1 soundtrack, an AAC
 * stereo one and SubRip subtitles, as the device test's file), chromium-browser on the path, and
 * the network up for Shaka from cdnjs.
 */
class HlsShakaTest {

    private val dir = System.getenv("TM_CAST_MEDIA")?.let(::File)

    private fun chromium(): String? = listOf("/usr/bin/chromium-browser", "/usr/bin/chromium").firstOrNull { File(it).canExecute() }

    /** What Shaka made of [master]: its tracks, its errors, and the subtitle cues it had. */
    private fun shaka(master: String, subtitles: Boolean): JSONObject {
        val page = File("build/hls-shaka/page.html").apply { parentFile.mkdirs() }
        page.writeText(PAGE.replace("MASTER_URL", master).replace("WANT_TEXT", subtitles.toString()))
        var result = JSONObject()
        // A cold headless Chromium now and then answers MediaCapabilities with an error, and Shaka
        // then drops every variant (4032, CONTENT_UNSUPPORTED_BY_BROWSER): that run is taken again.
        for (attempt in 1..5) {
            if (attempt > 1) Thread.sleep(2_000)
            val p = ProcessBuilder(
                chromium()!!, "--headless=new", "--disable-gpu", "--no-sandbox", "--autoplay-policy=no-user-gesture-required",
                "--enable-logging=stderr", "--v=0", "--virtual-time-budget=40000", "--dump-dom", "file://" + page.absolutePath,
            ).redirectError(File("build/hls-shaka/chromium-" + (if (subtitles) "subtitles" else "plain") + ".log")).start()
            val dom = p.inputStream.bufferedReader().readText()
            p.waitFor(120, TimeUnit.SECONDS)
            val encoded = Regex("RESULT ([A-Za-z0-9+/=]+)").find(dom)?.groupValues?.get(1)
                ?: error("No result from the page: " + dom.take(2000))
            result = JSONObject(String(java.util.Base64.getDecoder().decode(encoded), Charsets.UTF_8))
            if (result.optString("fatal") != "4032") break
        }
        // Still no MediaCapabilities answer: this Chromium cannot be asked today, which says nothing of the playlist.
        assumeTrue("Chromium's MediaCapabilities kept failing: $result", result.optString("fatal") != "4032")
        return result
    }

    private fun serve(subtitles: Boolean, block: (master: String, hls: MkvHls) -> Unit) {
        val file = dir!!.resolve("h264eac3subs.mkv")
        assumeTrue("$file missing", file.exists())
        val source = FileCastSource(file)
        val probe = MediaProbe.of(source, file.name)
        val audio = CastFormats.hlsAudio(probe.audio, ReceiverCaps.GOOGLE_TV, canConvert = false)
        val hls = MkvHls.prepare(source, probe, audio, audio.firstOrNull()?.number, subtitles = subtitles)
        val server = CastServer(InetAddress.getLoopbackAddress(), allowed = { true })
        server.start()
        try {
            server.publish("hls1", CastResource.Tree(hls::resolve))
            save(if (subtitles) "with-subtitles" else "plain", server.urlFor("hls1"), hls)
            block(server.urlFor("hls1") + "/master.m3u8", hls)
        } finally {
            server.stop()
        }
    }

    /** The playlists and segments on the disk under build/hls-shaka, for a look at what Shaka was given. */
    private fun save(label: String, base: String, hls: MkvHls) {
        val out = File("build/hls-shaka/$label").apply { deleteRecursively(); mkdirs() }
        val names = arrayListOf("master.m3u8", "v.m3u8", "v-init.mp4")
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
        for (n in names) out.resolve(n).writeBytes(java.net.URL("$base/$n").readBytes())
    }

    private fun ready(): Boolean = dir != null && chromium() != null

    @Test
    fun `shaka plays the picture and the stereo soundtrack, the dolby one dropped as the stick did`() {
        assumeTrue(ready())
        serve(subtitles = false) { master, _ ->
            val r = shaka(master, subtitles = false)
            assertTrue(r.toString(), !r.has("fatal") && r.getJSONArray("errors").length() == 0)
            val variants = r.getJSONArray("variants")
            val codecs = (0 until variants.length()).map { variants.getJSONObject(it).optString("codecs") }.toSet()
            assertEquals(r.toString(), setOf("mp4a.40.2"), codecs)
            assertTrue(r.toString(), r.getDouble("time") > 30.0)
        }
    }

    @Test
    fun `shaka takes the subtitles inside the file and shows them at their times`() {
        assumeTrue(ready())
        serve(subtitles = true) { master, _ ->
            val r = shaka(master, subtitles = true)
            assertTrue(r.toString(), !r.has("fatal") && r.getJSONArray("errors").length() == 0)
            assertEquals(r.toString(), 1, r.getJSONArray("texts").length())
            val cues = r.getJSONArray("cues")
            assertTrue(r.toString(), cues.length() > 0)
            // Line 1 runs from 1 s to 2.5 s in the file; after the seek, the lines near 30 s.
            val starts = (0 until cues.length()).map { cues.getJSONArray(it).getDouble(0) }
            assertTrue(r.toString(), starts.any { kotlin.math.abs(it - 1.0) < 0.1 } || starts.any { it in 28.0..36.0 })
            // Every line of the file starts on an odd second: a cue anywhere else is mistimed.
            starts.forEach { s -> assertTrue("cue at $s: $r", kotlin.math.abs(s - Math.round(s)) < 0.05 && Math.round(s) % 2L == 1L) }
            assertTrue(r.toString(), r.getDouble("time") > 30.0)
        }
    }

    private companion object {
        val PAGE = """
            <!doctype html><html><body><video id="v" muted></video><pre id="out">pending</pre>
            <script src="https://cdnjs.cloudflare.com/ajax/libs/shaka-player/4.12.6/shaka-player.compiled.debug.js"></script>
            <script>
            const out = document.getElementById('out');
            const done = (o) => { out.textContent = 'RESULT ' + btoa(unescape(encodeURIComponent(JSON.stringify(o)))); };
            const wait = (ms) => new Promise((r) => setTimeout(r, ms));
            (async () => {
              const errors = [];
              try {
                shaka.polyfill.installAll();
                const v = document.getElementById('v');
                const p = new shaka.Player();
                await p.attach(v);
                p.addEventListener('error', (e) => errors.push(e.detail.code + ' ' + JSON.stringify(e.detail.data).slice(0, 300)));
                await p.load('MASTER_URL');
                const variants = p.getVariantTracks().map((t) => ({ id: t.id, lang: t.language, label: t.label, codecs: t.audioCodec, ch: t.channelsCount, active: t.active }));
                const texts = p.getTextTracks().map((t) => ({ id: t.id, lang: t.language, label: t.label, mime: t.mimeType }));
                if (WANT_TEXT && texts.length) {
                  p.selectTextTrack(p.getTextTracks()[0]);
                  await p.setTextTrackVisibility(true);
                }
                v.play().catch((e) => errors.push('play ' + e));
                await wait(5000);
                const early = [];
                for (const tt of v.textTracks) if (tt.cues) for (const c of tt.cues) early.push([c.startTime, c.endTime, c.text]);
                v.currentTime = 30;
                await wait(6000);
                const cues = early.slice();
                for (const tt of v.textTracks) if (tt.cues) for (const c of tt.cues) cues.push([c.startTime, c.endTime, c.text]);
                done({ variants, texts, errors, time: v.currentTime, cues: cues.slice(0, 60) });
              } catch (e) {
                done({ fatal: String(e && (e.code || e.message || e)), data: e && e.data ? JSON.stringify(e.data).slice(0, 500) : null, errors });
              }
            })();
            </script></body></html>
        """.trimIndent()
    }
}
