package com.tmplayer.cast

import android.content.Context

/**
 * The F-Droid build's casting: none. This build has no Google Cast framework on its classpath and
 * no options provider in its manifest, so nothing here may name either (CP73).
 */
internal object CastBuild {
    fun create(context: Context): CastSupport = NoCastSupport
}
