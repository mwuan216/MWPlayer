package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MeteredPolicyTest {

    private val large = MeteredPolicy.LARGE_DOWNLOAD_BYTES * 2
    private val small = MeteredPolicy.LARGE_DOWNLOAD_BYTES / 4

    @Test
    fun `wifi only is a phone's switch, and a television holds nothing back for it`() {
        assertEquals(true, MeteredPolicy.wifiOnlyOffered(television = false))
        assertEquals(false, MeteredPolicy.wifiOnlyOffered(television = true))
        assertEquals(true, MeteredPolicy.wifiOnlyInForce(stored = true, television = false))
        assertEquals(false, MeteredPolicy.wifiOnlyInForce(stored = false, television = false))
        // Restored from a phone's backup onto a TV: kept, but not in force where there is no row.
        assertEquals(false, MeteredPolicy.wifiOnlyInForce(stored = true, television = true))
    }

    @Test
    fun `save mobile data sits beside wifi only, on a phone alone`() {
        assertEquals(true, MeteredPolicy.saveMobileDataOffered(television = false))
        assertEquals(false, MeteredPolicy.saveMobileDataOffered(television = true))
        assertEquals(true, MeteredPolicy.saveMobileDataInForce(stored = true, television = false))
        assertEquals(false, MeteredPolicy.saveMobileDataInForce(stored = false, television = false))
        assertEquals(false, MeteredPolicy.saveMobileDataInForce(stored = true, television = true))
    }

    @Test
    fun `wifi never asks anything`() {
        assertEquals(
            MeteredDecision.Allow,
            decide(metered = false, wifiOnly = true, sizeBytes = large),
        )
    }

    @Test
    fun `a downloaded video plays even under wifi only`() {
        assertEquals(
            MeteredDecision.Allow,
            decide(metered = true, wifiOnly = true, alreadyDownloaded = true, sizeBytes = large),
        )
    }

    @Test
    fun `wifi only blocks a video that still needs fetching`() {
        assertEquals(
            MeteredDecision.Block,
            decide(metered = true, wifiOnly = true, sizeBytes = small),
        )
    }

    @Test
    fun `a large video on mobile data asks once`() {
        assertEquals(
            MeteredDecision.Warn,
            decide(metered = true, sizeBytes = large),
        )
        assertEquals(
            MeteredDecision.Allow,
            decide(metered = true, warnedThisSession = true, sizeBytes = large),
        )
    }

    @Test
    fun `a small video on mobile data is not worth a prompt`() {
        assertEquals(MeteredDecision.Allow, decide(metered = true, sizeBytes = small))
    }

    private fun decide(
        metered: Boolean,
        wifiOnly: Boolean = false,
        alreadyDownloaded: Boolean = false,
        warnedThisSession: Boolean = false,
        sizeBytes: Long,
    ) = MeteredPolicy.decide(metered, wifiOnly, alreadyDownloaded, warnedThisSession, sizeBytes)
}
