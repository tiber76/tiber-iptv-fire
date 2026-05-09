package com.tiberiptv.fire

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object PreloadStreamServer {
    private const val BUFFER_SIZE = 64 * 1024
    private val staticLock = Object()
    private var current: Session? = null

    data class BufferStatus(
        val active: Boolean = false,
        val complete: Boolean = false,
        val downloadedBytes: Long = 0L,
        val servedBytes: Long = 0L,
        val aheadBytes: Long = 0L,
        val safeSeekBytes: Long = 0L,
        val totalBytes: Long = -1L,
        val convertingToDownload: Boolean = false,
        val errorMessage: String? = null
    )

    @JvmStatic
    @Throws(Exception::class)
    fun start(context: Context, remoteUrl: String, maxAheadBytes: Long): Session {
        if (RemoteActionGuard.activeLabel() != RemoteLabels.BUFFER) {
            throw IllegalStateException(UserFacingMessages.remoteGuardUnavailable("Préchargement"))
        }
        synchronized(staticLock) {
            stop()
            val dir = File(context.cacheDir, CacheDirectories.BUFFER)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val session = Session(remoteUrl, File(dir, "stream.part"), maxAheadBytes)
            session.start()
            current = session
            return session
        }
    }

    @JvmStatic
    fun isActive(): Boolean =
        synchronized(staticLock) {
            current?.isActive() == true
        }

    @JvmStatic
    fun isComplete(): Boolean =
        synchronized(staticLock) {
            current?.isComplete() == true
        }

    @JvmStatic
    fun status(): BufferStatus =
        synchronized(staticLock) {
            current?.status() ?: BufferStatus()
        }

    @JvmStatic
    fun stop() {
        synchronized(staticLock) {
            current?.stop()
            current = null
        }
    }

    @JvmStatic
    fun cleanupCache(context: Context) {
        synchronized(staticLock) {
            if (current != null) {
                return
            }
        }
        val dir = File(context.cacheDir, CacheDirectories.BUFFER)
        if (!dir.exists()) {
            return
        }
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }
    }

    class Session(
        private val remoteUrl: String,
        private val cacheFile: File,
        private val maxAheadBytes: Long
    ) {
        private val lock = Object()
        private var serverSocket: ServerSocket? = null
        private var downloadThread: Thread? = null
        private var serverThread: Thread? = null

        @Volatile
        private var activeConnection: HttpURLConnection? = null

        @Volatile
        private var stopped = false

        @Volatile
        private var complete = false

        @Volatile
        private var error: Exception? = null

        @Volatile
        private var downloadedBytes = 0L

        @Volatile
        private var servedBytes = 0L

        @Volatile
        private var totalBytes = -1L

        @Volatile
        private var unlimitedAhead = false

        @Throws(Exception::class)
        fun start() {
            if (cacheFile.exists() && !cacheFile.delete()) {
                throw IllegalStateException("Impossible de preparer le cache temporaire.")
            }
            serverSocket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
            serverThread = Thread(::serveLoop, "tiber-tampon-local")
            downloadThread = Thread(::downloadLoop, "tiber-tampon-fetch")
            serverThread?.start()
            downloadThread?.start()
        }

        fun localUrl(): String = "http://127.0.0.1:${serverSocket?.localPort ?: 0}/stream"

        fun isActive(): Boolean = !stopped && !complete && error == null

        fun isComplete(): Boolean = complete

        fun downloadedBytes(): Long = downloadedBytes

        fun totalBytes(): Long = totalBytes

        fun downloadToEnd() {
            unlimitedAhead = true
            synchronized(lock) {
                lock.notifyAll()
            }
        }

        fun status(): BufferStatus =
            BufferStatus(
                active = isActive(),
                complete = complete,
                downloadedBytes = downloadedBytes,
                servedBytes = servedBytes,
                aheadBytes = max(0L, downloadedBytes - servedBytes),
                safeSeekBytes = safeSeekBytes(),
                totalBytes = totalBytes,
                convertingToDownload = unlimitedAhead,
                errorMessage = error?.message
            )

        fun copyCacheTo(target: File) {
            if (!complete) {
                throw IllegalStateException("Préchargement incomplet.")
            }
            target.parentFile?.mkdirs()
            cacheFile.copyTo(target, overwrite = true)
        }

        fun waitForBuffered(minBytes: Long, timeoutMs: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            synchronized(lock) {
                while (!stopped && error == null && downloadedBytes < minBytes) {
                    if (complete) {
                        return downloadedBytes > 0L
                    }
                    val wait = deadline - System.currentTimeMillis()
                    if (wait <= 0L) {
                        return false
                    }
                    try {
                        lock.wait(min(wait, 500L))
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
                return error == null && downloadedBytes >= minBytes
            }
        }

        fun error(): Exception? = error

        fun stop() {
            stopped = true
            synchronized(lock) {
                lock.notifyAll()
            }
            try {
                serverSocket?.close()
            } catch (_: Exception) {
            }
            downloadThread?.interrupt()
            activeConnection?.disconnect()
            serverThread?.interrupt()
            deleteCacheFile()
        }

        private fun deleteCacheFile() {
            if (cacheFile.exists() && !cacheFile.delete()) {
                cacheFile.deleteOnExit()
            }
        }

        private fun downloadLoop() {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(remoteUrl).openConnection() as HttpURLConnection
                activeConnection = connection
                connection.requestMethod = "GET"
                connection.connectTimeout = 20_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Accept", "*/*")
                connection.setRequestProperty("Connection", "close")
                connection.setRequestProperty("User-Agent", StreamNetwork.USER_AGENT)
                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code")
                }
                totalBytes = connection.contentLengthLong
                BufferedInputStream(connection.inputStream).use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (!stopped) {
                            synchronized(lock) {
                                while (!stopped &&
                                    error == null &&
                                    !unlimitedAhead &&
                                    downloadedBytes - servedBytes >= maxAheadBytes
                                ) {
                                    lock.wait(500L)
                                }
                            }
                            val read = input.read(buffer)
                            if (read == -1) {
                                break
                            }
                            if ((cacheFile.parentFile?.usableSpace ?: Long.MAX_VALUE) < StoragePolicy.DOWNLOAD_SPACE_MARGIN_BYTES) {
                                throw IllegalStateException("Stockage presque plein.")
                            }
                            output.write(buffer, 0, read)
                            output.flush()
                            synchronized(lock) {
                                downloadedBytes += read.toLong()
                                lock.notifyAll()
                            }
                        }
                    }
                }
                synchronized(lock) {
                    complete = true
                    lock.notifyAll()
                }
            } catch (exception: Exception) {
                if (!stopped) {
                    error = exception
                    synchronized(lock) {
                        lock.notifyAll()
                    }
                }
            } finally {
                connection?.disconnect()
                if (activeConnection === connection) {
                    activeConnection = null
                }
            }
        }

        private fun serveLoop() {
            while (!stopped) {
                try {
                    val socket = serverSocket?.accept() ?: return
                    Thread({ serveClient(socket) }, "tiber-tampon-client").start()
                } catch (exception: Exception) {
                    if (!stopped) {
                        error = exception
                    }
                    return
                }
            }
        }

        private fun serveClient(socket: Socket) {
            try {
                socket.use { client ->
                    BufferedReader(InputStreamReader(client.getInputStream())).use { reader ->
                        BufferedOutputStream(client.getOutputStream()).use { output ->
                            var offset = 0L
                            var rangeRequested = false
                            var headRequest = false
                            val requestLine = reader.readLine()
                            if (requestLine != null) {
                                headRequest = requestLine.uppercase(Locale.US).startsWith("HEAD ")
                            }
                            while (true) {
                                val line = reader.readLine()
                                if (line == null || line.isEmpty()) {
                                    break
                                }
                                val lower = line.lowercase(Locale.US)
                                if (lower.startsWith("range:")) {
                                    rangeRequested = true
                                    offset = parseRangeStart(lower)
                                }
                            }
                            val rangeAllowed = rangeRequested && canServeRange(offset)
                            if (rangeRequested && !rangeAllowed) {
                                writeRangeUnavailable(output)
                                return
                            }
                            val serveOffset = if (rangeAllowed) offset else 0L
                            writeHeaders(output, serveOffset, rangeAllowed)
                            if (!headRequest) {
                                streamFromCache(output, serveOffset)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        private fun parseRangeStart(header: String): Long {
            val marker = header.indexOf("bytes=")
            if (marker < 0) {
                return 0L
            }
            val end = header.indexOf('-', marker)
            val value = if (end < 0) header.substring(marker + 6) else header.substring(marker + 6, end)
            return try {
                max(0L, value.trim().toLong())
            } catch (_: Exception) {
                0L
            }
        }

        private fun canServeRange(offset: Long): Boolean =
            (totalBytes <= 0L || offset < totalBytes) && (complete || offset <= safeSeekBytes())

        private fun safeSeekBytes(): Long =
            max(0L, downloadedBytes - StoragePolicy.BUFFER_SEEK_SAFETY_BYTES)

        @Throws(Exception::class)
        private fun writeHeaders(output: OutputStream, offset: Long, rangeRequested: Boolean) {
            val total = totalBytes
            val headers = StringBuilder()
            if (rangeRequested && total > 0L) {
                headers.append("HTTP/1.1 206 Partial Content\r\n")
                headers.append("Content-Range: bytes ")
                    .append(offset)
                    .append("-")
                    .append(total - 1L)
                    .append("/")
                    .append(total)
                    .append("\r\n")
            } else {
                headers.append("HTTP/1.1 200 OK\r\n")
            }
            headers.append("Content-Type: ").append(contentType()).append("\r\n")
                .append("Connection: close\r\n")
            if ((complete || safeSeekBytes() > 0L) && total > 0L) {
                headers.append("Accept-Ranges: bytes\r\n")
            }
            if (complete && total > 0L && offset < total) {
                headers.append("Content-Length: ").append(total - offset).append("\r\n")
            }
            headers.append("\r\n")
            output.write(headers.toString().toByteArray())
            output.flush()
        }

        @Throws(Exception::class)
        private fun writeRangeUnavailable(output: OutputStream) {
            val headers = StringBuilder()
                .append("HTTP/1.1 416 Range Not Satisfiable\r\n")
                .append("Connection: close\r\n")
            if (totalBytes > 0L) {
                headers.append("Content-Range: bytes */").append(totalBytes).append("\r\n")
            }
            headers.append("\r\n")
            output.write(headers.toString().toByteArray())
            output.flush()
        }

        private fun contentType(): String {
            val path = remoteUrl.substringBefore('?').lowercase(Locale.US)
            return when {
                path.endsWith(".mp4") -> "video/mp4"
                path.endsWith(".mkv") -> "video/x-matroska"
                path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
                path.endsWith(".ts") -> "video/mp2t"
                else -> "application/octet-stream"
            }
        }

        @Throws(Exception::class)
        private fun streamFromCache(output: OutputStream, offset: Long) {
            val buffer = ByteArray(BUFFER_SIZE)
            var position = offset
            RandomAccessFile(cacheFile, "r").use { input ->
                while (!stopped) {
                    val available: Long
                    synchronized(lock) {
                        while (!stopped &&
                            error == null &&
                            downloadedBytes <= position &&
                            !complete
                        ) {
                            lock.wait(500L)
                        }
                        if (error != null && downloadedBytes <= position) {
                            throw error as Exception
                        }
                        available = downloadedBytes - position
                        if (available <= 0L && complete) {
                            return
                        }
                    }
                    if (available <= 0L) {
                        continue
                    }
                    val toRead = min(buffer.size.toLong(), available).toInt()
                    input.seek(position)
                    val read = input.read(buffer, 0, toRead)
                    if (read <= 0) {
                        continue
                    }
                    output.write(buffer, 0, read)
                    output.flush()
                    position += read.toLong()
                    synchronized(lock) {
                        servedBytes = max(servedBytes, position)
                        lock.notifyAll()
                    }
                }
            }
        }
    }
}
