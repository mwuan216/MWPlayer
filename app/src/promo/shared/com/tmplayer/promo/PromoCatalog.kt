package com.tmplayer.promo

import com.tmplayer.data.ChatKind
import com.tmplayer.data.MediaItem
import com.tmplayer.online.HttpRequest
import com.tmplayer.online.HttpResponse
import com.tmplayer.online.HttpTransport
import com.tmplayer.online.MetaMatch
import java.net.URLDecoder

/**
 * The demo library the screenshot fixtures share: the Android promo build (`app/src/promo/`) and
 * the desktop's promo render test both compile this file, so a phone, a television and a computer
 * show the same chats, files and posters.
 *
 * Every title and person is invented for the fixture, so a shot can be published as it is. The
 * pictures are Unsplash photos chosen for each title's mood (credited in docs/promo-art/CREDITS.md),
 * which scripts/promo-art.py cuts into the posters, backdrops and episode thumbnails. Some titles have no art
 * on purpose: the app's generated cover stands in, as it does for a real file without a picture. The chats, file
 * names, sizes and versions are made up the way real Telegram channels post them: a scene
 * release, a fansub group's dash form, a course numbered by lesson, a clip named by hand.
 *
 * Pictures are asked for by resource name ("promo_quiet_orbit_poster"); each fixture answers from
 * `app/src/promo/res/drawable-nodpi/`.
 */
object PromoCatalog {

    /** A picture by resource name, or null when the fixture does not ship it. */
    fun interface Pictures {
        fun bytes(name: String): ByteArray?
    }

    // ----------------------------------------------------------------------------------------
    // The chats.

    const val CINEMA = 101L
    const val ANIME = 102L
    const val STUDY = 103L
    const val NATURE = 104L
    const val CLIPS = 105L
    const val FAMILY = 106L

    /** [avatar] is a picture by name, or null for the initials the app draws for a chat without a photo. */
    class Chat(val id: Long, val title: String, val avatar: String?, val kind: ChatKind)

    val chats = listOf(
        Chat(CINEMA, "Cinema Lounge", "promo_saltwater_lanterns_backdrop", ChatKind.Channel),
        Chat(ANIME, "Anime Vault", "promo_paper_kingdoms_backdrop", ChatKind.Group),
        Chat(STUDY, "Study Hub", "demo_tutorial", ChatKind.Channel),
        Chat(NATURE, "Nature Docs", "demo_forest", ChatKind.Channel),
        Chat(CLIPS, "Weekend Clips", "demo_coast", ChatKind.Group),
        Chat(FAMILY, "Family Archive", "demo_birthday", ChatKind.Group),
    )

    fun chatTitle(id: Long): String = chats.first { it.id == id }.title

    // ----------------------------------------------------------------------------------------
    // Films.

    /** One file as a chat posted it. */
    class Copy(val fileName: String, val sizeMb: Long, val width: Int, val height: Int)

    class Film(
        val slug: String,
        val title: String,
        val year: Int,
        val tmdbId: Int,
        val directors: List<String>,
        val durationSec: Int,
        val genres: List<String>,
        val certification: String,
        val overview: String,
        /** The copies posted, the best first; a second copy folds into one poster with a pick. */
        val copies: List<Copy>,
        /** False for a title the fixture has no art for, which then shows the generated cover. */
        val art: Boolean = true,
    ) {
        val poster get() = "promo_${slug}_poster"
        val backdrop get() = "promo_${slug}_backdrop"
    }

    private fun hd(file: String, mb: Long) = Copy(file, mb, 1920, 1080)
    private fun uhd(file: String, mb: Long) = Copy(file, mb, 3840, 2160)
    private fun sd(file: String, mb: Long) = Copy(file, mb, 1280, 720)

