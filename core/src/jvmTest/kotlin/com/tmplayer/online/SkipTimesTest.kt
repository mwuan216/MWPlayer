package com.tmplayer.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipTimesTest {

    private class FakeHttp(private val code: Int, private val body: String) : HttpTransport {
        val asked = mutableListOf<String>()
        override suspend fun send(request: HttpRequest): HttpResponse {
            asked += request.url
            return HttpResponse(code, emptyMap(), body.toByteArray())
        }
    }

    private fun api(http: HttpTransport) = SkipTimesApi(
        http,
        RateLimiter(0, { 0L }, {}),
        RateLimiter(0, { 0L }, {}),
        "test",
    )

    @Test
    fun `TheIntroDB milliseconds and open ends are read`() = runBlocking {
        val http = FakeHttp(
            200,
            """{"tmdb_id":1396,"type":"tv","season":1,"episode":2,"intro":[{"start_ms":314360,"end_ms":330461}],"credits":[{"start_ms":2839000,"end_ms":null}],"recap":[{"start_ms":null,"end_ms":40000}]}""",
        )
        val reply = api(http).theIntroDb("1396", null, null, 1, 2) as MetaReply.Ok
        assertEquals("https://api.theintrodb.org/v3/media?tmdb_id=1396&season=1&episode=2", http.asked.single())
        assertEquals(
            listOf(
                SkipSegment(SkipKind.Recap, 0, 40_000),
                SkipSegment(SkipKind.Intro, 314_360, 330_461),
                SkipSegment(SkipKind.Credits, 2_839_000, null),
            ),
            reply.value,
        )
    }

    @Test
    fun `AniSkip seconds become milliseconds and a 404 is nothing timed`() = runBlocking {
        val http = FakeHttp(
            200,
            """{"found":true,"results":[{"interval":{"startTime":13.891,"endTime":123.834},"skipType":"op","skipId":"x","episodeLength":1430.721}],"message":"Successfully found skip times","statusCode":200}""",
        )
        val reply = api(http).aniSkip("21", 1000, 1430) as MetaReply.Ok
        assertTrue(http.asked.single().startsWith("https://api.aniskip.com/v2/skip-times/21/1000?episodeLength=1430&types=op"))
        assertEquals(listOf(SkipSegment(SkipKind.Opening, 13_891, 123_834)), reply.value)
        val none = api(FakeHttp(404, """{"found":false,"results":[]}""")).aniSkip("21", 1, 0) as MetaReply.Ok
        assertEquals(emptyList<SkipSegment>(), none.value)
    }

    @Test
    fun `the button shows inside a segment, not in its last second, and not once skipped`() {
        val intro = SkipSegment(SkipKind.Intro, 60_000, 150_000)
        val credits = SkipSegment(SkipKind.Credits, 2_700_000, null)
        val all = listOf(intro, credits)
        assertNull(SkipSegments.active(all, 59_000, 3_000_000))
        assertEquals(intro, SkipSegments.active(all, 61_000, 3_000_000))
        assertNull(SkipSegments.active(all, 149_500, 3_000_000))
        assertNull(SkipSegments.active(all, 61_000, 3_000_000, skipped = setOf(60_000)))
        assertEquals(2_700_000L, SkipSegments.creditsStart(all, 3_000_000))
        assertEquals(3_000_000L, credits.target(3_000_000))
    }

    @Test
    fun `IntroDB app segments are read and its outro is the credits`() = runBlocking {
        val http = FakeHttp(
            200,
            """{"imdb_id":"tt0903747","media_type":"tv","season":1,"episode":2,"intro":{"start_sec":314.5,"end_sec":331,"start_ms":314500,"end_ms":331000,"confidence":1,"submission_count":5},"recap":null,"outro":{"start_ms":2843000,"end_ms":2901000},"post_credits":null}""",
        )
        val reply = api(http).introDbApp("tt0903747", 1, 2) as MetaReply.Ok
        assertEquals("https://api.introdb.app/segments?imdb_id=tt0903747&season=1&episode=2", http.asked.single())
        assertEquals(
            listOf(SkipSegment(SkipKind.Intro, 314_500, 331_000), SkipSegment(SkipKind.Credits, 2_843_000, 2_901_000)),
            reply.value,
        )
    }
}
