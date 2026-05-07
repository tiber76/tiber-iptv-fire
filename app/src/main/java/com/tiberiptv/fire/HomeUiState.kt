package com.tiberiptv.fire

import androidx.compose.runtime.Immutable

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
    val refreshingCatalogMode: String? = null,
    val networkProfile: NetworkProfile = NetworkProfile.NORMAL,
    val loading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)