    val films = listOf(
        Film(
            "quiet_orbit", "The Quiet Orbit", 2024, 9101, listOf("Ilse Marrow"), 7_560, listOf("Science Fiction", "Drama"), "PG-13",
            "Alone on a research station at the end of its mission, a botanist keeps a small greenhouse alive and starts to hear from a crew that should not exist.",
            listOf(uhd("The.Quiet.Orbit.2024.2160p.WEB-DL.DDP5.1.HEVC.mkv", 6_980), hd("The.Quiet.Orbit.2024.1080p.WEB-DL.DDP5.1.x264.mkv", 2_460)),
        ),
        Film(
            "saltwater_lanterns", "Saltwater Lanterns", 2023, 9102, listOf("Anjali Varma"), 7_080, listOf("Drama", "Family"), "PG",
            "On the night of the lantern festival, two estranged sisters share one canoe across the backwaters and one long conversation they have put off for years.",
            listOf(hd("Saltwater.Lanterns.2023.1080p.WEB-DL.x264.mkv", 2_140), sd("Saltwater.Lanterns.2023.720p.WEB-DL.x264.mkv", 1_090)),
        ),
        Film(
            "monsoon_express", "Monsoon Express", 2022, 9103, listOf("Rohan Kale"), 8_460, listOf("Thriller", "Action"), "PG-13",
            "A night train is cut off by a landslide in the middle of the monsoon, and a ticket inspector has until dawn to find out who on board is not who they claim.",
            listOf(hd("Monsoon.Express.2022.1080p.BluRay.x264.mkv", 2_610)),
        ),
        Film(
            "paper_kingdoms", "Paper Kingdoms", 2021, 9104, listOf("Mei Takara", "Owen Hale"), 5_640, listOf("Animation", "Family", "Adventure"), "G",
            "A girl who folds paper animals finds a way into a city made entirely of origami, where a small paper fox needs her help before the rain arrives.",
            listOf(hd("Paper.Kingdoms.2021.1080p.BluRay.x264.mkv", 1_720)),
        ),
        Film(
            "kestrel_point", "Last Light at Kestrel Point", 2020, 9105, listOf("Fiona Gale"), 6_540, listOf("Mystery", "Drama"), "PG-13",
            "When the keeper of a remote lighthouse goes missing in the fog, his daughter returns to the headland and finds the logbook stops three nights early.",
            listOf(hd("Last.Light.at.Kestrel.Point.2020.1080p.BluRay.x264.mkv", 1_980)),
            art = false,
        ),
        Film(
            "neon_tiffin", "Neon Tiffin", 2024, 9106, listOf("Kabir Sethi"), 6_720, listOf("Comedy"), "PG",
            "A delivery rider mixes up two tiffin orders on the busiest night of the year, and spends the rest of it putting right a chain of lunches across the city.",
            listOf(sd("Neon.Tiffin.2024.720p.WEBRip.x264.mp4", 1_160)),
            art = false,
        ),
    )

    fun film(slug: String): Film = films.first { it.slug == slug }

    /** The film's frame for a Telegram thumbnail: its backdrop, as a channel's would be a still. */
    fun filmItems(pictures: Pictures, chatId: Long, list: List<Film>, firstId: Long, newest: Int, posterThumbnails: Boolean = false): List<MediaItem> {
        var id = firstId
        var date = newest
        return list.flatMap { film ->
            film.copies.map { copy ->
                MediaItem(
                    chatId = chatId,
                    messageId = id++,
                    fileId = 0,
                    title = copy.fileName,
                    sizeBytes = copy.sizeMb * 1024 * 1024,
                    durationSec = film.durationSec,
                    mimeType = if (copy.fileName.endsWith(".mp4")) "video/mp4" else "video/x-matroska",
                    thumbnailFileId = 0,
                    miniThumbnail = if (film.art) pictures.bytes(if (posterThumbnails) film.poster else film.backdrop) else null,
                    date = date--,
                    fileName = copy.fileName,
                    width = copy.width,
                    height = copy.height,
                )
            }
        }
    }

    // ----------------------------------------------------------------------------------------
    // Shows.

    class Episode(val number: Int, val name: String, val durationSec: Int, val sizeMb: Long, val overview: String)

    class Show(
        val slug: String,
        val title: String,
        val year: Int,
        /** TMDB's id, or AniList's for the anime. */
        val id: Int,
        val anime: Boolean,
        val genres: List<String>,
        val creators: List<String>,
        val certification: String,
        val overview: String,
        val episodes: List<Episode>,
        /** The file name each episode was posted under. */
        val fileName: (Episode) -> String,
        val art: Boolean = true,
    ) {
        val poster get() = "promo_${slug}_poster"
        val backdrop get() = "promo_${slug}_backdrop"
        fun still(e: Episode) = "promo_${slug}_e${e.number}"
    }

