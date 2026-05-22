package com.tiberiptv.fire

import android.content.Context
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal object PosterImages {
    private const val MAX_WARM_IMAGES = 180
    private const val MAX_WARM_PER_ROW = 14
    private const val MAX_PARALLEL_WARM_REQUESTS = 6
    private const val WARM_TIMEOUT_MS = 35_000L

    fun normalizedUrl(url: String?): String {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return ""
        }
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> ""
        }.replace(" ", "%20")
    }

    suspend fun warmCatalog(context: Context, rows: List<XtreamModels.ContentRow>) {
        val urls = prioritizedUrls(rows)
        if (urls.isEmpty()) {
            return
        }
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(WARM_TIMEOUT_MS) {
                val imageLoader = Coil.imageLoader(context)
                val semaphore = Semaphore(MAX_PARALLEL_WARM_REQUESTS)
                coroutineScope {
                    urls.map { url ->
                        async {
                            semaphore.withPermit {
                                try {
                                    imageLoader.execute(
                                        ImageRequest.Builder(context)
                                            .data(url)
                                            .diskCacheKey(url)
                                            .memoryCachePolicy(CachePolicy.ENABLED)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .networkCachePolicy(CachePolicy.ENABLED)
                                            .precision(Precision.INEXACT)
                                            .allowHardware(true)
                                            .build()
                                    )
                                } catch (exception: CancellationException) {
                                    throw exception
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        }
    }

    private fun prioritizedUrls(rows: List<XtreamModels.ContentRow>): List<String> {
        val urls = LinkedHashSet<String>()
        rows.forEach { row ->
            row.items.asSequence()
                .take(MAX_WARM_PER_ROW)
                .map { item -> normalizedUrl(item.imageUrl) }
                .filter { url -> url.isNotBlank() }
                .forEach { url ->
                    if (urls.size < MAX_WARM_IMAGES) {
                        urls.add(url)
                    }
                }
        }
        if (urls.size >= MAX_WARM_IMAGES) {
            return urls.toList()
        }
        rows.asSequence()
            .flatMap { row -> row.items.asSequence() }
            .map { item -> normalizedUrl(item.imageUrl) }
            .filter { url -> url.isNotBlank() }
            .forEach { url ->
                if (urls.size < MAX_WARM_IMAGES) {
                    urls.add(url)
                }
            }
        return urls.toList()
    }
}
