package com.tiberiptv.fire

import android.content.Context
import android.os.Build
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

internal object DownloadStorage {
    fun existingFile(context: Context, item: XtreamModels.StreamItem, storedPath: String): File? {
        val storedFile = if (storedPath.isNotEmpty()) File(storedPath) else null
        if (storedFile?.isFile == true) {
            return storedFile
        }
        val fileName = fileName(item)
        return directories(context, create = false)
            .map { directory -> File(directory, fileName) }
            .firstOrNull { file -> file.isFile }
    }

    fun targetFile(
        context: Context,
        item: XtreamModels.StreamItem,
        storedPath: String,
        minAvailableBytes: Long = 0L
    ): File =
        existingFile(context, item, storedPath)
            ?: File(preferredDirectory(context, minAvailableBytes), fileName(item))

    fun downloadedSize(context: Context, item: XtreamModels.StreamItem, storedPath: String): Long =
        existingFile(context, item, storedPath)?.length() ?: -1L

    fun directories(context: Context, create: Boolean = true): List<File> {
        val externalDirectories = context.getExternalFilesDirs(null)
            .filterNotNull()
            .map { directory -> File(directory, "downloads") }
        val externalMediaDirectories = externalMediaDirectories(context)
            .map { directory -> File(directory, "downloads") }
        val storageManagerDirectories = storageManagerVolumeRoots(context)
            .let { roots -> appSpecificDownloadDirectories(roots, context.packageName) }
        val fallbackDirectory = File(context.filesDir, "downloads")
        return usableDirectories(
            externalDirectories + externalMediaDirectories + storageManagerDirectories + fallbackDirectory,
            create
        )
    }

    internal fun appSpecificDownloadDirectories(volumeRoots: List<File>, packageName: String): List<File> =
        volumeRoots.map { volumeRoot -> File(volumeRoot, "Android/data/$packageName/files/downloads") }

    internal fun usableDirectories(candidates: List<File>, create: Boolean = true): List<File> =
        candidates
            .distinctBy { directory -> directory.absolutePath }
            .filter { directory ->
                if (create && !directory.exists()) {
                    directory.mkdirs()
                }
                directory.exists() || !create
            }

    fun preferredDirectory(context: Context, minAvailableBytes: Long = 0L): File {
        val directories = directories(context)
        val candidates = directories
            .filter { directory -> minAvailableBytes <= 0L || availableBytes(directory) >= minAvailableBytes }
            .ifEmpty { directories }
        return candidates.maxByOrNull { directory -> availableBytes(directory) }
            ?: File(context.filesDir, "downloads").also { directory -> directory.mkdirs() }
    }

    fun availableBytes(directory: File): Long {
        return try {
            val stat = StatFs(statTarget(directory).absolutePath)
            stat.availableBytes
        } catch (_: Exception) {
            directory.freeSpace
        }
    }

    fun totalBytes(directory: File): Long {
        return try {
            val stat = StatFs(statTarget(directory).absolutePath)
            stat.totalBytes
        } catch (_: Exception) {
            directory.totalSpace
        }
    }

    private fun statTarget(directory: File): File =
        if (directory.exists()) directory else directory.parentFile ?: directory

    @Suppress("DEPRECATION")
    private fun externalMediaDirectories(context: Context): List<File> =
        try {
            context.externalMediaDirs.filterNotNull()
        } catch (_: LinkageError) {
            emptyList()
        }

    private fun storageManagerVolumeRoots(context: Context): List<File> {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager ?: return emptyList()
        return try {
            storageManager.storageVolumes.mapNotNull { volume -> storageVolumeDirectory(volume) }
        } catch (_: LinkageError) {
            emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun storageVolumeDirectory(volume: StorageVolume): File? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory
        } else {
            storageVolumeDirectoryByReflection(volume)
        }

    private fun storageVolumeDirectoryByReflection(volume: StorageVolume): File? =
        try {
            val path = volume.javaClass.getMethod("getPath").invoke(volume) as? String
            path?.let(::File)
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: SecurityException) {
            null
        }

    private fun fileName(item: XtreamModels.StreamItem): String =
        "${safeFileName(item.title)}-${item.id}.${item.extension.ifEmpty { "mp4" }}"

    private fun safeFileName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "video" }
}
