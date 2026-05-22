package com.tiberiptv.fire

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okio.Path.Companion.toOkioPath

class TiberApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        TiberNetwork.initialize(this)
        CatalogSyncScheduler.schedule(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { TiberNetwork.imageClient() }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.22)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve(CacheDirectories.COIL_IMAGES).toOkioPath())
                    .maxSizeBytes(100L * 1024L * 1024L)
                    .build()
            }
            .build()
}
