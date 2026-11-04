package com.kubb.tv.data

import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale

/**
 * Request signature for the site's API gateway.
 *
 *   vv = md5(pub + "&" + lower(canonicalQuery(qs)) + "&" + privateKey)
 *
 * GET appends `&vv=..&pub=..` to the query; POST appends it to the form body.
 * Key mode is the site's own fallback: pub is the millisecond timestamp and the
 * private key comes from a fixed table by pub % 8. The gateway does not check
 * freshness, so the TV clock does not matter.
 */
object Signature {
    private val FALLBACK_KEYS = listOf(
        "version001", "vers1on001", "vers1on00i", "bersion001",
        "vcrsion001", "versi0n001", "versio_001", "version0o1",
    )

    fun md5(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * Mirrors the site's get_query(): decode each value and turn + into spaces,
     * keeping the original order. Signing the raw percent-encoded query breaks
     * every request with a Chinese value.
     */
    fun canonicalQuery(qs: String): String = qs.split('&').filter { it.isNotEmpty() }.joinToString("&") { kv ->
        val i = kv.indexOf('=')
        if (i < 0) kv else kv.substring(0, i) + "=" + URLDecoder.decode(kv.substring(i + 1), "UTF-8").replace('+', ' ')
    }

    fun sign(qs: String, pub: String, privateKey: String): String =
        md5("$pub&${canonicalQuery(qs).lowercase(Locale.ROOT)}&$privateKey")

    /** `vv=..&pub=..` to append to a GET query or a POST body. */
    fun signParams(qs: String, now: Long = System.currentTimeMillis()): String {
        val pub = now.toString()
        return "vv=${sign(qs, pub, FALLBACK_KEYS[(now % 8).toInt()])}&pub=$pub"
    }
}
