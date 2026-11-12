package com.confused.anikuta.core.torrent

import com.confused.anikuta.core.common.Logger

/**
 * THE GATEWAY — the single object the app talks to (one injected dependency
 * at the watch seam). Turns a raw torrent/magnet link into a playable
 * localhost stream URL and exposes the live status for the player chip.
 *
 * Removal guide: TORRENT-REMOVAL.md (delete this module + ~8 call sites).
 */
class TorrentGateway(
    private val loader: TorrServerLoader,
    private val api: TorrentServerApi,
    val preferences: TorrentPreferences,
) {
    companion object {
        private const val TAG = "Anikuta:Core:Torrent"

        /** Torrent links must never be auto-picked without the user's consent gate. */
        const val DISCLAIMER_SEEN_KEY = "torrent_disclaimer_accepted"
    }

    /** Torrent support enabled in Settings (default OFF). */
    fun isEnabled(): Boolean = preferences.enabled

    /** True once the user accepted the first-use disclaimer. */
    fun isDisclaimerAccepted(): Boolean = preferences.disclaimerAccepted

    fun acceptDisclaimer() {
        preferences.disclaimerAccepted = true
        Logger.i(TAG) { "torrent disclaimer accepted" }
    }

    /**
     * Transforms a raw torrent/magnet link into a playable stream URL:
     * ensures the server is up → adds the link (metadata resolve) → builds
     * the /stream URL. [onUnavailable] fires when the engine cannot load
     * (unsupported device); [onDead] when the swarm never answered.
     */
    suspend fun prepare(rawLink: String, title: String): TorrentPrepared {
        val base = loader.ensureStarted()
            ?: throw TorrentException("The torrent engine is unavailable on this device")
        val status = try {
            api.add(rawLink, title)
        } catch (e: TorrentException) {
            throw e
        } catch (e: Exception) {
            // Metadata resolve timed out / connection died — a thin or dead swarm.
            throw TorrentException("No peers answered for this torrent (thin or dead swarm)", e)
        }
        val hash = status.hash
            ?: throw TorrentException("The torrent server returned no hash for this link")
        val streamUrl = try {
            status.streamUrl(base, rawLink)
        } catch (e: TorrentException) {
            throw e
        }
        Logger.i(TAG) {
            "torrent prepared: hash=$hash files=${status.fileStats?.size ?: 0} " +
                "seeders=${status.connectedSeeders ?: 0} url=$streamUrl"
        }
        return TorrentPrepared(streamUrl, hash, status.title, status)
    }

    /** The live status snapshot for the player chip (null when the poll fails). */
    suspend fun status(hash: String): TorrentStatus? =
        runCatching { api.get(hash) }.onFailure {
            Logger.d(TAG) { "status poll failed: ${it.message}" }
        }.getOrNull()

    /** Forgets the torrent server-side (stream closed + registry entry removed). */
    suspend fun forget(hash: String) = api.remove(hash)
}
