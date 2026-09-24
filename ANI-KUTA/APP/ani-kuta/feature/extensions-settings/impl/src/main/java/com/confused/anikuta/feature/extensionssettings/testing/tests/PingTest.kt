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
 * PING (round 82, D-576): reaches the source's site root and measures the
 * round trip. ANY completed HTTP exchange passes — even a 403 (a Cloudflare
 * wall means the SITE is up; the SEARCH test is where a block becomes a
 * failure). A HEAD request is tried first; some servers reject HEAD with a
 * protocol error, so a plain GET is the fallback before declaring failure.
 */
class PingTest(
    private val client: OkHttpClient,
) : ExtensionTest {

    override val kind = ExtensionTestKind.PING
    override val requiresAnyOf = emptySet<ExtensionTestKind>()

    override suspend fun run(context: ExtensionTestContext): TestOutcome =
        withContext(context.ioDispatcher) {
            val base = context.target.baseUrl
            if (base.isNullOrBlank() || !(base.startsWith("http://") || base.startsWith("https://"))) {
                return@withContext TestOutcome.fail("No site URL to ping")
            }

            // HEAD first (no body transfer), GET as the fallback.
            val headOutcome = exchange(base, method = "HEAD")
            if (headOutcome is ExchangeResult.Ok) return@withContext headOutcome.toOutcome(base)
            val getOutcome = exchange(base, method = "GET")
            if (getOutcome is ExchangeResult.Ok) return@withContext getOutcome.toOutcome(base)

            val reason = ((headOutcome as? ExchangeResult.Error)?.message ?: "connection failed") +
                (getOutcome as? ExchangeResult.Error)?.let { " / ${it.message}" }.orEmpty()
            TestOutcome.fail(reason.take(120), detail = base)
        }

    private sealed interface ExchangeResult {
        data class Ok(val code: Int, val durationMs: Long) : ExchangeResult
        data class Error(val message: String) : ExchangeResult
    }

    private fun ExchangeResult.Ok.toOutcome(base: String): TestOutcome = TestOutcome.pass(
        message = "Responded HTTP $code in $durationMs ms",
        detail = base,
        // ROUND 85: the live metrics — the detail page renders them as chips.
        payload = TestPayload(httpCode = code, rttMs = durationMs),
    )

    private fun exchange(url: String, method: String): ExchangeResult {
        val startedAt = System.nanoTime()
        return try {
            val request = Request.Builder()
                .url(url)
                .method(method, null)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                val durationMs = (System.nanoTime() - startedAt) / 1_000_000L
                ExchangeResult.Ok(response.code, durationMs)
            }
        } catch (e: Exception) {
            ExchangeResult.Error("${e::class.java.simpleName}: ${e.message ?: "no message"}")
        }
    }

    private companion object {
        // A neutral desktop Chrome UA — some CDNs 403 empty/default UAs.
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
