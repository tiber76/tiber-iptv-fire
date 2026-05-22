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
        TiberDatabase.get(context).stateDao().deleteMovieCatalog()

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

    @Test
    fun categoryPreferencesPersistPinnedAndHiddenKeys() {
        val key = "$MOVIES_SCOPE|Recent"

        assertTrue(store.pinnedCategoryKeys().isEmpty())
        assertTrue(store.hiddenCategoryKeys().isEmpty())
        assertTrue(store.customGroupCategoryKeys().isEmpty())

        store.togglePinnedCategory(key)
        store.toggleHiddenCategory(key)
        store.toggleCustomGroupCategory(key)

        assertEquals(setOf(key), store.pinnedCategoryKeys())
        assertEquals(setOf(key), store.hiddenCategoryKeys())
        assertEquals(setOf(key), store.customGroupCategoryKeys())

        store.clearCategoryPreferences()

        assertTrue(store.pinnedCategoryKeys().isEmpty())
        assertTrue(store.hiddenCategoryKeys().isEmpty())
        assertTrue(store.customGroupCategoryKeys().isEmpty())
    }

    @Test
    fun loadIndexedRowsUsesPersistedSearchMetadata() {
        store.saveRows(MOVIES_SCOPE, sampleRows())

        val indexedRows = store.loadIndexedRows(MOVIES_SCOPE)
        val filtered = filteredRows(
            index = indexedRows.searchIndex,
            query = "second",
            filter4k = false,
            filterHighRating = false,
            filterRecentYear = false,
            sort = CatalogSort.RECENT
        )

        assertEquals(2, indexedRows.rows.size)
        assertEquals("Second movie 4K", filtered.single().items.single().title)
    }

    @Test
    fun saveRowsUsesTypedMovieCatalogInsteadOfLegacyJsonTable() {
        store.saveRows(MOVIES_SCOPE, sampleRows())
        val dao = TiberDatabase.get(context).stateDao()

        assertEquals(0, dao.catalogItemCount(MOVIES_SCOPE))
        assertEquals(3, dao.movieCatalogItemCount())
    }

    @Test
    fun loadFilteredIndexedRowsUsesSqlMetadata() {
        store.saveRows(MOVIES_SCOPE, sampleRows())

        val searchRows = store.loadFilteredIndexedRows(
            scope = MOVIES_SCOPE,
            query = "third",
            filter4k = false,
            filterHighRating = false,
            filterRecentYear = false,
            sort = CatalogSort.RECENT
        ).rows
        val ultraHdRows = store.loadFilteredIndexedRows(
            scope = MOVIES_SCOPE,
            query = "",
            filter4k = true,
            filterHighRating = false,
            filterRecentYear = false,
            sort = CatalogSort.RECENT
        ).rows

        assertEquals("Résultats recherche", searchRows.single().title)
        assertEquals("Third movie", searchRows.single().items.single().title)
        assertEquals("Second movie 4K", ultraHdRows.single().items.single().title)
    }

    @Test
    fun loadFilteredIndexedRowsCanSortByResume() {
        store.saveRows(MOVIES_SCOPE, sampleRows())
        store.saveResume(streamItem("1", "First movie").key(), 120_000L, 600_000L)

        val rows = store.loadFilteredIndexedRows(
            scope = MOVIES_SCOPE,
            query = "",
            filter4k = false,
            filterHighRating = false,
            filterRecentYear = false,
            sort = CatalogSort.RESUME
        ).rows

        assertEquals("Recent", rows.first().title)
        assertEquals("First movie", rows.first().items.first().title)
    }

    @Test
    fun loadFilteredIndexedRowsUsesFtsPrefixSearch() {
        store.saveRows(MOVIES_SCOPE, sampleRows())

        val searchRows = store.loadFilteredIndexedRows(
            scope = MOVIES_SCOPE,
            query = "sec mov",
            filter4k = false,
            filterHighRating = false,
            filterRecentYear = false,
            sort = CatalogSort.RECENT
        ).rows

        assertEquals("Résultats recherche", searchRows.single().title)
        assertEquals("Second movie 4K", searchRows.single().items.single().title)
    }

    @Test
    fun itemDetailCachePersistsBackdropAndTrailer() {
        val item = streamItem("42", "Cached movie")
        assertTrue(!store.hasCachedItemDetail(item))
        store.saveItemDetail(
            item,
            XtreamModels.ItemDetail(
                plot = "Local synopsis",
                genre = "Drama",
                duration = "90",
                rating = "8.4",
                releaseDate = "2026-01-01",
                contentRating = "PG-13",
                cast = "Cast",
                director = "Director",
                trailer = "abc123",
                backdropUrl = "https://cdn.example.test/backdrop.jpg"
            )
        )

        val cached = store.cachedItemDetail(item) ?: error("Missing cached detail")

        assertTrue(store.hasCachedItemDetail(item))
        assertEquals("Local synopsis", cached.plot)
        assertEquals("abc123", cached.trailer)
        assertEquals("https://cdn.example.test/backdrop.jpg", cached.backdropUrl)
    }

    @Test
    fun itemDetailCacheReportsFreshnessFromSavedAt() {
        val item = streamItem("43", "Freshness movie")
        store.saveItemDetail(
            item,
            XtreamModels.ItemDetail(
                plot = "Cached",
                genre = "",
                duration = "",
                rating = "",
                releaseDate = "",
                contentRating = "",
                cast = "",
                director = "",
                trailer = ""
            )
        )
        val fresh = store.cachedItemDetail(item, ttlMs = ONE_HOUR_MS) ?: error("Missing cached detail")
        assertTrue(fresh.isFresh)

        val dao = TiberDatabase.get(context).stateDao()
        val entity = dao.itemDetail(item.key()) ?: error("Missing detail entity")
        entity.saved_at = System.currentTimeMillis() - TWO_HOURS_MS
        dao.upsertItemDetail(entity)

        val stale = store.cachedItemDetail(item, ttlMs = ONE_HOUR_MS) ?: error("Missing cached detail")

        assertTrue(!stale.isFresh)
        assertEquals("Cached", stale.detail.plot)
    }

    @Test
    fun seriesInfoCachePersistsEpisodes() {
        val item = streamItem("77", "Cached series")
        val season = XtreamModels.SeriesSeason("Saison 1").apply {
            episodes.add(
                XtreamModels.StreamItem(
                    id = "771",
                    title = "Episode cached",
                    type = XtreamModels.StreamItem.TYPE_EPISODE,
                    imageUrl = "https://cdn.example.test/episode.jpg",
                    categoryId = "1",
                    extension = "mp4",
                    rating = "7.2"
                )
            )
        }
        store.saveSeriesInfo(
            item,
            XtreamModels.SeriesInfo(
                detail = XtreamModels.ItemDetail(
                    plot = "Series synopsis",
                    genre = "",
                    duration = "",
                    rating = "",
                    releaseDate = "",
                    contentRating = "",
                    cast = "",
                    director = "",
                    trailer = "",
                    backdropUrl = "https://cdn.example.test/series-backdrop.jpg"
                ),
                seasons = listOf(season)
            )
        )

        val cached = store.cachedSeriesInfo(item) ?: error("Missing cached series")

        assertEquals("Series synopsis", cached.detail.plot)
        assertEquals("https://cdn.example.test/series-backdrop.jpg", cached.detail.backdropUrl)
        assertEquals("Saison 1", cached.seasons.single().name)
        assertEquals("Episode cached", cached.seasons.single().episodes.single().title)
    }

    @Test
    fun epgCachePersistsProgramsWithinTtl() {
        val item = XtreamModels.StreamItem(
            id = "live-1",
            title = "Live channel",
            type = XtreamModels.StreamItem.TYPE_LIVE,
            imageUrl = "",
            categoryId = "live",
            extension = "ts"
        )
        store.saveEpg(
            item,
            listOf(
                XtreamModels.EpgProgram(
                    title = "Current show",
                    description = "Description",
                    start = "18:00",
                    end = "19:00"
                )
            )
        )

        val cached = store.cachedEpg(item, ONE_HOUR_MS)

        assertEquals("Current show", cached.single().title)
        assertEquals("18:00", cached.single().start)
    }

    private fun sampleRows(): List<XtreamModels.ContentRow> =
        listOf(
            XtreamModels.ContentRow(
                "Recent",
                listOf(
                    streamItem("1", "First movie", addedTimestamp = "100"),
                    streamItem("2", "Second movie 4K", addedTimestamp = "200")
                )
            ),
            XtreamModels.ContentRow(
                "Top",
                listOf(streamItem("3", "Third movie", addedTimestamp = "300"))
            )
        )

    private fun streamItem(id: String, title: String, addedTimestamp: String = ""): XtreamModels.StreamItem =
        XtreamModels.StreamItem(
            id = id,
            title = title,
            type = XtreamModels.StreamItem.TYPE_MOVIE,
            imageUrl = "",
            categoryId = "cat",
            extension = "mp4",
            playable = true,
            releaseDate = "",
            addedTimestamp = addedTimestamp,
            rating = "7.5",
            year = "2026"
        )

    private companion object {
        private const val MOVIES_SCOPE = "MOVIES"
        private const val THIRTY_HOURS_MS = 30L * 60L * 60L * 1000L
        private const val ONE_HOUR_MS = 60L * 60L * 1000L
        private const val TWO_HOURS_MS = 2L * ONE_HOUR_MS
    }
}
