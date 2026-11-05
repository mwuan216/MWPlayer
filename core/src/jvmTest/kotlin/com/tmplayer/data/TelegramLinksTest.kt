package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramLinksTest {

    @Test
    fun `a public post in every form people paste it`() {
        val expected = TgLink.Post(username = "tmplayer_app", channelId = null, messageId = 123)
        assertEquals(expected, TelegramLinks.parse("https://t.me/tmplayer_app/123"))
        assertEquals(expected, TelegramLinks.parse("t.me/tmplayer_app/123"))
        assertEquals(expected, TelegramLinks.parse("  http://www.telegram.me/tmplayer_app/123/ "))
        assertEquals(expected, TelegramLinks.parse("https://telegram.dog/tmplayer_app/123?single"))
        assertEquals(expected, TelegramLinks.parse("https://t.me/s/tmplayer_app/123"))
        assertEquals(expected, TelegramLinks.parse("tg://resolve?domain=tmplayer_app&post=123"))
    }

    @Test
    fun `a private channel's post by its internal id`() {
        val expected = TgLink.Post(username = null, channelId = 1234567890L, messageId = 45)
        assertEquals(expected, TelegramLinks.parse("https://t.me/c/1234567890/45"))
        assertEquals(expected, TelegramLinks.parse("tg://privatepost?channel=1234567890&post=45"))
        assertEquals("https://t.me/c/1234567890/45", expected.url)
    }

    @Test
    fun `a topic sits between the chat and the post`() {
        val public = TelegramLinks.parse("https://t.me/somegroup/7/310") as TgLink.Post
        assertEquals(7L, public.topicId)
        assertEquals(310L, public.messageId)
        assertEquals("https://t.me/somegroup/7/310", public.url)
        val private = TelegramLinks.parse("https://t.me/c/99887766/12/400") as TgLink.Post
        assertEquals(99887766L, private.channelId)
        assertEquals(12L, private.topicId)
        assertEquals(400L, private.messageId)
    }

    @Test
    fun `a start time is read in seconds, units and clocks`() {
        assertEquals(90, (TelegramLinks.parse("https://t.me/chan_name/5?t=90") as TgLink.Post).startSec)
        assertEquals(83, (TelegramLinks.parse("https://t.me/chan_name/5?t=1m23s") as TgLink.Post).startSec)
        assertEquals(3723, (TelegramLinks.parse("https://t.me/chan_name/5?single&t=1h2m3s") as TgLink.Post).startSec)
        assertEquals(0, (TelegramLinks.parse("https://t.me/chan_name/5?t=nonsense") as TgLink.Post).startSec)
        assertEquals(90, TelegramLinks.startSeconds("1:30"))
        assertEquals(3723, TelegramLinks.startSeconds("1:02:03"))
        assertNull(TelegramLinks.startSeconds(""))
        assertNull(TelegramLinks.startSeconds("abc"))
        assertNull(TelegramLinks.startSeconds("-5"))
    }

    @Test
    fun `a comment link keeps its comment and the start time goes back into the url`() {
        val post = TelegramLinks.parse("https://t.me/chan_name/5?comment=77&t=10") as TgLink.Post
        assertEquals(77L, post.commentId)
        assertEquals("https://t.me/chan_name/5?comment=77&t=10", post.url)
    }

    @Test
    fun `invites in their three spellings`() {
        val expected = TgLink.Invite("AbCdEf123456_-x")
        assertEquals(expected, TelegramLinks.parse("https://t.me/+AbCdEf123456_-x"))
        assertEquals(expected, TelegramLinks.parse("https://t.me/joinchat/AbCdEf123456_-x"))
        assertEquals(expected, TelegramLinks.parse("tg://join?invite=AbCdEf123456_-x"))
        assertEquals("https://t.me/+AbCdEf123456_-x", expected.url)
    }

    @Test
    fun `a phone number link is not an invite`() {
        assertNull(TelegramLinks.parse("https://t.me/+15551234567"))
    }

    @Test
    fun `a bare chat address is a public chat`() {
        assertEquals(TgLink.Public("tmplayer_app"), TelegramLinks.parse("https://t.me/tmplayer_app"))
        assertEquals(TgLink.Public("tmplayer_app"), TelegramLinks.parse("tg://resolve?domain=tmplayer_app"))
    }

    @Test
    fun `telegram's own paths and other sites are not links to open`() {
        assertNull(TelegramLinks.parse("https://t.me/proxy?server=1.2.3.4&port=443&secret=abc"))
        assertNull(TelegramLinks.parse("https://t.me/addstickers/SomePack"))
        assertNull(TelegramLinks.parse("https://t.me/share/url?url=x"))
        assertNull(TelegramLinks.parse("https://example.com/tmplayer_app/123"))
        assertNull(TelegramLinks.parse("@tmplayer_app"))
        assertNull(TelegramLinks.parse("tmplayer app"))
        assertNull(TelegramLinks.parse("https://t.me/tmplayer_app/notanumber"))
        assertNull(TelegramLinks.parse("https://t.me/tmplayer_app/1/2/3"))
        assertNull(TelegramLinks.parse("tg://msg?text=hi"))
    }

    @Test
    fun `a link is found inside a forwarded message's words`() {
        val found = TelegramLinks.find("Part two is here: https://t.me/chan_name/88 enjoy")
        assertEquals(TgLink.Post("chan_name", null, 88), found)
        assertNull(TelegramLinks.find("no links at all"))
    }

    @Test
    fun `only posts and invites are offered to open from search`() {
        assertTrue(TelegramLinks.opensSomething("https://t.me/chan_name/88"))
        assertTrue(TelegramLinks.opensSomething("https://t.me/+AbCdEf123456"))
        assertFalse(TelegramLinks.opensSomething("https://t.me/chan_name"))
        assertFalse(TelegramLinks.opensSomething("chan_name"))
    }

    @Test
    fun `usernames never come out of telegram's own paths`() {
        assertNull(Usernames.clean("https://t.me/joinchat/AbCdEf123456"))
        assertNull(Usernames.clean("https://t.me/proxy?server=x"))
        assertEquals("tmplayer_app", Usernames.clean("https://t.me/s/tmplayer_app"))
        assertEquals("tmplayer_app", Usernames.clean("https://t.me/tmplayer_app/123"))
    }
}
