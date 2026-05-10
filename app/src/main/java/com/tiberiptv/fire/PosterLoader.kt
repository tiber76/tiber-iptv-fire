package com.tiberiptv.fire

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

class PosterLoader(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val diskDir = File(context.cacheDir, CacheDirectories.POSTERS)

    init {
        if (!diskDir.exists()) {
            diskDir.mkdirs()
        }
    }

    fun load(url: String?, target: ImageView, placeholderColor: Int) {
        val key = url?.trim().orEmpty()
        if (key.isEmpty()) {
            if (target.tag != null || target.drawable != null) {
                target.setImageDrawable(null)
                target.setBackgroundColor(placeholderColor)
                target.tag = null
            }
            return
        }

        if (target.tag == key && target.drawable != null) {
            return
        }

        target.tag = key
        memoryCache.get(key)?.let { cached ->
            target.setImageBitmap(cached)
            return
        }

        target.setImageDrawable(null)
        target.setBackgroundColor(placeholderColor)
        diskExecutor.execute {
            readFromDisk(key)?.let { diskBitmap ->
                memoryCache.put(key, diskBitmap)
                main.post {
                    if (key == target.tag) {
                        target.setImageBitmap(diskBitmap)
                    }
                }
                return@execute
            }

            val requestGeneration = pauseGeneration
            networkExecutor.execute {
                fetch(key, requestGeneration)?.let { fetchedBitmap ->
                    memoryCache.put(key, fetchedBitmap)
                    writeToDisk(key, fetchedBitmap)
                    main.post {
                        if (key == target.tag) {
                            target.setImageBitmap(fetchedBitmap)
                        }
                    }
                }
            }
        }
    }

    fun shutdown() {
        // Shared application queue; keep it alive so posters can retry after navigation.
    }

    fun memoryCacheBytes(): Int = memoryCache.size()

    fun memoryCacheMaxBytes(): Int = memoryCache.maxSize()

    fun diskFileCount(): Int =
        (diskDir.listFiles()?.size ?: 0) + (legacyDiskDir.listFiles()?.size ?: 0)

    fun diskCacheBytes(): Long = diskUsage(diskDir) + diskUsage(legacyDiskDir)

    fun clearCache() {
        memoryCache.evictAll()
        clearDirectory(diskDir)
        clearDirectory(legacyDiskDir)
    }

    private fun readFromDisk(url: String): Bitmap? {
        val file = File(diskDir, "${hash(url)}.jpg")
        if (!file.exists()) {
            return null
        }
        return try {
            decodeSampled(file)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeToDisk(url: String, bitmap: Bitmap) {
        val file = File(diskDir, "${hash(url)}.jpg")
        try {
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_CACHE_QUALITY, output)
            }
        } catch (_: Exception) {
        }
    }

    private val legacyDiskDir: File
        get() = File(diskDir.parentFile ?: diskDir, CacheDirectories.LEGACY_POSTERS)

    private fun clearDirectory(directory: File) {
        directory.listFiles()?.forEach { file ->
            file.delete()
        }
    }

    companion object {
        private const val MAX_IMAGE_WIDTH = 640
        private const val MAX_IMAGE_HEIGHT = 960
        private const val JPEG_CACHE_QUALITY = 95
        private const val POSTER_GUARD_TIMEOUT_MS = 18_000L
        private const val POSTER_GUARD_RETRY_MS = 180L
        private const val POSTER_PRIORITY_PAUSE_MS = 7_000L
        private val diskExecutor: ExecutorService = Executors.newFixedThreadPool(2)
        private val networkExecutor: ExecutorService = Executors.newSingleThreadExecutor()
        @Volatile private var pauseUntilMs: Long = 0L
        @Volatile private var pauseGeneration: Long = 0L
        @Volatile private var activeConnection: HttpURLConnection? = null
        private val memoryCache: LruCache<String, Bitmap> =
            object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 10L).toInt()) {
                override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
            }

        @Synchronized
        fun pauseRemoteLoading(durationMs: Long = POSTER_PRIORITY_PAUSE_MS) {
            pauseUntilMs = System.currentTimeMillis() + durationMs
            pauseGeneration += 1L
            activeConnection?.disconnect()
            RemoteActionGuard.release(RemoteLabels.POSTER)
        }

        private fun fetch(url: String, generation: Long): Bitmap? {
            val normalizedUrl = normalizeUrl(url) ?: return null
            if (shouldPause(generation)) {
                return null
            }
            if (!acquirePosterSlot()) {
                return null
            }
            var connection: HttpURLConnection? = null
            return try {
                if (shouldPause(generation)) {
                    return null
                }
                connection = URL(normalizedUrl).openConnection() as HttpURLConnection
                activeConnection = connection
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                connection.setRequestProperty("Connection", "close")
                connection.setRequestProperty("User-Agent", "TiberIPTV-Fire/0.1")
                connection.inputStream.use { stream: InputStream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
                if (activeConnection === connection) {
                    activeConnection = null
                }
                RemoteActionGuard.release(RemoteLabels.POSTER)
            }
        }

        private fun acquirePosterSlot(): Boolean {
            val deadline = System.currentTimeMillis() + POSTER_GUARD_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (System.currentTimeMillis() < pauseUntilMs) {
                    return false
                }
                if (RemoteActionGuard.tryAcquire(RemoteLabels.POSTER)) {
                    return true
                }
                try {
                    Thread.sleep(POSTER_GUARD_RETRY_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            return false
        }

        private fun shouldPause(generation: Long): Boolean =
            generation != pauseGeneration || System.currentTimeMillis() < pauseUntilMs

        private fun normalizeUrl(url: String): String? {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) {
                return null
            }
            val withScheme = when {
                trimmed.startsWith("//") -> "https:$trimmed"
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                else -> return null
            }
            return withScheme.replace(" ", "%20")
        }

        private fun decodeSampled(file: File): Bitmap? {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            FileInputStream(file).use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return FileInputStream(file).use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }

        private fun sampleSize(width: Int, height: Int): Int {
            var sample = 1
            while (width / sample > MAX_IMAGE_WIDTH || height / sample > MAX_IMAGE_HEIGHT) {
                sample *= 2
            }
            return max(1, sample)
        }

        private fun hash(value: String): String {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                val bytes = digest.digest(value.toByteArray())
                buildString {
                    for (byte in bytes) {
                        append(String.format("%02x", byte))
                    }
                }
            } catch (_: Exception) {
                value.hashCode().toString(16)
            }
        }

        private fun diskUsage(file: File?): Long {
            if (file == null || !file.exists()) {
                return 0L
            }
            if (file.isFile) {
                return file.length()
            }
            return file.listFiles()?.sumOf { child -> diskUsage(child) } ?: 0L
        }
    }
}
