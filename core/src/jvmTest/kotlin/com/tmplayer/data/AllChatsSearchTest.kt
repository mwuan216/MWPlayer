package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllChatsSearchTest {

    @Test
    fun `the fallback asks for the longest word, and only for queries of several words`() {
        assertEquals("harbour", AllChatsSearch.fallbackQuery("harbour notes 2"))
        assertNull(AllChatsSearch.fallbackQuery("harbour"))
        assertNull(AllChatsSearch.fallbackQuery("  "))
    }

    @Test
    fun `a search is done once both kinds have run out`() {
        assertFalse(AllChatsCursor().done)
        assertFalse(AllChatsCursor(videoDone = true).done)
        assertTrue(AllChatsCursor(videoDone = true, documentDone = true).done)
    }
}
