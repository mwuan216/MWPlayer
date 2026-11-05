package com.tmplayer.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Low-end devices (CP68): the lines, and what is recommended where. */
class DeviceFitTest {

    private val gib = 1024L * 1024 * 1024
    private val roomy = 64 * gib

    private fun phone(memory: Long, free: Long = roomy, sdk: Int = 34, only32: Boolean = false, lowRam: Boolean = false) =
        DeviceProbe(totalMemoryBytes = memory, lowRamDevice = lowRam, freeStorageBytes = free, sdk = sdk, only32Bit = only32, tv = false)

    @Test
    fun `a phone sold as 2 GB is short of memory, one sold as 3 GB is not`() {
        assertTrue(DeviceFitRules.assess(phone(1_900_000_000L)).shortOfMemory)
        assertFalse(DeviceFitRules.assess(phone(2_900_000_000L)).shortOfMemory)
        assertFalse(DeviceFitRules.assess(phone(8 * gib)).shortOfMemory)
    }

    @Test
    fun `a television sold as 1 or 1_5 GB is short, one sold as 2 GB is not`() {
        fun tv(memory: Long) = DeviceProbe(totalMemoryBytes = memory, freeStorageBytes = roomy, sdk = 28, tv = true)
        assertTrue(DeviceFitRules.assess(tv(960_000_000L)).shortOfMemory)
        assertTrue(DeviceFitRules.assess(tv(1_450_000_000L)).shortOfMemory)
        assertFalse(DeviceFitRules.assess(tv(1_900_000_000L)).shortOfMemory)
    }

    @Test
    fun `Android's own low RAM flag, an old Android or 32-bit only count as short of memory`() {
        assertTrue(DeviceFitRules.assess(phone(6 * gib, lowRam = true)).shortOfMemory)
        assertTrue(DeviceFitRules.assess(phone(6 * gib, sdk = 25)).shortOfMemory)
        assertFalse(DeviceFitRules.assess(phone(6 * gib, sdk = 26)).shortOfMemory)
        assertTrue(DeviceFitRules.assess(phone(6 * gib, only32 = true)).shortOfMemory)
    }

    @Test
    fun `storage is short under three films' worth`() {
        assertTrue(DeviceFitRules.assess(phone(8 * gib, free = 2 * gib)).shortOfStorage)
        assertTrue(DeviceFitRules.assess(phone(8 * gib, free = 0)).shortOfStorage)
        assertFalse(DeviceFitRules.assess(phone(8 * gib, free = 3 * gib)).shortOfStorage)
        assertFalse(DeviceFitRules.assess(phone(8 * gib, free = 100 * gib)).any)
    }

    @Test
    fun `the desktop reads free disk only`() {
        val desktop = DeviceProbe(freeStorageBytes = gib)
        assertEquals(DeviceFit(shortOfMemory = false, shortOfStorage = true), DeviceFitRules.assess(desktop))
        assertEquals(DeviceFit.Fine, DeviceFitRules.assess(DeviceProbe(freeStorageBytes = 500 * gib)))
        assertEquals("nothing read, nothing claimed", DeviceFit.Fine, DeviceFitRules.assess(DeviceProbe()))
    }

    private val defaults = LightSettings(lowStorageMode = false, seekThumbnails = true, lighterPictures = false, reduceMotion = false)

    @Test
    fun `short of both, every change, Low storage mode first`() {
        val all = DeviceFitRules.recommendations(DeviceFit(true, true), defaults, seekThumbnailsShown = true)
        assertEquals(Recommendation.entries.toList(), all)
    }

    @Test
    fun `short of storage only, Low storage mode alone`() {
        assertEquals(
            listOf(Recommendation.LowStorageMode),
            DeviceFitRules.recommendations(DeviceFit(false, true), defaults, seekThumbnailsShown = true),
        )
    }

    @Test
    fun `short of memory only, thumbnails, pictures and motion, not storage`() {
        assertEquals(
            listOf(Recommendation.NoSeekThumbnails, Recommendation.LighterPictures, Recommendation.ReduceMotion),
            DeviceFitRules.recommendations(DeviceFit(true, false), defaults, seekThumbnailsShown = true),
        )
    }

    @Test
    fun `thumbnails are not offered where they never show`() {
        assertFalse(
            Recommendation.NoSeekThumbnails in
                DeviceFitRules.recommendations(DeviceFit(true, false), defaults, seekThumbnailsShown = false),
        )
    }

    @Test
    fun `the desktop is offered storage only`() {
        assertEquals(
            listOf(Recommendation.LowStorageMode),
            DeviceFitRules.recommendations(DeviceFit(true, true), defaults, seekThumbnailsShown = false, pictures = false),
        )
    }

    @Test
    fun `what is already set is not offered again, and a fine device gets nothing`() {
        val applied = LightSettings(lowStorageMode = true, seekThumbnails = false, lighterPictures = true, reduceMotion = true)
        assertEquals(emptyList<Recommendation>(), DeviceFitRules.recommendations(DeviceFit(true, true), applied, seekThumbnailsShown = true))
        assertEquals(emptyList<Recommendation>(), DeviceFitRules.recommendations(DeviceFit.Fine, defaults, seekThumbnailsShown = true))
    }

    @Test
    fun `applying writes each setting, which then stops being recommended, and each undoes alone`() = runBlocking {
        val store = SettingsStore(
            SettingsStore.openDataStore(Files.createTempDirectory("tm-fit").resolve(SettingsStore.FILE_NAME).toFile()),
        )
        assertEquals(defaults, store.lightSettings.first())
        store.applyRecommendations(Recommendation.entries)
        val now = store.lightSettings.first()
        assertEquals(LightSettings(lowStorageMode = true, seekThumbnails = false, lighterPictures = true, reduceMotion = true), now)
        assertTrue(DeviceFitRules.recommendations(DeviceFit(true, true), now, seekThumbnailsShown = true).isEmpty())
        store.setReduceMotion(false)
        assertEquals(
            listOf(Recommendation.ReduceMotion),
            DeviceFitRules.recommendations(DeviceFit(true, true), store.lightSettings.first(), seekThumbnailsShown = true),
        )
    }

    @Test
    fun `the card and the hint are once per install, sign outs included`() = runBlocking {
        val store = SettingsStore(
            SettingsStore.openDataStore(Files.createTempDirectory("tm-fit-once").resolve(SettingsStore.FILE_NAME).toFile()),
        )
        assertFalse(store.deviceCardDismissed.first())
        assertFalse(store.deviceHintShown.first())
        store.dismissDeviceCard()
        store.markDeviceHintShown()
        store.setLighterPictures(true)
        store.clearEverything()
        assertTrue(store.deviceCardDismissed.first())
        assertTrue(store.deviceHintShown.first())
        assertTrue("the device's own setting outlives the account", store.lighterPictures.first())
    }
}