    val harbourLines = Show(
        "harbour_lines", "Harbour Lines", 2024, 9201, anime = false,
        genres = listOf("Drama"), creators = listOf("Tessa Morrow"), certification = "TV-14",
        overview = "A young dock worker and an ageing ship captain are thrown together when the port they both depend on is sold, and the whole harbour picks a side.",
        episodes = listOf(
            Episode(1, "First Shift", 2_880, 1_420, "Nadia starts at the port the morning the sale is announced."),
            Episode(2, "Night Office", 2_760, 1_380, "A late meeting in the harbour office ends with a promise nobody can keep."),
            Episode(3, "Rough Water", 2_940, 1_460, "Captain Hale takes the old boat out against orders, and Nadia goes with him."),
            Episode(4, "Market Day", 2_820, 1_400, "The whole town turns up at the fish market, and so do the new owners."),
        ),
        fileName = { "Harbour.Lines.S01E%02d.1080p.WEB-DL.DDP5.1.x264.mkv".format(it.number) },
    )

    val skybound = Show(
        "skybound_academy", "Skybound Academy", 2025, 9301, anime = true,
        genres = listOf("Animation", "Adventure", "Fantasy"), creators = emptyList(), certification = "TV-PG",
        overview = "A first-year student arrives at a school built on islands that float above the clouds, where every lesson is flown and the glider races decide who stays.",
        episodes = listOf(
            Episode(1, "Above the Clouds", 1_440, 342, ""),
            Episode(2, "The Glider Race", 1_440, 338, ""),
            Episode(3, "The Floating Library", 1_440, 351, ""),
            Episode(4, "Sunset on the Edge", 1_440, 344, ""),
        ),
        fileName = { "[Kumo] Skybound Academy - %02d (1080p).mkv".format(it.number) },
    )

    val wildCoasts = Show(
        "wild_coasts", "Wild Coasts", 2023, 9401, anime = false,
        genres = listOf("Documentary"), creators = listOf("Ruth Ashdown"), certification = "TV-G",
        overview = "A year on the edges of the world's oceans, from seabird cliffs in the north to mangrove forests and the warm reefs of the tropics.",
        episodes = listOf(
            Episode(1, "Cliffs", 3_180, 2_840, "Puffins return to the northern cliffs to raise their young above a cold sea."),
            Episode(2, "Mangroves", 3_120, 2_790, "At low tide the mangroves become a nursery for half the coast."),
            Episode(3, "Warm Water", 3_240, 2_910, "Dolphins and turtles follow the currents along a tropical shore."),
        ),
        fileName = { "Wild.Coasts.S01E%02d.2160p.WEB-DL.HEVC.mkv".format(it.number) },
        art = false,
    )

    val shows = listOf(harbourLines, skybound, wildCoasts)

    /** Every episode of [show], newest first, posted from [newest] down. */
    fun episodeItems(pictures: Pictures, chatId: Long, show: Show, firstId: Long, newest: Int): List<MediaItem> {
        var id = firstId
        return show.episodes.reversed().mapIndexed { index, e ->
            val name = show.fileName(e)
            val uhd = "2160p" in name
            MediaItem(
                chatId = chatId,
                messageId = id++,
                fileId = 0,
                title = name,
                sizeBytes = e.sizeMb * 1024 * 1024,
                durationSec = e.durationSec,
                mimeType = "video/x-matroska",
                thumbnailFileId = 0,
                miniThumbnail = if (show.art) pictures.bytes(show.still(e)) else null,
                date = newest - index,
                fileName = name,
                width = if (uhd) 3840 else 1920,
                height = if (uhd) 2160 else 1080,
            )
        }
    }

    // ----------------------------------------------------------------------------------------
    // The course and the clips.

    const val COURSE = "Python from Zero"

    class Lesson(val number: Int, val name: String, val durationSec: Int, val sizeMb: Long)

    val lessons = listOf(
        Lesson(1, "Variables", 1_420, 212),
        Lesson(2, "Loops", 1_655, 248),
        Lesson(3, "Functions", 1_892, 281),
        Lesson(4, "Files", 1_530, 229),
    )

    fun lessonFile(l: Lesson) = "$COURSE - Lesson %02d - %s.mp4".format(l.number, l.name)

    /**
     * The course's welcome video, then its lessons, newest first, posted without pictures as
     * screen recordings often are, so each shows the generated cover.
     */
    fun courseItems(pictures: Pictures, chatId: Long, firstId: Long, newest: Int): List<MediaItem> {
        var id = firstId
        fun item(name: String, picture: String?, durationSec: Int, sizeMb: Long, date: Int) = MediaItem(
            chatId = chatId,
            messageId = id++,
            fileId = 0,
            title = name,
            sizeBytes = sizeMb * 1024 * 1024,
            durationSec = durationSec,
            mimeType = "video/mp4",
            thumbnailFileId = 0,
            miniThumbnail = picture?.let(pictures::bytes),
            date = date,
            fileName = name,
            width = 1920,
            height = 1080,
        )
        val run = lessons.reversed().mapIndexed { index, l ->
            item(lessonFile(l), null, l.durationSec, l.sizeMb, newest - index)
        }
        return run + item("$COURSE - Welcome and setup.mp4", null, 486, 74, newest - run.size)
    }

