package com.tmplayer.data

import dev.g000sha256.tdl.dto.AddedProxy
import dev.g000sha256.tdl.dto.Proxy
import dev.g000sha256.tdl.dto.ProxyTypeHttp
import dev.g000sha256.tdl.dto.ProxyTypeMtproto
import dev.g000sha256.tdl.dto.ProxyTypeSocks5
import java.net.URLDecoder

/** The three kinds of proxy TDLib can go through. */
enum class ProxyKind { Mtproto, Socks5, Http }

/**
 * One proxy, as the viewer gave it: a link pasted in, or the fields typed by hand.
 *
 * Kept apart from TDLib's own class so the parsing and the checks are plain values a test can
 * compare, and so the screen can hold a half typed one without TDLib being asked anything.
 */
data class ProxySpec(
    val kind: ProxyKind,
    val server: String,
    val port: Int,
    /** MTProto only: the secret, hex or base64 as the link carried it. */
    val secret: String = "",
    /** SOCKS5 and HTTP only, both optional. */
    val username: String = "",
    val password: String = "",
) {
    /** Whether this can be handed to TDLib: a server, a port that exists, and a secret for MTProto. */
    val isComplete: Boolean
        get() = server.isNotBlank() && port in 1..65535 && (kind != ProxyKind.Mtproto || secret.isNotBlank())

    /** "host:port", how the list names it. */
    val address: String get() = if (':' in server && !server.startsWith('[')) "[$server]:$port" else "$server:$port"

    fun toTdlib(): Proxy = Proxy(
        server.trim(),
        port,
        when (kind) {
            ProxyKind.Mtproto -> ProxyTypeMtproto(secret.trim())
            ProxyKind.Socks5 -> ProxyTypeSocks5(username, password)
            ProxyKind.Http -> ProxyTypeHttp(username, password, false)
        },
    )

    companion object {
        fun of(proxy: Proxy): ProxySpec = when (val type = proxy.type) {
            is ProxyTypeMtproto -> ProxySpec(ProxyKind.Mtproto, proxy.server, proxy.port, secret = type.secret)
            is ProxyTypeSocks5 -> ProxySpec(ProxyKind.Socks5, proxy.server, proxy.port, username = type.username, password = type.password)
            is ProxyTypeHttp -> ProxySpec(ProxyKind.Http, proxy.server, proxy.port, username = type.username, password = type.password)
            else -> ProxySpec(ProxyKind.Socks5, proxy.server, proxy.port)
        }
    }
}

/** A proxy TDLib has stored, with the id the other proxy calls take. */
data class ProxyEntry(
    val id: Int,
    val spec: ProxySpec,
    val enabled: Boolean,
    /** Seconds since the epoch, zero when never used. */
    val lastUsedDate: Int = 0,
) {
    companion object {
        fun of(added: AddedProxy): ProxyEntry =
            ProxyEntry(added.id, ProxySpec.of(added.proxy), added.isEnabled, added.lastUsedDate)
    }
}

/**
 * Reads the links Telegram shares proxies with.
 *
 * `https://t.me/proxy?server=…&port=…&secret=…` and `tg://proxy?…` are MTProto;
 * `https://t.me/socks?server=…&port=…&user=…&pass=…` and `tg://socks?…` are SOCKS5. telegram.me,
 * telegram.dog and a missing scheme are taken as well, since that is how links arrive when copied
 * out of a message. Anything else is null, and the screen says what a proxy link looks like.
 */
object ProxyLinks {

    private val HOSTS = setOf("t.me", "telegram.me", "telegram.dog")

    fun parse(text: String): ProxySpec? {
        val link = text.trim().takeIf { it.isNotEmpty() } ?: return null
        val (place, query) = link.split('?', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val kind = kindOf(place) ?: return null
        val params = parameters(query.substringBefore('#'))
        val server = params["server"].orEmpty().trim()
        val port = params["port"]?.trim()?.toIntOrNull() ?: return null
        val spec = when (kind) {
            ProxyKind.Mtproto -> ProxySpec(kind, server, port, secret = params["secret"].orEmpty().trim())
            else -> ProxySpec(kind, server, port, username = params["user"].orEmpty(), password = params["pass"].orEmpty())
        }
        return spec.takeIf { it.isComplete }
    }

    /** Whether [text] is worth trying as a link rather than as a server name. */
    fun looksLikeLink(text: String): Boolean = kindOf(text.trim().substringBefore('?')) != null

    private fun kindOf(place: String): ProxyKind? {
        val lower = place.lowercase().trimEnd('/')
        if (lower.startsWith("tg://") || lower.startsWith("tg:")) {
            return when (lower.removePrefix("tg://").removePrefix("tg:")) {
                "proxy" -> ProxyKind.Mtproto
                "socks" -> ProxyKind.Socks5
                else -> null
            }
        }
        val bare = lower.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        val host = bare.substringBefore('/')
        if (host !in HOSTS) return null
        return when (bare.substringAfter('/', "")) {
            "proxy" -> ProxyKind.Mtproto
            "socks" -> ProxyKind.Socks5
            else -> null
        }
    }

    private fun parameters(query: String): Map<String, String> =
        query.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = pair.substringBefore('=').lowercase()
            val value = pair.substringAfter('=', "")
            // A plus is kept as a plus: it is part of a base64 secret, not a space.
            key to runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)
        }
}
