package com.tiberiptv.fire

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class XtreamApiTest {
    @Test
    fun getSeriesInfoKeepsEpisodeImagesAndFallsBackToSeriesCover() {
        val response = """
            {
              "info": {
                "cover": "images/series-cover.jpg"
              },
              "episodes": {
                "1": [
                  {
                    "id": "101",
                    "title": "Pilot",
                    "container_extension": "mkv",
                    "info": {
                      "movie_image": "n/a",
                      "cover_big": "https://cdn.example.test/episode-101.jpg"
                    }
                  },
                  {
                    "id": "102",
                    "title": "Second",
                    "container_extension": "mp4",
                    "info": {
                      "movie_image": ""
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        withJsonServer(response) { serverUrl ->
            RemoteActionGuard.release(null)
            assertTrue(RemoteActionGuard.tryAcquire(RemoteLabels.SERIES_DETAIL))
            try {
                val api = XtreamApi(XtreamModels.Credentials(serverUrl, "user", "pass"))
                val info = api.getSeriesInfo("42")

                val episodes = info.seasons.single().episodes
                assertEquals("Saison 1", info.seasons.single().name)
                assertEquals("https://cdn.example.test/episode-101.jpg", episodes[0].imageUrl)
                assertEquals("$serverUrl/images/series-cover.jpg", episodes[1].imageUrl)
            } finally {
                RemoteActionGuard.release(RemoteLabels.SERIES_DETAIL)
            }
        }
    }

    private fun withJsonServer(response: String, block: (String) -> Unit) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val responseTask = executor.submit {
            server.use { socket ->
                socket.accept().use { client ->
                    val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                    while (!reader.readLine().isNullOrEmpty()) {
                        // Consume request headers before sending the fixed JSON response.
                    }
                    val bytes = response.toByteArray(Charsets.UTF_8)
                    val headers = (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    ).toByteArray(Charsets.UTF_8)
                    client.getOutputStream().use { output ->
                        output.write(headers)
                        output.write(bytes)
                    }
                }
            }
        }
        try {
            block("http://127.0.0.1:${server.localPort}")
            responseTask.get(5, TimeUnit.SECONDS)
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }
}
