package com.tiberiptv.fire

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class AccountSummary(
    val id: String,
    val serverUrl: String,
    val username: String,
    val label: String
)

class CredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): XtreamModels.Credentials =
        XtreamModels.Credentials(
            preferences.getString(KEY_SERVER, "").orEmpty(),
            preferences.getString(KEY_USERNAME, "").orEmpty(),
            preferences.getString(KEY_PASSWORD, "").orEmpty()
        )

    fun save(credentials: XtreamModels.Credentials) {
        val normalized = credentials.normalized()
        preferences.edit()
            .putString(KEY_SERVER, normalized.serverUrl)
            .putString(KEY_USERNAME, normalized.username)
            .putString(KEY_PASSWORD, normalized.password)
            .putString(KEY_ACCOUNTS, accountsWith(normalized).toJson())
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_SERVER)
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }

    fun accounts(): List<AccountSummary> =
        storedAccounts().map { account ->
            AccountSummary(
                id = account.id,
                serverUrl = account.serverUrl,
                username = account.username,
                label = account.label()
            )
        }

    fun activateAccount(id: String): XtreamModels.Credentials? {
        val account = storedAccounts().firstOrNull { it.id == id } ?: return null
        val credentials = account.credentials()
        preferences.edit()
            .putString(KEY_SERVER, credentials.serverUrl)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
        return credentials
    }

    fun removeAccount(id: String) {
        val remaining = storedAccounts().filterNot { it.id == id }
        val activeId = load().normalized().accountId()
        preferences.edit()
            .putString(KEY_ACCOUNTS, remaining.toJson())
            .apply()
        if (activeId == id) {
            val next = remaining.firstOrNull()
            if (next == null) {
                clear()
            } else {
                activateAccount(next.id)
            }
        }
    }

    private fun accountsWith(credentials: XtreamModels.Credentials): List<StoredAccount> {
        val stored = storedAccounts().filterNot { it.id == credentials.accountId() }.toMutableList()
        stored.add(
            0,
            StoredAccount(
                id = credentials.accountId(),
                serverUrl = credentials.serverUrl,
                username = credentials.username,
                password = credentials.password
            )
        )
        return stored.take(MAX_ACCOUNTS)
    }

    private fun storedAccounts(): List<StoredAccount> {
        val raw = preferences.getString(KEY_ACCOUNTS, "[]").orEmpty()
        val parsed = mutableListOf<StoredAccount>()
        try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val account = StoredAccount(
                    id = json.optString("id"),
                    serverUrl = json.optString("server"),
                    username = json.optString("username"),
                    password = json.optString("password")
                ).normalized()
                if (account.credentials().isComplete()) {
                    parsed.add(account)
                }
            }
        } catch (_: Exception) {
        }
        val active = load().normalized()
        if (active.isComplete() && parsed.none { it.id == active.accountId() }) {
            parsed.add(0, active.toStoredAccount())
        }
        return parsed.distinctBy { it.id }.take(MAX_ACCOUNTS)
    }

    private fun List<StoredAccount>.toJson(): String {
        val array = JSONArray()
        forEach { account ->
            array.put(
                JSONObject()
                    .put("id", account.id)
                    .put("server", account.serverUrl)
                    .put("username", account.username)
                    .put("password", account.password)
            )
        }
        return array.toString()
    }

    private data class StoredAccount(
        val id: String,
        val serverUrl: String,
        val username: String,
        val password: String
    ) {
        fun credentials(): XtreamModels.Credentials =
            XtreamModels.Credentials(serverUrl, username, password)

        fun normalized(): StoredAccount =
            XtreamModels.Credentials(
                normalizeServer(serverUrl),
                username.trim(),
                password
            ).let { credentials ->
                StoredAccount(
                    id = "${credentials.serverUrl.lowercase(Locale.US)}|${credentials.username.lowercase(Locale.US)}",
                    serverUrl = credentials.serverUrl,
                    username = credentials.username,
                    password = credentials.password
                )
            }

        fun label(): String {
            val host = serverUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
            return "$username@$host"
        }
    }

    private fun XtreamModels.Credentials.normalized(): XtreamModels.Credentials =
        XtreamModels.Credentials(
            normalizeServer(serverUrl),
            username.trim(),
            password
        )

    private fun XtreamModels.Credentials.accountId(): String =
        "${serverUrl.lowercase(Locale.US)}|${username.lowercase(Locale.US)}"

    private fun XtreamModels.Credentials.toStoredAccount(): StoredAccount =
        StoredAccount(
            id = accountId(),
            serverUrl = serverUrl,
            username = username,
            password = password
        )

    companion object {
        private const val PREFS = "tiber_iptv_credentials"
        private const val KEY_SERVER = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ACCOUNTS = "accounts_json"
        private const val MAX_ACCOUNTS = 6

        @JvmStatic
        fun normalizeServer(value: String): String {
            var server = value.trim()
            if (!server.startsWith("http://") && !server.startsWith("https://")) {
                server = "http://$server"
            }
            if (server.endsWith("/")) {
                server = server.substring(0, server.length - 1)
            }
            return server
        }
    }
}
