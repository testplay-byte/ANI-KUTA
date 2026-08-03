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
 *  3. `copyAssets()` → `subfont.ttf` + `cacert.pem` to mpvDir ROOT.
 *  4. `view.initialize(configDir, cacheDir, logLvl)`.
 *  5. `addLogObserver` + `addObserver`.
 *  6. HTTP headers set BEFORE `loadfile` (by the host).
 *
 * NOTE: Most MPV options are now set in [AnikutaMPVView.initOptions] (via
 * `MPVLib.setOptionString`) rather than in mpv.conf. This matches the old
 * project's pattern — init-time options set programmatically are more reliable
 * than mpv.conf entries (some options like `hwdec` and `sub-ass-force-margins`
 * only take effect when set via setOptionString before the render pipeline
 * initializes).
 *
 * The mpv.conf here is intentionally MINIMAL — only options that the old
 * project also puts in mpv.conf (network, audio language, subtitle defaults).
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Core:Player:Init".
 */
object PlayerInitializer {

    private const val TAG = "Anikuta:Core:Player:Init"
    const val MPV_DIR = "mpv"

    /**
     * Copy `subfont.ttf` + `cacert.pem` from assets to the MPV config-dir ROOT.
     *
     * CRITICAL: `subfont.ttf` MUST be at the config root, NOT in `fonts/`.
     * Without it, libass logs "Error opening memory font" and NO subtitle
     * text can render (video/audio still work).
     *
     * `cacert.pem` (Mozilla CA bundle) is required for HTTPS subtitle downloads.
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
     * MINIMAL mpv.conf — most options are set programmatically in
     * [AnikutaMPVView.initOptions] via `setOptionString`. This matches the
     * old project's approach (only network/audio/subtitle defaults in conf).
     *
     * Removed (were causing issues):
     * - `cache=yes` / `cache-secs=120` — old project omits these; relies on
     *   `demuxer-max-bytes` only (set in initOptions).
     * - `hwdec=auto-copy` — wrong variant; `auto` (zero-copy) is set in
     *   initOptions. `auto-copy` forces GPU→CPU copy-back which fails on
     *   some devices → "audio but no video".
     * - `hwdec-codecs` — let lib defaults apply (matches old project).
     * - `sub-ass-force-margins` / `sub-use-margins` — set via setOptionString
     *   in initOptions (init API, more reliable for render pipeline).
     * - `user-agent` — set via `http-header-fields` (full browser UA fallback).
     */
    fun writeConfig(mpvDir: File) {
        val mpvConf = """
            # ANI-KUTA MPV configuration
            # Auto-generated — do not edit manually.
            # Most options are set programmatically in AnikutaMPVView.initOptions().

            # ── Audio language preference (matches old project) ──
            alang=jpn,eng

            # ── Subtitle defaults (overridden by initOptions at runtime) ──
            sub-font-size=55
            sub-pos=100

            # ── Network ──
            tls-verify=yes
            ytdl=no

            # ── Misc ──
            keep-open=yes
        """.trimIndent()

        File(mpvDir, "mpv.conf").writeText(mpvConf)
        Logger.d(TAG) { "Wrote mpv.conf (minimal — most options in initOptions)" }

        // Input config — key bindings (minimal for now, expanded in Phase 4)
        val inputConf = """
            # ANI-KUTA MPV input configuration

            SPACE cycle pause
            LEFT seek -10
            RIGHT seek 10
            UP seek 60
            DOWN seek -60
            WHEEL_UP add volume 5
            WHEEL_DOWN add volume -5
            j cycle sub
            J cycle sub down
            k cycle audio
            q quit
            ESC quit
            f cycle fullscreen
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

        // Configure disk cache (persistent cache for instant resume)
        val cacheDir = File(context.cacheDir, "mpv-cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Logger.d(TAG) { "Created MPV cache dir: ${cacheDir.absolutePath}" }
        }

        // Initialize the MPV view.
        // After this returns, BaseMPVView calls initOptions(vo) which sets
        // all the critical options (setVo, hwdec, demuxer-max-bytes, etc.).
        view.initialize(mpvDir.absolutePath, cacheDir.absolutePath, "warn")
        Logger.i(TAG) { "MPV initialized (config: ${mpvDir.absolutePath}, cache: ${cacheDir.absolutePath})" }
    }
}