    /**
     * Two videos people post themselves, a concert filmed from the crowd and a day of a road trip,
     * sent as files without a thumbnail, so they show the generated cover.
     */
    fun clipItems(pictures: Pictures, chatId: Long, firstId: Long, newest: Int): List<MediaItem> = listOf(
        MediaItem(
            chatId = chatId, messageId = firstId, fileId = 0, title = "Live at the Old Fort, full set",
            sizeBytes = 1_310L * 1024 * 1024, durationSec = 4_380, mimeType = "video/mp4", thumbnailFileId = 0,
            miniThumbnail = null, date = newest,
            fileName = "old-fort-live-full-set-1080p.mp4", caption = "Live at the Old Fort, full set", width = 1920, height = 1080,
        ),
        MediaItem(
            chatId = chatId, messageId = firstId + 1, fileId = 0, title = "Ladakh on Two Wheels, day 3",
            sizeBytes = 2_270L * 1024 * 1024, durationSec = 1_962, mimeType = "video/mp4", thumbnailFileId = 0,
            miniThumbnail = null, date = newest - 1,
            fileName = "ladakh-ride-day-3-4k.mp4", caption = "Ladakh on Two Wheels, day 3", width = 3840, height = 2160,
        ),
    )

    // ----------------------------------------------------------------------------------------
    // The library and where the viewer is in it.

    /**
     * Every chat's videos, newest first, and the demo viewer's state over them: a film part way
     * through, a show and an anime followed
     * week by week with new episodes since, a course two lessons in, a few titles put aside for
     * later, and three starred chats.
     */
    class Library(
        pictures: Pictures,
        /**
         * The films posted with their posters as the file's thumbnail, as many film channels post
         * them, so a wide tile shows the upright poster whole over a soft fill of itself. For the
         * setup's Performance pictures (CP69), where Lighter pictures drops that fill's blur.
         */
        posterThumbnails: Boolean = false,
    ) {
        val cinema = filmItems(pictures, CINEMA, films, firstId = 1_000, newest = 9_000, posterThumbnails = posterThumbnails) +
            episodeItems(pictures, CINEMA, harbourLines, firstId = 1_100, newest = 8_950)
        val anime = episodeItems(pictures, ANIME, skybound, firstId = 1_200, newest = 8_800)
        val study = courseItems(pictures, STUDY, firstId = 1_300, newest = 8_600)
        val nature = episodeItems(pictures, NATURE, wildCoasts, firstId = 1_400, newest = 8_400)
        val clips = clipItems(pictures, CLIPS, firstId = 1_500, newest = 8_200)

        val byChat: Map<Long, List<MediaItem>> = mapOf(CINEMA to cinema, ANIME to anime, STUDY to study, NATURE to nature, CLIPS to clips)
        val all: List<MediaItem> get() = byChat.values.flatten()

        /**
         * The chats starred in this demo, which Home gives a row each: films, a show, an anime
         * and a nature series, each with its own backdrop.
         */
        val favourites = setOf(CINEMA, ANIME, STUDY, NATURE)

        private fun named(part: String) = all.first { part in it.fileName }

        val quietOrbit = named("Quiet.Orbit.2024.2160p")
        private val harbour3 = named("Harbour.Lines.S01E03")
        private val harbour4 = named("Harbour.Lines.S01E04")
        private val sky2 = named("Skybound Academy - 02")
        private val sky3 = named("Skybound Academy - 03")
        private val sky4 = named("Skybound Academy - 04")
        private val lesson3 = named("Lesson 03")
        private val ladakh = named("ladakh-ride")

        /** Watched to the end: the episodes and lessons before the ones in progress. */
        val finished: Set<String> = listOf(
            named("Harbour.Lines.S01E01"), named("Harbour.Lines.S01E02"), named("Skybound Academy - 01"),
            named("Lesson 01"), named("Lesson 02"), named("Welcome and setup"),
        ).mapTo(HashSet()) { it.id }

        /** Where the viewer stopped, as a fraction of each video. */
        private val stopped: List<Pair<MediaItem, Float>> = listOf(
            quietOrbit to 0.57f, harbour3 to 0.38f, sky2 to 0.62f, lesson3 to 0.45f, ladakh to 0.3f,
        )

        fun point(item: MediaItem): com.tmplayer.data.WatchPoint? = stopped.firstOrNull { it.first.id == item.id }?.let { (video, at) ->
            com.tmplayer.data.WatchPoint((video.durationSec * 1000L * at).toLong(), video.durationSec * 1000L)
        }

        fun isFinished(item: MediaItem) = item.id in finished

        val continueWatching: List<com.tmplayer.data.ResumeRecord> = stopped.mapIndexed { index, (item, _) ->
            val at = point(item)!!
            com.tmplayer.data.ResumeRecord(
                chatId = item.chatId, messageId = item.messageId, fileId = item.fileId, title = item.title,
                chatTitle = chatTitle(item.chatId), sizeBytes = item.sizeBytes, durationSec = item.durationSec,
                positionMs = at.positionMs, durationMs = at.durationMs, updatedAt = 1_000L - index,
            )
        }

        val watchLater: List<com.tmplayer.data.WatchLaterRecord> = listOf(
            named("Monsoon.Express"), named("Wild.Coasts.S01E01"), named("Paper.Kingdoms"), named("Kestrel.Point"),
        ).mapIndexed { index, item ->
            com.tmplayer.data.WatchLaterRecord(
                chatId = item.chatId, messageId = item.messageId, fileId = item.fileId, title = item.title,
                fileName = item.fileName, chatTitle = chatTitle(item.chatId), sizeBytes = item.sizeBytes,
                durationSec = item.durationSec, mimeType = item.mimeType, addedAt = 500L - index,
            )
        }

        /** Arrived since the viewer last watched these two shows. */
        val newEpisodes: List<com.tmplayer.data.NewEpisode> = listOf(
            com.tmplayer.data.NewEpisode(harbour4, harbourLines.title, "S01E04"),
            com.tmplayer.data.NewEpisode(sky4, skybound.title, "E04"),
            com.tmplayer.data.NewEpisode(sky3, skybound.title, "E03"),
        )

        /** Each Continue and Watch later record's video, for the tiles' pictures. */
        val art: Map<String, MediaItem> = (stopped.map { it.first } + watchLater.mapNotNull { r -> all.firstOrNull { it.chatId == r.chatId && it.messageId == r.messageId } })
            .associateBy { com.tmplayer.data.SettingsStore.progressKey(it.chatId, it.messageId) }
    }

