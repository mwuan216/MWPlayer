package com.tmplayer.data

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class UsernamesTest {
    @Test
    fun readsTheWaysPeopleTypeAUsername() {
        assertEquals("gokulakannan122", Usernames.clean("@gokulakannan122"))
        assertEquals("gokulakannan122", Usernames.clean("  gokulakannan122 "))
        assertEquals("gokulakannan122", Usernames.clean("https://t.me/gokulakannan122"))
        assertEquals("tmplayer_app", Usernames.clean("t.me/tmplayer_app/123"))
        assertEquals("tmplayer_app", Usernames.clean("telegram.me/tmplayer_app?start=1"))
    }

    @Test
    fun refusesWhatCannotBeAUsername() {
        assertNull(Usernames.clean("abc"))
        assertNull(Usernames.clean("1startswithdigit"))
        assertNull(Usernames.clean("two words"))
        assertNull(Usernames.clean(""))
        assertNull(Usernames.clean("@"))
    }

    @Test
    fun anAtOrAnAddressMeansAUsernameWasAskedFor() {
        assertTrue(Usernames.looksAsked("@gokulakannan122"))
        assertTrue(Usernames.looksAsked("https://t.me/gokulakannan122"))
        assertFalse(Usernames.looksAsked("gokulakannan122"))
        assertFalse(Usernames.looksAsked("@ab"))
    }
}
