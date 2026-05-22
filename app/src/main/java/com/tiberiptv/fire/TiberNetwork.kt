package com.tiberiptv.fire

import android.content.Context
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

internal object TiberNetwork {
    const val USER_AGENT = "TiberIPTV-Fire/0.1"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var sharedClient: OkHttpClient? = null

    @Volatile
    private var sharedImageClient: OkHttpClient? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun appClient(): OkHttpClient =
        sharedClient ?: synchronized(this) {
            sharedClient ?: buildClient(
                dispatcher = Dispatcher().apply {
                    maxRequests = 32
                    maxRequestsPerHost = 6
                }
            ).also { sharedClient = it }
        }

    fun imageClient(): OkHttpClient =
        sharedImageClient ?: synchronized(this) {
            sharedImageClient ?: appClient().newBuilder()
                .dispatcher(
                    Dispatcher().apply {
                        maxRequests = 18
                        maxRequestsPerHost = 8
                    }
                )
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        .header("User-Agent", USER_AGENT)
                        .build()
                    chain.proceed(request)
                }
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
                .also { sharedImageClient = it }
        }

    private fun buildClient(dispatcher: Dispatcher): OkHttpClient {
        val context = appContext
        return OkHttpClient.Builder()
            .apply {
                if (context != null) {
                    cache(
                        Cache(
                            directory = File(context.cacheDir, CacheDirectories.HTTP),
                            maxSize = 128L * 1024L * 1024L
                        )
                    )
                }
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .dispatcher(dispatcher)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
