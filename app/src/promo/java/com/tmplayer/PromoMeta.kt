package com.tmplayer

import android.annotation.SuppressLint
import android.content.Context
import com.tmplayer.data.MediaItem
import com.tmplayer.online.MetadataCache
import com.tmplayer.online.MetadataStore
import com.tmplayer.online.OnlineMetadata
import com.tmplayer.promo.PromoCatalog
import java.io.File

/**
 * Posters and overviews for the screenshot fixture, over the demo library's canned TMDB and
 * AniList ([PromoCatalog.Canned]) that never touch the network. `--ez meta true` turns lookups on;
 * `--es metakey none|refused|own` shows the TMDB key row in each state; without `meta` the feature
 * is installed but off, which is what a fresh install looks like (the detail panel then offers to
 * turn it on).
 */
internal object PromoMeta {

    /** Whether lookups are on in this run, for the fixture's Home and detail variants. */
    var on = false
        private set

    /** The demo library's pictures, read from this build's resources by name. */
    @Volatile
    lateinit var pictures: PromoCatalog.Pictures
        private set

    /** `--ez poster_thumbs true`: the films' thumbnails are their posters. Set before [library] is first read. */
    @Volatile
    var posterThumbnails = false

    /** The demo library, built once [install] has run. */
    val library: PromoCatalog.Library by lazy { PromoCatalog.Library(pictures, posterThumbnails) }

    /** The film the detail variant opens: the one the demo viewer is part way through. */
    val film: MediaItem get() = library.quietOrbit

    @SuppressLint("DiscouragedApi")
    fun install(context: Context, enabled: Boolean, key: String?) {
        on = enabled
        val res = context.resources
        val cache = HashMap<String, ByteArray?>()
        pictures = PromoCatalog.Pictures { name ->
            synchronized(cache) {
                cache.getOrPut(name) {
                    val id = res.getIdentifier(name, "drawable", context.packageName)
                    if (id == 0) null else res.openRawResource(id).use { it.readBytes() }
                }
            }
        }
        val dir = File(context.cacheDir, "promo-metadata").apply { deleteRecursively() }
        val store = MetadataStore(File(dir, "metadata.properties"))
        store.update { it.copy(enabled = enabled || key == "refused", ownKey = if (key == "own") "promo-own-key" else "") }
        val online = OnlineMetadata(
            buildKey = if (key == "none") "" else "promo-key",
            store = store,
            cache = MetadataCache(File(dir, "cache")),
            appVersion = BuildConfig.VERSION_NAME,
            http = PromoCatalog.Canned(pictures, refuse = key == "refused"),
        )
        OnlineMetadata.current = online
        // A refused key is learnt from one answer, as it would be in the app.
        if (key == "refused") {
            kotlinx.coroutines.runBlocking { online.lookup(com.tmplayer.online.MetaQuery("Refused", 2000)) }
            online.setEnabled(enabled)
        }
    }
}
