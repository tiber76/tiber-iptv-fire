package com.tiberiptv.fire

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.util.Locale
import kotlin.math.min

class TrailerActivity : Activity() {
    private var remoteGuardLabel: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trailer = intent.getStringExtra(EXTRA_TRAILER)
        remoteGuardLabel = intent.getStringExtra(EXTRA_REMOTE_GUARD_LABEL)
        if (remoteGuardLabel != "bande-annonce" || remoteGuardLabel != RemoteActionGuard.activeLabel()) {
            Toast.makeText(this, "Bande-annonce bloquee: verrou remote absent.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val videoId = youtubeId(trailer?.trim().orEmpty())
        val url = trailerUrl(trailer, videoId)
        if (url.isEmpty()) {
            Toast.makeText(this, "Bande-annonce indisponible.", Toast.LENGTH_SHORT).show()
            releaseRemoteGuard()
            finish()
            return
        }

        releaseRemoteGuard()
        if (!openExternalTrailer(url, videoId)) {
            Toast.makeText(this, "Aucune application YouTube ou navigateur disponible.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onDestroy() {
        releaseRemoteGuard()
        super.onDestroy()
    }

    private fun releaseRemoteGuard() {
        val label = remoteGuardLabel
        if (!label.isNullOrEmpty()) {
            RemoteActionGuard.release(label)
            remoteGuardLabel = ""
        }
    }

    private fun openExternalTrailer(url: String, videoId: String): Boolean {
        val intents = mutableListOf<Intent>()
        if (videoId.isNotEmpty()) {
            val nativeUri = Uri.parse("vnd.youtube:$videoId")
            intents += Intent(Intent.ACTION_VIEW, nativeUri).setPackage("com.google.android.youtube.tv")
            intents += Intent(Intent.ACTION_VIEW, nativeUri).setPackage("com.google.android.youtube")
            intents += Intent(Intent.ACTION_VIEW, nativeUri)
        }
        val webUri = Uri.parse(url)
        intents += Intent(Intent.ACTION_VIEW, webUri).setPackage("com.google.android.youtube.tv")
        intents += Intent(Intent.ACTION_VIEW, webUri).setPackage("com.google.android.youtube")
        intents += Intent(Intent.ACTION_VIEW, webUri)

        for (candidate in intents) {
            try {
                startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    private fun trailerUrl(trailer: String?, id: String): String {
        val value = trailer?.trim().orEmpty()
        if (id.isNotEmpty()) {
            return Uri.parse("https://www.youtube.com/watch")
                .buildUpon()
                .appendQueryParameter("v", id)
                .build()
                .toString()
        }
        return if (value.startsWith("http://") || value.startsWith("https://")) value else ""
    }

    private fun youtubeId(value: String): String {
        val clean = value.trim()
        if (clean.matches(Regex("[A-Za-z0-9_-]{11}"))) {
            return clean
        }
        val lower = clean.lowercase(Locale.US)
        val watch = lower.indexOf("v=")
        if (watch >= 0) {
            return trimYoutubeId(clean.substring(watch + 2))
        }
        val shortUrl = lower.indexOf("youtu.be/")
        if (shortUrl >= 0) {
            return trimYoutubeId(clean.substring(shortUrl + "youtu.be/".length))
        }
        val embed = lower.indexOf("/embed/")
        if (embed >= 0) {
            return trimYoutubeId(clean.substring(embed + "/embed/".length))
        }
        return ""
    }

    private fun trimYoutubeId(value: String): String {
        var end = value.length
        for (separator in arrayOf("&", "?", "/", "#")) {
            val index = value.indexOf(separator)
            if (index >= 0) {
                end = min(end, index)
            }
        }
        val id = value.substring(0, end)
        return if (id.matches(Regex("[A-Za-z0-9_-]{11}"))) id else ""
    }

    companion object {
        const val EXTRA_TRAILER = "trailer"
        const val EXTRA_TITLE = "title"
        const val EXTRA_REMOTE_GUARD_LABEL = "remote_guard_label"
    }
}
