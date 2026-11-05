package io.github.anilbeesetti.nextlib.media3ext.ffdecoder

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.SimpleDecoderOutputBuffer

/**
 * NextLib's FFmpeg audio decoder, outside a Media3 renderer. The class is package private in
 * NextLib, built only for its renderer; the cast's sound conversion (CP73) needs the same decoder
 * on its own, to turn DTS, TrueHD and the rest into PCM for the AAC encoder, so this file lives
 * in NextLib's package to reach it. Nothing else of NextLib's is touched.
 */
@UnstableApi
object FfmpegAudioBridge {

    /** Whether NextLib's natives are here and decode [mimeType]. */
    fun decodes(mimeType: String): Boolean =
        runCatching { FfmpegLibrary.isAvailable() && FfmpegLibrary.supportsFormat(mimeType) }.getOrDefault(false)

    /** A 16 bit PCM decoder for [format]. */
    fun decoder(format: Format): SimpleDecoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *> =
        FfmpegAudioDecoder(format, BUFFERS, BUFFERS, INPUT_BYTES, false)

    /** The channels [decoder] puts out, known once it has decoded a frame; 2 before that. */
    fun channelCount(decoder: Any): Int = (decoder as? FfmpegAudioDecoder)?.channelCount?.takeIf { it > 0 } ?: 2

    private const val BUFFERS = 16
    private const val INPUT_BYTES = 64 * 1024
}
