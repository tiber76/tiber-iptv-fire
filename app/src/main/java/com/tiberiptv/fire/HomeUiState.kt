package com.tiberiptv.fire

import androidx.compose.runtime.Immutable

@Immutable
data class HomeHeroItem(
    val title: String = "",
    val type: String = "",
    val progressLabel: String = "",
    val item: XtreamModels.StreamItem? = null
)

@Immutable
data class HomeUiState(
    val appStarting: Boolean = true,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val authenticated: Boolean = false,
    val accountServer: String = "",
    val activeAccountId: String = "",
    val accounts: List<AccountSummary> = emptyList(),
    val liveCatalogLoadedAt: Long = 0L,
    val moviesCatalogLoadedAt: Long = 0L,
    val seriesCatalogLoadedAt: Long = 0L,
    val liveItemCount: Int = 0,
    val movieItemCount: Int = 0,
    val seriesItemCount: Int = 0,
    val favoriteItemCount: Int = 0,
    val downloadedItemCount: Int = 0,
    val heroItem: HomeHeroItem? = null,
    val refreshingCatalogMode: String? = null,
    val networkProfile: NetworkProfile = NetworkProfile.NORMAL,
    val loading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)
