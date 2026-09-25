package dev.tihulu.youtubetv.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class ExtractorDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val blockedDomains = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adservice.google.com",
        "ads.youtube.com"
    )

    override fun execute(request: Request): Response {
        val host = request.url().toHttpUrl().host
        if (isBlocked(host)) {
            throw IOException("Blocked advertising/tracking host: $host")
        }

        val bodyBytes = request.dataToSend()
        val method = request.httpMethod().uppercase()
        val requestBody = if (method == "GET" || method == "HEAD") {
            null
        } else {
            (bodyBytes ?: ByteArray(0)).toRequestBody(null)
        }

        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(method, requestBody)

        if (request.headers().keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/136.0 Safari/537.36"
            )
        }

        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }

        client.newCall(builder.build()).execute().use { response ->
            val responseBody = if (method == "HEAD") "" else response.body.string()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                response.request.url.toString()
            )
        }
    }

    private fun isBlocked(host: String): Boolean =
        blockedDomains.any { blocked -> host == blocked || host.endsWith(".$blocked") }
}