    // ----------------------------------------------------------------------------------------
    // TMDB, AniList and TVmaze as far as the demo library goes.

    /**
     * Answers the metadata lookups for the demo library without a network: TMDB's searches, a
     * title's page with its extras, a show's seasons and episodes with their stills, AniList for
     * the anime, and the pictures by their path. TVmaze finds nothing. [refuse] turns the TMDB key
     * away, as a revoked key would be.
     */
    class Canned(private val pictures: Pictures, private val refuse: Boolean = false) : HttpTransport {
        override suspend fun send(request: HttpRequest): HttpResponse {
            val url = URLDecoder.decode(request.url, "UTF-8")
            val path = url.substringBefore('?')
            if (path.endsWith(".webp")) {
                val bytes = pictures.bytes(path.substringAfterLast('/').removeSuffix(".webp"))
                return if (bytes != null) HttpResponse(200, body = bytes) else HttpResponse(404)
            }
            if (refuse && "themoviedb" in url) return json(401, """{"status_code":7}""")
            if ("api.tvmaze.com" in url) return json(404, "{}")
            if ("graphql.anilist.co" in url) return json(200, anilist(request.body.orEmpty()))
            val query = Regex("""[?&]query=([^&]*)""").find(url)?.groupValues?.get(1)?.let(MetaMatch::normalise)
            val tmdbShows = shows.filter { !it.anime }
            val body = when {
                "search/movie" in path -> """{"results":[${films.firstOrNull { MetaMatch.normalise(it.title) == query }?.let(::movie).orEmpty()}]}"""
                "search/tv" in path -> """{"results":[${tmdbShows.firstOrNull { MetaMatch.normalise(it.title) == query }?.let(::tv).orEmpty()}]}"""
                Regex("""/movie/\d+$""").containsMatchIn(path) -> {
                    val id = path.substringAfterLast('/').toInt()
                    movieExtras(films.firstOrNull { it.tmdbId == id } ?: return json(404, "{}"))
                }
                else -> {
                    val match = Regex("""/tv/(\d+)(?:/season/(\d+)(?:/episode/(\d+))?)?$""").find(path)
                        ?: return json(200, """{"results":[]}""")
                    val show = tmdbShows.firstOrNull { it.id.toString() == match.groupValues[1] } ?: return json(404, "{}")
                    val season = match.groupValues[2]
                    val number = match.groupValues[3]
                    when {
                        season.isEmpty() -> tvExtras(show)
                        season != "1" -> return json(404, "{}")
                        number.isEmpty() -> """{"episodes":[${show.episodes.joinToString(",") { episode(show, it) }}]}"""
                        else -> show.episodes.firstOrNull { it.number.toString() == number }?.let { episode(show, it) } ?: return json(404, "{}")
                    }
                }
            }
            return json(200, body)
        }

