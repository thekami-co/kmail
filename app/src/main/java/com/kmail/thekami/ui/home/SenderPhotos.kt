package com.kmail.thekami.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Sender profile pictures from Gravatar. Only an MD5 hash of the address leaves the device.
 * Pictures are kept in memory and in the cache folder. Senders with no picture are remembered for
 * a week so the list never asks again on every scroll.
 */
object SenderPhotos {
    private const val MISS_TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_BYTES = 300_000

    private val memory = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val missing: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private val gate = Semaphore(4)

    private sealed interface Fetched {
        class Bytes(val data: ByteArray) : Fetched
        data object NoPhoto : Fetched
        data object Failed : Fetched
    }

    /** The picture if it is already in memory, so rows that scroll back in show it with no flicker. */
    fun cached(email: String): ImageBitmap? = memory.get(keyOf(email))?.asImageBitmap()

    suspend fun load(context: Context, email: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = keyOf(email)
        memory.get(key)?.let { return@withContext it.asImageBitmap() }
        if (key in missing) return@withContext null

        val dir = File(context.applicationContext.cacheDir, "sender-photos").apply { mkdirs() }
        val photoFile = File(dir, "$key.img")
        val noneFile = File(dir, "$key.none")

        if (photoFile.exists()) {
            val bitmap = decode(photoFile.readBytes())
            if (bitmap != null) {
                memory.put(key, bitmap)
                return@withContext bitmap.asImageBitmap()
            }
            photoFile.delete()
        }
        if (noneFile.exists() && System.currentTimeMillis() - noneFile.lastModified() < MISS_TTL_MS) {
            missing += key
            return@withContext null
        }

        when (val result = gate.withPermit { fetch(key) }) {
            is Fetched.Bytes -> {
                val bitmap = decode(result.data)
                if (bitmap == null) {
                    missing += key
                    null
                } else {
                    runCatching { photoFile.writeBytes(result.data) }
                    memory.put(key, bitmap)
                    bitmap.asImageBitmap()
                }
            }
            Fetched.NoPhoto -> {
                runCatching { noneFile.writeText("") }
                missing += key
                null
            }
            Fetched.Failed -> {
                // Offline or a server hiccup: skip until the app restarts, but do not remember it on disk.
                missing += key
                null
            }
        }
    }

    private fun fetch(key: String): Fetched = try {
        val connection = URL("https://www.gravatar.com/avatar/$key?s=128&d=404").openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        try {
            when (connection.responseCode) {
                200 -> {
                    val data = connection.inputStream.use { it.readBytes() }
                    if (data.size in 1..MAX_BYTES) Fetched.Bytes(data) else Fetched.Failed
                }
                404 -> Fetched.NoPhoto
                else -> Fetched.Failed
            }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        Fetched.Failed
    }

    private fun decode(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private fun keyOf(email: String): String =
        MessageDigest.getInstance("MD5")
            .digest(email.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
}
