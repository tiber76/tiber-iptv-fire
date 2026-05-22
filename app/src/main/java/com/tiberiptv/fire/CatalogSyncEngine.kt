package com.tiberiptv.fire

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

internal object CatalogSyncEngine {
    suspend fun fetchRows(api: XtreamApi, mode: Mode): List<XtreamModels.ContentRow> {
        val rows = mutableListOf<XtreamModels.ContentRow>()
        when (mode) {
            Mode.LIVE -> {
                for (category in api.getLiveCategories()) {
                    currentCoroutineContext().ensureActive()
                    val items = api.getLiveStreams(category.id).take(LIVE_ITEMS_PER_CATEGORY)
                    if (items.isNotEmpty()) {
                        rows.add(XtreamModels.ContentRow(category.name, items))
                    }
                }
            }
            Mode.MOVIES -> {
                for (category in api.getMovieCategories()) {
                    currentCoroutineContext().ensureActive()
                    val items = api.getMovieStreams(category.id).filter { item -> item.playable }
                    if (items.isNotEmpty()) {
                        rows.add(XtreamModels.ContentRow(category.name, items))
                    }
                }
            }
            Mode.SERIES -> {
                for (category in api.getSeriesCategories()) {
                    currentCoroutineContext().ensureActive()
                    val items = api.getSeriesStreams(category.id)
                    if (items.isNotEmpty()) {
                        rows.add(XtreamModels.ContentRow(category.name, items))
                    }
                }
            }
            Mode.FAVORITES, Mode.DOWNLOADS -> Unit
        }
        return rows
    }

    suspend fun hydrateDetails(
        api: XtreamApi,
        stateStore: AppStateStore,
        mode: Mode,
        rows: List<XtreamModels.ContentRow>
    ) {
        val candidates = detailHydrationCandidates(mode, rows)
        if (candidates.isEmpty()) {
            return
        }
        withTimeoutOrNull(DETAIL_HYDRATION_TIMEOUT_MS) {
            for (item in candidates) {
                currentCoroutineContext().ensureActive()
                if (stateStore.hasCachedItemDetail(item)) {
                    continue
                }
                try {
                    when (item.type) {
                        XtreamModels.StreamItem.TYPE_MOVIE ->
                            stateStore.saveItemDetail(item, api.getMovieDetail(item.id))
                        XtreamModels.StreamItem.TYPE_SERIES ->
                            stateStore.saveSeriesInfo(item, api.getSeriesInfo(item.id))
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    // Detail hydration is opportunistic; the catalog remains usable if one item fails.
                }
            }
        }
    }

    private fun detailHydrationCandidates(
        mode: Mode,
        rows: List<XtreamModels.ContentRow>
    ): List<XtreamModels.StreamItem> {
        val maxItems = when (mode) {
            Mode.MOVIES -> DETAIL_HYDRATION_MOVIE_LIMIT
            Mode.SERIES -> DETAIL_HYDRATION_SERIES_LIMIT
            else -> return emptyList()
        }
        val output = LinkedHashMap<String, XtreamModels.StreamItem>()
        rows.forEach { row ->
            row.items
                .asSequence()
                .filter { item ->
                    item.type == XtreamModels.StreamItem.TYPE_MOVIE ||
                        item.type == XtreamModels.StreamItem.TYPE_SERIES
                }
                .take(DETAIL_HYDRATION_PER_ROW_LIMIT)
                .forEach { item ->
                    if (output.size < maxItems) {
                        output.putIfAbsent(item.key(), item)
                    }
                }
        }
        if (output.size >= maxItems) {
            return output.values.toList()
        }
        rows.asSequence()
            .flatMap { row -> row.items.asSequence() }
            .filter { item ->
                item.type == XtreamModels.StreamItem.TYPE_MOVIE ||
                    item.type == XtreamModels.StreamItem.TYPE_SERIES
            }
            .forEach { item ->
                if (output.size < maxItems) {
                    output.putIfAbsent(item.key(), item)
                }
            }
        return output.values.toList()
    }

    private const val LIVE_ITEMS_PER_CATEGORY = 40
    private const val DETAIL_HYDRATION_MOVIE_LIMIT = 36
    private const val DETAIL_HYDRATION_SERIES_LIMIT = 20
    private const val DETAIL_HYDRATION_PER_ROW_LIMIT = 5
    private const val DETAIL_HYDRATION_TIMEOUT_MS = 75_000L
}