        private fun json(code: Int, body: String) = HttpResponse(code, mapOf("Content-Type" to "application/json"), body.toByteArray())
    }

    private fun path(art: Boolean, name: String) = if (art) "\"/$name.webp\"" else "null"

    private fun esc(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun names(list: List<String>, job: String? = null) =
        list.joinToString(",") { if (job != null) """{"name":"${esc(it)}","job":"$job"}""" else """{"name":"${esc(it)}"}""" }

    private fun movie(f: Film) = """{"id":${f.tmdbId},"title":"${esc(f.title)}","original_title":"${esc(f.title)}","release_date":"${f.year}-03-14",
        "overview":"${esc(f.overview)}","poster_path":${path(f.art, f.poster)},"backdrop_path":${path(f.art, f.backdrop)}}"""

    /** A film's page with its extras appended, which is also what a lookup by id reads. */
    private fun movieExtras(f: Film): String {
        val others = films.filter { it !== f }
        return movie(f).dropLast(1) + """,
            "genres":[${names(f.genres)}],"runtime":${(f.durationSec + 59) / 60},"vote_average":0,"vote_count":0,
            "credits":{"cast":[],"crew":[${names(f.directors, "Director")}]},
            "videos":{"results":[]},
            "release_dates":{"results":[{"iso_3166_1":"US","release_dates":[{"certification":"${f.certification}","type":3}]}]},
            "recommendations":{"results":[${others.joinToString(",") { """{"id":${it.tmdbId},"title":"${esc(it.title)}"}""" }}]},
            "similar":{"results":[]}}"""
    }

    private fun tv(s: Show) = """{"id":${s.id},"name":"${esc(s.title)}","original_name":"${esc(s.title)}","first_air_date":"${s.year}-02-01",
        "overview":"${esc(s.overview)}","poster_path":${path(s.art, s.poster)},"backdrop_path":${path(s.art, s.backdrop)}}"""

    private fun tvExtras(s: Show) = tv(s).dropLast(1) + """,
        "genres":[${names(s.genres)}],"episode_run_time":[${s.episodes.first().durationSec / 60}],"number_of_seasons":1,"vote_average":0,"vote_count":0,
        "status":"Returning Series","created_by":[${names(s.creators)}],
        "seasons":[{"season_number":1,"episode_count":${s.episodes.size},"air_date":"${s.year}-02-01"}],
        "aggregate_credits":{"cast":[]},
        "content_ratings":{"results":[{"iso_3166_1":"US","rating":"${s.certification}"}]},
        "videos":{"results":[]},
        "recommendations":{"results":[]},"similar":{"results":[]}}"""

    private fun episode(s: Show, e: Episode) = """{"episode_number":${e.number},"season_number":1,"name":"${esc(e.name)}","overview":"${esc(e.overview)}",
        "air_date":"${s.year}-02-%02d","runtime":${e.durationSec / 60},"still_path":${path(s.art, s.still(e))}}""".format(e.number * 7 - 6)

    /** AniList's search for the anime by its title; anything else finds nothing. */
    private fun anilist(body: String): String {
        val show = shows.firstOrNull { it.anime && it.title in body } ?: return """{"data":{"Page":{"media":[]}}}"""
        return """{"data":{"Page":{"media":[{"id":${show.id},"format":"TV","seasonYear":${show.year},
            "title":{"romaji":"${esc(show.title)}","english":"${esc(show.title)}","native":""},"synonyms":[],
            "description":"${esc(show.overview)}",
            "coverImage":{"large":"https://s4.anilist.co/${show.poster}.webp"},"bannerImage":"https://s4.anilist.co/${show.backdrop}.webp"}]}}}"""
    }
}
