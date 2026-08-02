package com.confused.anikuta.core.player

import android.content.Context
import com.confused.anikuta.core.common.Logger
import `is`.xyz.mpv.MPVLib
import java.io.File

/**
 * Centralized MPV initialization.
 *
 * Sequence (must match the MPV lib's expectations):
 *  1. Ensure `mpvDir` exists.
 *  2. Write clean `mpv.conf` + `input.conf`.
 *  3. `copyAssets()` → `subfont.ttf` to mpvDir ROOT (NOT fonts/ — subtitle rendering bug).
 *  4. Configure cache (D-049 — video caching for instant resume).
 *  5. `view.initialize(configDir, cacheDir, logLvl)`.
 *  6. `addLogObserver` + `addObserver`.
 *
 * D-049 (video caching): MPV cache is configured with:
 * - `cache=yes` — enable demuxer cache
 * - `cache-secs=120` — cache 2 minutes (1 min before + 1 min after resume position)
 * - `stream-cache-dir` — disk cache directory for persistent cache between sessions
 * - `demuxer-max-bytes=150MiB` — max in-memory cache size
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Core:Player:Init".
 */
object PlayerInitializer {

    private const val TAG = "Anikuta:Core:Player:Init"
    const val MPV_DIR = "mpv"

    /**
     * Copy `subfont.ttf` from assets to the MPV config-dir ROOT.
     *
     * CRITICAL: `subfont.ttf` MUST be at the config root, NOT in `fonts/`.
     * Without it, libass logs "Error opening memory font" and NO subtitle
     * text can render (video/audio still work).
     */
    fun copyAssets(context: Context, mpvDir: File) {
        val assetManager = context.assets
        val files = arrayOf("subfont.ttf", "cacert.pem")
        for (filename in files) {
            try {
                val ins = assetManager.open(filename, android.content.res.AssetManager.ACCESS_STREAMING)
                val outFile = File(mpvDir, filename)
                if (!outFile.exists() || outFile.length() != ins.available().toLong()) {
                    java.io.FileOutputStream(outFile).use { out -> ins.copyTo(out) }
                    Logger.d(TAG) { "Copied asset: $filename (${outFile.length()} bytes) -> mpv/" }
                }
                ins.close()
            } catch (e: java.io.IOException) {
                Logger.w(TAG) { "Asset not found (non-fatal): $filename" }
            }
        }
    }

    /**
     * Write the MPV configuration files.
     *
     * D-049: Cache config enables instant resume — the demuxer cache covers
     * ~2 minutes of video (1 min before + 1 min after the resume position).
     */
    fun writeConfig(mpvDir: File) {
        val mpvConf = """
            # ANI-KUTA MPV configuration
            # Auto-generated — do not edit manually.

            # ── Video caching (D-049) ──
            # Enable demuxer cache for instant resume
            cache=yes
            cache-secs=120
            demuxer-max-bytes=150MiB
            demuxer-readahead-secs=60

            # ── Hardware decoding ──
            hwdec=auto-copy
            hwdec-codecs=h264,hevc,vp9,av1

            # ── Subtitles ──
            sub-ass-force-margins=yes
            sub-use-margins=yes
            sub-font-size=${'$'}{sub-font-size}
            sub-color=${'$'}{sub-color}
            sub-back-color=${'$'}{sub-back-color}

            # ── Audio ──
            audio-channels=auto

            # ── Network ──
            network-timeout=30
            user-agent=Mozilla/5.0
        """.trimIndent()

        File(mpvDir, "mpv.conf").writeText(mpvConf)
        Logger.d(TAG) { "Wrote mpv.conf (with D-049 cache config)" }

        // Input config — key bindings (minimal for now, expanded in Phase 4)
        val inputConf = """
            # ANI-KUTA MPV input configuration
            # Key bindings for player controls (Phase 4 will add gesture controls)

            SPACE cycle pause
            LEFT seek -10
            RIGHT seek 10
            UP add volume 5
            DOWN add volume -5
            f cycle fullscreen
            ESC quit
        """.trimIndent()

        File(mpvDir, "input.conf").writeText(inputConf)
        Logger.d(TAG) { "Wrote input.conf" }
    }

    /**
     * Initialize MPV on the given view. Must be called EXACTLY ONCE per view instance.
     *
     * @param context Application context.
     * @param view The AnikutaMPVView to initialize.
     */
    fun initialize(context: Context, view: AnikutaMPVView) {
        Logger.i(TAG) { "Initializing MPV..." }

        val mpvDir = File(context.filesDir, MPV_DIR)
        if (!mpvDir.exists()) {
            mpvDir.mkdirs()
            Logger.d(TAG) { "Created MPV dir: ${mpvDir.absolutePath}" }
        }

        copyAssets(context, mpvDir)
        writeConfig(mpvDir)

        // Configure disk cache (D-049 — persistent cache for instant resume)
        val cacheDir = File(context.cacheDir, "mpv-cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Logger.d(TAG) { "Created MPV cache dir: ${cacheDir.absolutePath}" }
        }

        // Initialize the MPV view
        view.initialize(mpvDir.absolutePath, cacheDir.absolutePath, "info")
        Logger.i(TAG) { "MPV initialized (config: ${mpvDir.absolutePath}, cache: ${cacheDir.absolutePath})" }
    }
}
