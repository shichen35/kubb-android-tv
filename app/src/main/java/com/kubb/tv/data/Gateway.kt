package com.kubb.tv.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

/** The site's domain. Every API and image host hangs off it, so a domain move is a one-line change. */
const val DOMAIN = "kubb.tv"

/** A nonzero `data.code`: a valid business rejection, never retried. */
class ApiError(message: String, val code: Int) : IOException(message)

/** Sends one request: a GET when [form] is null, else a form POST. Returns the body text. */
fun interface Transport {
    suspend fun send(url: String, form: String?): String
}

/** A desktop Chrome UA. The gateway does not care; the video CDN rejects curl/okhttp UAs. */
const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

class OkHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).build(),
) : Transport {
    override suspend fun send(url: String, form: String?): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        if (form != null) request.post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
        val call = client.newCall(request.build())
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val result = runCatching {
                            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                            it.body!!.string()
                        }
                        if (cont.isActive) result.fold(cont::resume, cont::resumeWithException)
                    }
                }
            })
        }
    }
}

/**
 * The single request entry point for the API gateway: build the query (+ v=1), sign, send, check `data.code`, return
 * `data.info`. Each GET attempt has a 3 s cutoff and is retried up to 3 times
 * with a fresh signature; only network, timeout and parse errors are retried.
 * The one POST (search) gets 8 s and no retry.
 */
class Gateway(private val transport: Transport, private val now: () -> Long = System::currentTimeMillis) {
    /** True once the gateway has asked for a human check (code 5), which a TV cannot pass. */
    @Volatile
    var challenged = false
        private set

    suspend fun get(host: String, path: String, params: List<Pair<String, Any?>>): Any? {
        val qs = buildQuery(params + ("v" to 1))
        var last: Exception? = null
        repeat(RETRY_LIMIT) {
            val url = "$host$path?$qs&${Signature.signParams(qs, now())}"
            try {
                val text = withTimeoutOrNull(TIMEOUT_MS) { transport.send(url, null) }
                    ?: throw IOException("timeout")
                return unwrap(text)
            } catch (e: ApiError) {
                throw e
            } catch (e: IOException) {
                last = e
            } catch (e: org.json.JSONException) {
                last = IOException("bad response", e)
            }
        }
        throw last ?: IOException("request failed")
    }

    /** Like the site, the form body is signed and the URL query is sent as is. */
    suspend fun post(host: String, path: String, query: List<Pair<String, Any?>>, form: List<Pair<String, Any?>>): Any? {
        val url = "$host$path?${buildQuery(query + ("v" to 1))}"
        val body = buildQuery(form)
        val text = withTimeoutOrNull(POST_TIMEOUT_MS) { transport.send(url, "$body&${Signature.signParams(body, now())}") }
            ?: throw IOException("timeout")
        return try {
            unwrap(text)
        } catch (e: org.json.JSONException) {
            throw IOException("bad response", e)
        }
    }

    fun unwrap(text: String): Any? {
        val data = JSONObject(text).optJSONObject("data") ?: throw IOException("bad response")
        val code = data.opt("code") as? Number ?: throw IOException("bad response")
        when (code.toInt()) {
            0 -> return data.opt("info")
            CHALLENGE_CODE -> {
                challenged = true
                throw ApiError("访问受限，请稍后再试", CHALLENGE_CODE)
            }
            else -> throw ApiError(data.optString("msg").ifBlank { "code $code" }, code.toInt())
        }
    }

    companion object {
        const val M10 = "https://m10.$DOMAIN" // lists, detail, episodes, play
        const val RANK = "https://rankv21.$DOMAIN" // ranking, search, hot keywords
        private const val TIMEOUT_MS = 3000L
        private const val POST_TIMEOUT_MS = 8000L
        private const val RETRY_LIMIT = 3
        private const val CHALLENGE_CODE = 5

        /** Params in the order given (the signature covers it); null values are left out, commas stay literal. */
        fun buildQuery(params: List<Pair<String, Any?>>): String =
            params.filter { it.second != null }.joinToString("&") { (k, v) -> "$k=${encodeUriComponent(v.toString())}" }

        // JavaScript's encodeURIComponent, but leaving commas literal as the site sends `cid=0,1,4`.
        private fun encodeUriComponent(s: String): String = buildString {
            for (b in s.toByteArray()) {
                val c = b.toInt().toChar()
                if (b >= 0 && (c.isLetterOrDigit() || c in "-_.!~*'(),")) append(c) else append("%%%02X".format(b.toInt() and 0xff))
            }
        }
    }
}
