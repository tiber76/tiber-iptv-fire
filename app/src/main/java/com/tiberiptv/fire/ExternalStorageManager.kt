package com.tiberiptv.fire

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExternalStorageManager(private val context: Context) {
    private val appContext: Context = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        logEnvironment()
    }

    suspend fun listUsbVolumes(): Result<List<UsbVolume>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val storageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val volumes = storageManager.storageVolumes
                    .filter { volume -> volume.isRemovable && !volume.isPrimary }
                    .map { volume ->
                        UsbVolume(
                            uuid = volume.uuid.orEmpty(),
                            description = volume.getDescription(appContext).orEmpty(),
                            state = volume.state.orEmpty(),
                            volume = volume
                        )
                    }
                Log.i(TAG, "usb_volumes count=${volumes.size} values=${volumes.map { it.uuid to it.state }}")
                volumes
            }.recoverCatching { error ->
                Log.e(TAG, "usb_volumes_failed sdk=${Build.VERSION.SDK_INT}", error)
                throw StorageError.NoUsbDetected
            }
        }

    suspend fun requestUsbAccess(activity: Activity, volume: UsbVolume): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            createUsbAccessIntent(volume).mapCatching { intent ->
                try {
                    activity.startActivityForResult(intent, REQUEST_USB_TREE)
                    Unit
                } catch (error: ActivityNotFoundException) {
                    Log.w(TAG, "saf_not_available uuid=${volume.uuid}", error)
                    throw StorageError.SafNotAvailable
                } catch (error: RuntimeException) {
                    Log.w(TAG, "saf_launch_failed uuid=${volume.uuid}", error)
                    throw StorageError.SafNotAvailable
                }
            }
        }

    fun createUsbAccessIntent(volume: UsbVolume): Result<Intent> =
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                throw StorageError.SafNotAvailable
            }
            volume.volume.createOpenDocumentTreeIntent().apply {
                addFlags(USB_PERMISSION_FLAGS)
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, data)
            }
        }.recoverCatching { error ->
            if (error is StorageError) throw error
            throw StorageError.SafNotAvailable
        }

    fun persistUsbAccess(uri: Uri): Result<Uri> =
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, USB_PERMISSION_FLAGS)
            preferences.edit().putString(KEY_USB_TREE_URI, uri.toString()).apply()
            Log.i(TAG, "usb_tree_persisted uri=$uri")
            uri
        }.recoverCatching { error ->
            Log.e(TAG, "usb_tree_persist_failed uri=$uri", error)
            throw StorageError.PermissionDenied
        }

    suspend fun writeFile(fileName: String, mimeType: String, content: ByteArray): Result<Uri> =
        withContext(Dispatchers.IO) {
            val treeUri = storedTreeUri()
            if (treeUri != null) {
                writeDocumentFile(treeUri, fileName, mimeType, content)
            } else {
                writeAppSpecificFallback(fileName, content)
            }
        }

    suspend fun readFile(uri: Uri): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                    ?: throw StorageError.PermissionDenied
            }.recoverCatching { error ->
                if (error is StorageError) throw error
                throw StorageError.WriteFailed(error)
            }
        }

    suspend fun listFiles(directoryUri: Uri): List<DocumentFile> =
        withContext(Dispatchers.IO) {
            DocumentFile.fromTreeUri(appContext, directoryUri)?.listFiles()?.toList().orEmpty()
        }

    fun hasUsbAccess(): Boolean =
        storedTreeUri() != null || appSpecificUsbDirectories(create = false).isNotEmpty()

    fun revokeUsbAccess() {
        val uri = storedTreeUri()
        if (uri != null) {
            runCatching { appContext.contentResolver.releasePersistableUriPermission(uri, USB_PERMISSION_FLAGS) }
        }
        preferences.edit().remove(KEY_USB_TREE_URI).apply()
        Log.i(TAG, "usb_tree_revoked")
    }

    fun appSpecificUsbDirectories(create: Boolean = true): List<File> {
        val usbVolumes = runCatching { listUsbVolumesBlocking() }.getOrDefault(emptyList())
        val directories = usbVolumes.mapNotNull { volume -> appSpecificDirectory(volume) }
        return directories.filter { directory ->
            if (create && !directory.exists()) {
                directory.mkdirs()
            }
            directory.exists() || !create
        }
    }

    private fun listUsbVolumesBlocking(): List<UsbVolume> {
        val storageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        return storageManager.storageVolumes
            .filter { volume -> volume.isRemovable && !volume.isPrimary }
            .map { volume ->
                UsbVolume(
                    uuid = volume.uuid.orEmpty(),
                    description = volume.getDescription(appContext).orEmpty(),
                    state = volume.state.orEmpty(),
                    volume = volume
                )
            }
    }

    private fun appSpecificDirectory(volume: UsbVolume): File? {
        val candidates = appContext.getExternalFilesDirs(null).filterNotNull()
        val volumeDirectory = candidates.firstOrNull { directory ->
            volume.uuid.isNotBlank() && directory.absolutePath.contains(volume.uuid, ignoreCase = true)
        } ?: candidates.drop(1).maxByOrNull { directory -> directory.freeSpace }
        val downloadDirectory = volumeDirectory?.let { directory -> File(directory, APP_SPECIFIC_DOWNLOADS) }
        Log.i(
            TAG,
            "app_specific_fallback uuid=${volume.uuid} directory=${downloadDirectory?.absolutePath.orEmpty()}"
        )
        return downloadDirectory
    }

    private fun writeDocumentFile(
        treeUri: Uri,
        fileName: String,
        mimeType: String,
        content: ByteArray
    ): Result<Uri> =
        runCatching {
            val tree = DocumentFile.fromTreeUri(appContext, treeUri) ?: throw StorageError.PermissionDenied
            val document = tree.findFile(fileName)?.takeIf { file -> file.exists() }
                ?: tree.createFile(mimeType, fileName)
                ?: throw StorageError.PermissionDenied
            appContext.contentResolver.openOutputStream(document.uri, "wt")?.use { output ->
                output.write(content)
            } ?: throw StorageError.PermissionDenied
            Log.i(TAG, "write_document_file uri=${document.uri} bytes=${content.size}")
            document.uri
        }.recoverCatching { error ->
            if (error is StorageError) throw error
            Log.e(TAG, "write_document_file_failed fileName=$fileName", error)
            throw StorageError.WriteFailed(error)
        }

    private fun writeAppSpecificFallback(fileName: String, content: ByteArray): Result<Uri> =
        runCatching {
            val directory = appSpecificUsbDirectories(create = true).firstOrNull()
                ?: throw StorageError.NoUsbDetected
            val file = File(directory, fileName)
            file.outputStream().use { output -> output.write(content) }
            Log.i(TAG, "write_app_specific_fallback path=${file.absolutePath} bytes=${content.size}")
            Uri.fromFile(file)
        }.recoverCatching { error ->
            if (error is StorageError) throw error
            Log.e(TAG, "write_app_specific_fallback_failed fileName=$fileName", error)
            throw StorageError.WriteFailed(error)
        }

    private fun storedTreeUri(): Uri? =
        preferences.getString(KEY_USB_TREE_URI, null)?.takeIf { value -> value.isNotBlank() }?.let(Uri::parse)

    private fun logEnvironment() {
        val isFireTv = appContext.packageManager.hasSystemFeature(FIRE_TV_FEATURE)
        Log.i(
            TAG,
            "environment fireTv=$isFireTv manufacturer=${Build.MANUFACTURER} sdk=${Build.VERSION.SDK_INT}"
        )
    }

    companion object {
        const val REQUEST_USB_TREE = 7038
        private const val TAG = "ExternalStorage"
        private const val PREFS = "tiber_iptv_state"
        private const val KEY_USB_TREE_URI = "usb_tree_uri"
        private const val FIRE_TV_FEATURE = "amazon.hardware.fire_tv"
        private const val APP_SPECIFIC_DOWNLOADS = "downloads"
        private const val USB_PERMISSION_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    }
}
