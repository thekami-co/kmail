package com.kmail.thekami.mail.gmail

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpReply(val code: Int, val body: ByteArray, val retryAfterSeconds: Long?)

/** One HTTPS request. Throws [java.io.IOException] on connection problems. Never logs tokens or bodies. */
interface GmailHttp {
    suspend fun execute(
        method: String,
        url: String,
        accessToken: String,
        body: ByteArray?,
        contentType: String?,
    ): HttpReply
}

@Singleton
class UrlConnectionGmailHttp @Inject constructor() : GmailHttp {
    override suspend fun execute(
        method: String,
        url: String,
        accessToken: String,
        body: ByteArray?,
        contentType: String?,
    ): HttpReply = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "https only" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            HttpReply(code, bytes, connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull())
        } finally {
            connection.disconnect()
        }
    }
}
