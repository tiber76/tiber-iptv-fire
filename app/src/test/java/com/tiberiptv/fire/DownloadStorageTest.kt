package com.tiberiptv.fire

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DownloadStorageTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "downloads").deleteRecursively()
    }

    @Test
    fun existingFileUsesPersistedPathWhenPresent() {
        val item = streamItem("42", "Stored Movie")
        val storedFile = File(context.filesDir, "custom/movie.mp4")
        storedFile.parentFile?.mkdirs()
        storedFile.writeText("video")

        val resolved = DownloadStorage.existingFile(context, item, storedFile.absolutePath)

        assertEquals(storedFile.absolutePath, resolved?.absolutePath)
    }

    @Test
    fun existingFileFallsBackToKnownDownloadFileName() {
        val item = streamItem("42", "Stored Movie")
        val fallback = File(context.filesDir, "downloads/Stored_Movie-42.mp4")
        fallback.parentFile?.mkdirs()
        fallback.writeText("video")

        val resolved = DownloadStorage.existingFile(context, item, "/missing/movie.mp4")

        assertEquals(fallback.absolutePath, resolved?.absolutePath)
    }

    @Test
    fun existingFileReturnsNullWhenVolumeFileIsUnavailable() {
        val item = streamItem("42", "Stored Movie")

        val resolved = DownloadStorage.existingFile(context, item, "/missing/movie.mp4")

        assertNull(resolved)
    }

    @Test
    fun targetFileUsesDownloadsDirectoryAndSafeName() {
        val item = streamItem("42", "Film: été bleu")

        val target = DownloadStorage.targetFile(context, item, "")

        assertTrue(target.parentFile?.absolutePath?.endsWith("/downloads") == true)
        assertEquals("Film_t_bleu-42.mp4", target.name)
    }

    private fun streamItem(id: String, title: String): XtreamModels.StreamItem =
        XtreamModels.StreamItem(
            id = id,
            title = title,
            type = XtreamModels.StreamItem.TYPE_MOVIE,
            imageUrl = "",
            categoryId = "",
            extension = "mp4",
            playable = true,
            releaseDate = "",
            addedTimestamp = "",
            rating = "",
            year = ""
        )
}
