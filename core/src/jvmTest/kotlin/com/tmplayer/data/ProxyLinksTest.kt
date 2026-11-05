package com.tmplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyLinksTest {

    @Test
    fun `an MTProto link from t me`() {
        val spec = ProxyLinks.parse("https://t.me/proxy?server=proxy.example.org&port=443&secret=ee0123456789abcdef0123456789abcdef")
        assertEquals(ProxySpec(ProxyKind.Mtproto, "proxy.example.org", 443, secret = "ee0123456789abcdef0123456789abcdef"), spec)
    }

    @Test
    fun `the tg scheme, other hosts and no scheme at all`() {
        val want = ProxySpec(ProxyKind.Mtproto, "1.2.3.4", 8888, secret = "dd00ff")
        assertEquals(want, ProxyLinks.parse("tg://proxy?server=1.2.3.4&port=8888&secret=dd00ff"))
        assertEquals(want, ProxyLinks.parse("  t.me/proxy?server=1.2.3.4&port=8888&secret=dd00ff\n"))
        assertEquals(want, ProxyLinks.parse("https://telegram.me/proxy/?server=1.2.3.4&port=8888&secret=dd00ff"))
        assertEquals(want, ProxyLinks.parse("http://www.t.me/proxy?port=8888&server=1.2.3.4&secret=dd00ff#x"))
    }

    @Test
    fun `a SOCKS5 link with and without a login`() {
        assertEquals(
            ProxySpec(ProxyKind.Socks5, "10.0.0.1", 1080, username = "me", password = "p@ss word"),
            ProxyLinks.parse("https://t.me/socks?server=10.0.0.1&port=1080&user=me&pass=p%40ss%20word"),
        )
        assertEquals(ProxySpec(ProxyKind.Socks5, "10.0.0.1", 1080), ProxyLinks.parse("tg://socks?server=10.0.0.1&port=1080"))
    }

    @Test
    fun `a plus in a base64 secret stays a plus`() {
        assertEquals("7gAA+/x", ProxyLinks.parse("tg://proxy?server=a.b&port=443&secret=7gAA+/x")?.secret)
    }

    @Test
    fun `anything that is not a whole proxy link is null`() {
        assertNull(ProxyLinks.parse(""))
        assertNull(ProxyLinks.parse("https://t.me/somechannel"))
        assertNull(ProxyLinks.parse("https://example.com/proxy?server=a&port=1&secret=b"))
        assertNull(ProxyLinks.parse("tg://proxy?server=a.b&port=443"))
        assertNull(ProxyLinks.parse("tg://proxy?server=a.b&port=70000&secret=ab"))
        assertNull(ProxyLinks.parse("tg://socks?server=&port=1080"))
        assertNull(ProxyLinks.parse("1.2.3.4:443"))
        assertTrue(ProxyLinks.looksLikeLink("tg://socks?x"))
        assertFalse(ProxyLinks.looksLikeLink("1.2.3.4"))
    }

    @Test
    fun `a spec survives the trip through TDLib's own type`() {
        val specs = listOf(
            ProxySpec(ProxyKind.Mtproto, "a.b", 443, secret = "dd00"),
            ProxySpec(ProxyKind.Socks5, "a.b", 1080, username = "u", password = "p"),
            ProxySpec(ProxyKind.Http, "a.b", 8080),
        )
        for (spec in specs) assertEquals(spec, ProxySpec.of(spec.toTdlib()))
        assertEquals("[2001:db8::1]:443", ProxySpec(ProxyKind.Http, "2001:db8::1", 443).address)
    }
}
