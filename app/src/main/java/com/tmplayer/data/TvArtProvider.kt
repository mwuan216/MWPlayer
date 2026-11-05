package com.tmplayer.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands the pictures on TMPlayer's home screen channels ([TvChannelsPublisher]) to the launcher.
 *
 * The launcher is another app, so it cannot read a file in this one's storage, and which app the
 * launcher is cannot be known ahead to grant it a URI. So this provider is exported, and kept as
 * narrow as that allows: it serves only the copies [TvChannelsPublisher] made in one directory of
 * its own ([dir]), by a bare file name of a fixed shape, read only, and nothing else at all. No
 * path, no query, no listing, no writes. What it serves is already on the home screen for anyone
 * in the room to see.
 */
class TvArtProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read only")
        val context = context ?: throw FileNotFoundException()
        val file = fileFor(context, uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = if (NAME.matches(uri.lastPathSegment.orEmpty())) "image/jpeg" else null

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        /** A copy's name: its kind, a number or a hash, and the one extension. Nothing with a slash or a dot elsewhere. */
        private val NAME = Regex("[a-z]_[0-9a-f]{1,40}\\.jpg")

        fun authority(context: Context): String = "${context.packageName}.tvart"

        /** Where the copies live: out of backups, and not the cache, which the system may empty under the launcher. */
        fun dir(context: Context): File = File(context.noBackupFilesDir, "tv_art")

        fun uriFor(context: Context, name: String): Uri =
            Uri.Builder().scheme("content").authority(authority(context)).appendPath(name).build()

        fun isValidName(name: String): Boolean = NAME.matches(name)

        private fun fileFor(context: Context, uri: Uri): File? {
            if (uri.authority != authority(context)) return null
            val segments = uri.pathSegments
            if (segments.size != 1) return null
            val name = segments[0]
            if (!NAME.matches(name)) return null
            return File(dir(context), name).takeIf { it.isFile }
        }
    }
}
