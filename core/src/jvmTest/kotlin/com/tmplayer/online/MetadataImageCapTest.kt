package com.tmplayer.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class MetadataImageCapTest {
    @Test
    fun picturesPastTheCapGoOldestFirst() {
        val dir = Files.createTempDirectory("meta").toFile()
        var clock = 1_000_000L
        val cache = MetadataCache(dir, now = { clock }, imageCapBytes = 250)
        cache.putImage("a", ByteArray(100)); clock += 1000
        cache.putImage("b", ByteArray(100)); clock += 1000
        cache.putImage("c", ByteArray(100))
        assertNull("the oldest went", cache.image("a"))
        assertNotNull(cache.image("b"))
        assertNotNull(cache.image("c"))
        dir.deleteRecursively()
    }

    @Test
    fun theNewestStaysEvenWhenItAloneIsOverTheCap() {
        val dir = Files.createTempDirectory("meta").toFile()
        val cache = MetadataCache(dir, imageCapBytes = 50)
        cache.putImage("big", ByteArray(100))
        assertEquals(100L, cache.image("big")?.length())
        dir.deleteRecursively()
    }
}

class MetadataImagePurgeTest {
    @Test
    fun purgingImagesKeepsTheAnswers() {
        val dir = Files.createTempDirectory("meta").toFile()
        val cache = MetadataCache(dir)
        cache.putImage("poster", ByteArray(120))
        java.io.File(dir, "answers").apply { mkdirs() }.resolve("a.json").writeText("{}")
        assertEquals(120L, cache.imageBytes())
        cache.purgeImages()
        assertEquals(0L, cache.imageBytes())
        assertNull(cache.image("poster"))
        assertEquals(true, java.io.File(dir, "answers/a.json").isFile)
        dir.deleteRecursively()
    }
}
