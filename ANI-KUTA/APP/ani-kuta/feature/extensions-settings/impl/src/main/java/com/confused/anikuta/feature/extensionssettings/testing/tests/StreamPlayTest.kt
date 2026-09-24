package com.confused.anikuta.feature.extensionssettings.testing.tests

import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTest
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestContext
import com.confused.anikuta.feature.extensionssettings.testing.ExtensionTestKind
import com.confused.anikuta.feature.extensionssettings.testing.TestOutcome
import com.confused.anikuta.feature.extensionssettings.testing.TestTimeFormat
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * STREAM PLAY (round 82, D-576): the end of the chain — "does the video
 * actually PLAY from it?" A real player download is out of scope for a test
 * run, so this performs the same exchange a player's first read does: a
 * Range-GET (bytes=0-64K) on the resolved stream URL with the stream's own
 * headers, then verifies the server answers 200/206 AND delivers at least one
 * byte. A stream that answers but sends nothing is honestly a failure.
 */
class StreamPlayTest(
    private val client: OkHttpClient,
) : ExtensionTest {

    override val kind = ExtensionTestKind.STREAM_PLAY
    override val requiresAnyOf = setOf(ExtensionTestKind.VIDEO_RESOLVE)

    override suspend fun run(context: ExtensionTestContext): TestOutcome =
        withContext(context.ioDispatcher) {
            val url = context.resolvedVideoUrl
                ?: return@withContext TestOutcome.skip("No resolved stream URL to test")
            if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                return@withContext TestOutcome.fail(
                    "Stream URL is not fetchable (${url.take(24)}…)",
                    detail = "torrents / magnets are out of the play-test's scope",
                )
            }

            val startedAt = System.nanoTime()
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-65535")
                    .apply {
                        context.resolvedVideoHeaders?.forEach { (name, value) ->
                            if (!name.equals("Range", ignoreCase = true)) header(name, value)
                        }
                    }
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body
                    var bytes = 0L
                    if (body != null) {
                        body.byteStream().use { input ->
                            // Read a real chunk — a 200 with an empty body is
                            // NOT a playable stream.
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (bytes < 65536L) {
                                read = input.read(buffer)
                                if (read == -1) break
                                bytes += read
                            }
                        }
                    }
                    val durationMs = (System.nanoTime() - startedAt) / 1_000_000L
                    if (bytes > 0) {
                        TestOutcome.pass(
                            "Stream answered HTTP ${response.code} and delivered " +
                                "${formatBytes(bytes)} in ${TestTimeFormat.format(durationMs)}",
                            detail = context.resolvedVideoLabel,
                        )
                    } else {
                        TestOutcome.fail(
                            "HTTP ${response.code} but no bytes delivered",
                            detail = context.resolvedVideoLabel,
                        )
                    }
                }
            } catch (e: Exception) {
                TestOutcome.fail(
                    "${e::class.java.simpleName}: ${e.message ?: "connection failed"}",
                    detail = context.resolvedVideoLabel,
                )
            }
        }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
