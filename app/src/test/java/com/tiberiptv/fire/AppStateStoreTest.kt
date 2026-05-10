package com.tiberiptv.fire

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppStateStoreTest {
    private lateinit var context: Context
    private lateinit var store: AppStateStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("tiber_iptv_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = AppStateStore(context)
        store.clearCatalogCaches()
    }

    @Test
    fun cachedItemCountUsesPersistedSummaryWhenCatalogItemsAreMissing() {
        store.saveRows(MOVIES_SCOPE, sampleRows())
        assertEquals(3, store.cachedItemCount(MOVIES_SCOPE))

        TiberDatabase.get(context).stateDao().deleteCatalogScope(MOVIES_SCOPE)

        assertEquals(3, store.cachedItemCount(MOVIES_SCOPE))
        assertTrue(store.cacheSavedAt(MOVIES_SCOPE) > 0L)
    }

    @Test
    fun loadRowsKeepsCatalogAvailableAfterTwentyFourHours() {
        store.saveRows(MOVIES_SCOPE, sampleRows())
        val dao = TiberDatabase.get(context).stateDao()
        val cache = dao.cache(MOVIES_SCOPE) ?: error("Missing saved cache")
        cache.saved_at = System.currentTimeMillis() - THIRTY_HOURS_MS
        dao.upsertCache(cache)

        val rows = store.loadRows(MOVIES_SCOPE)

        assertEquals(2, rows.size)
        assertEquals("Recent", rows.first().title)
        assertEquals(2, rows.first().items.size)
    }

    @Test
    fun clearCatalogCachesRemovesRowsAndPersistedSummary() {
        store.saveRows(MOVIES_SCOPE, sampleRows())

        store.clearCatalogCaches()

        assertEquals(0L, store.cacheSavedAt(MOVIES_SCOPE))
        assertEquals(0, store.cachedItemCount(MOVIES_SCOPE))
        assertTrue(store.loadRows(MOVIES_SCOPE).isEmpty())
    }

    private fun sampleRows(): List<XtreamModels.ContentRow> =
        listOf(
            XtreamModels.ContentRow(
                "Recent",
                listOf(
                    streamItem("1", "First movie"),
                    streamItem("2", "Second movie")
                )
            ),
            XtreamModels.ContentRow(
                "Top",
                listOf(streamItem("3", "Third movie"))
            )
        )

    private fun streamItem(id: String, title: String): XtreamModels.StreamItem =
        XtreamModels.StreamItem(
            id = id,
            title = title,
            type = XtreamModels.StreamItem.TYPE_MOVIE,
            imageUrl = "",
            categoryId = "cat",
            extension = "mp4",
            playable = true,
            releaseDate = "",
            addedTimestamp = "",
            rating = "7.5",
            year = "2026"
        )

    private companion object {
        private const val MOVIES_SCOPE = "MOVIES"
        private const val THIRTY_HOURS_MS = 30L * 60L * 60L * 1000L
    }
}
