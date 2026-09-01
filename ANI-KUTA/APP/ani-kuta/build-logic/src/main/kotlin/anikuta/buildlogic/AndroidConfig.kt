package anikuta.buildlogic

/**
 * Shared Android configuration for all ANIKUTA modules.
 * Applied by the convention plugins in build-logic.
 */
object AndroidConfig {
    const val applicationId = "com.confused.anikuta"
    const val compileSdk = 36  // Kept at 36 (was originally for Nav3; Nav3 removed D-150, SDK left at 36 for the compose 1.10 line + future-proofing — D-322)
    const val minSdk = 24
    const val targetSdk = 36
    // ── CloudStream V2 era (Task 51 / round 11) ──────────────────────────────
    // The CloudStream rebuild on streaming/CLOUDSTREAM-V2 (forked from main
    // v0.2.63) opens the 0.3.x line: a fresh, modular, well-documented CS
    // implementation (plugin system + repos + trust UI + search + details +
    // episodes/seasons; playback deliberately excluded until its own port).
    // ── Task 52 (round 12) — the playback port ───────────────────────────────
    // 0.4.0 completes the CS system: link resolution (loadLinks), the dedicated
    // Media3 ExoPlayer engine (:core:cs-player) and the dedicated CS watch
    // screen (:feature:cs-watch) — the aniyomi playback stack untouched.
    // Minor-bump precedent: headline features get the minor (0.2.63 → 0.3.0).
    // versionCode continues the monotonic line from 0.3.0's 64.
    //
    // ── Task 56 (round 16) — the device-feedback-fixes release ─────────────
    // 0.4.4 fixes the five v0.4.3 device findings (doc cloudstream-v2/07):
    // F1 the resolve sheet NEVER auto-opens playback (remembered-server +
    // single-link auto-selects removed — the user always picks), F2 quality
    // chips sort highest-LEFTMOST with Unknown/Auto at the far right (both
    // stacks — the aniyomi accordion had NO sort at all), F3 sub/dub episode
    // lists show per-flavor ordinals (Dub restarts at "EP 1") + tag-stripped
    // names, F4 COMBINED mode merges sibling rows pairwise by ORDINAL (the
    // global-numbering reality broke the round-15 number-equality pairing —
    // 12+12 shows now render 12 rows and a tap resolves BOTH flavors), F5 the
    // LazyColumn duplicate-key crash on multi-quality DASH manifests (all raw
    // lists key by row index). Auto-advance stays within the current flavor.
    //
    // ── Task 57 (round 17) — the linked-progress + overlay-subs release ────
    // 0.4.5 fixes the six v0.4.4 device findings (doc cloudstream-v2/08):
    // P1 sub/dub share ONE progress identity (the flavor ORDINAL — sub-5 at
    // 80% shows 80% on dub-5; watched toggles, ratings, mark-series and
    // tracker sync all run on the ordinal keys for CS-bridged tagged lists),
    // P2 COMBINED rows show their SUB·DUB variant pills again (details audio
    // pill via scanlator, watch page + episodes sheet via structured flavors),
    // P3 the COMBINED dual-resolve dedup keys on (url + audio) so a shared
    // encode URL no longer erases one flavor's whole section (both merge
    // sites), P4 the debug toolkit (Settings → Debug options page: bubble /
    // resolve-list sources / copy button, all default-OFF; when ON the
    // resolve lists gain per-row copy icons + a header report action), P5
    // smarter server-name/audio/resolution parsing (bracket vocabulary), P6
    // the OVERLAY subtitle system (provider subs fetch+parse+render in OUR
    // Compose overlay — no more REQUIRES_RELOAD re-prepares, no crash, no
    // zero-subs; sheet 0.55 height; embedded-track picks guarded with
    // revert+retry; settings sheet at aniyomi structural parity), P7/P8 the
    // player hardening (30 s retained back-buffer so backward seeks serve
    // from memory, live-state seekRelative, safe clamps, 100 ms ticker).
    //
    // ── Task 59 (round 19) — the v0.4.6 device-round fixes ────────────────
    // 0.4.7 fixes the v0.4.6 device findings (doc cloudstream-v2/10): (1)
    // the CS DOWNLOADS dead-callback fix — the episode row's download button
    // was still wired to the classic resolver (round 18 gated a dead
    // EpisodesSection param, not the live row lambda), so CS-bridged
    // downloads hit the CS-guard error instead of opening the CS source
    // picker; (2) the subtitle OVERLAY rewrite — the whole cue renders as ONE
    // multi-line Text now (natural line spacing — the v0.4.6 per-line Texts
    // double-ledged the inter-line gap and let the strokes overlap at large
    // fonts) with every decoration pass (per-line back-color boxes, shadow,
    // border) drawn from the SAME TextLayoutResult (a pass can no longer
    // detach from the glyphs — the "subtitle at top, border at bottom"
    // finding) + a 4% horizontal wrap inset; (3) the subtitle DEFAULTS +
    // RESET — font size MAX (100), scale 0.5x, border 5 per the user's spec,
    // with a Reset button on BOTH subtitle settings sheets (the aniyomi
    // change additive-only); (4) the resolve sheets' formatting toggle is a
    // distinct BORDED pill ABOVE the episode title (the v0.4.6 header was
    // click-anywhere + popped a menu over the title); (5) the .WHITECAT
    // plugin share format v2 — the extension is just .WHITECAT (the legacy
    // .moviebox.WHITECAT still imports), the export carries METADATA
    // (anikuta/export.json: source repo URL + icon URL + catalog fields +
    // anikuta/icon.png embedded icon bytes) so the receiver keeps the icon
    // + repository, the import gate is CONTENT-FIRST (.bin/renamed files
    // analyzed by their zip manifest), the confirm dialog is titled with
    // the plugin's name, and Add shows a clean 1.5s "Plugin added" then
    // hands off to the EXTENSIONS page.
    //
    // ── Task 60 (round 20) — the v0.4.7 device-round fixes ────────────────
    // 0.4.8 fixes the nine v0.4.7 device findings (doc cloudstream-v2/11):
    // (1) the CS subtitle overlay's line-gap bug ROOT-CAUSED — the fill Text
    // set fontSize but not lineHeight, so Material3's ambient bodyLarge
    // leaked a FIXED 24sp line box in (huge gap at 0.5x scale, overlapping
    // glyphs at 2x+); the overlay now passes an EXPLICIT font-proportional
    // lineHeight (1.2x) so the inter-line gap is a constant ~20% of the glyph
    // height at every size and scale; (2) BOLD subtitles default ON on BOTH
    // stacks (MPV sub-bold + the CS overlay); (3) the subtitle sheets' Reset
    // asks for CONFIRMATION first (both stacks, same dialog); (4) the
    // resolve/quality sheets' formatting toggle moved ONTO the episode
    // HEADING — tapping the title text pops a small DISTINCT-BORDER menu with
    // the Formatted-sources switch (the round-19 standalone pill removed, all
    // four sheets); (5) the .WHITECAT share format drops the legacy
    // .moviebox.WHITECAT compatibility (the user: no old-format support) and
    // the import CONFIRM page shows the plugin's embedded ICON; (6) the
    // post-Add hand-off lands on the CLOUDSTREAM tab of the extensions page
    // (was the aniyomi tab); (7) the download rows' server name flexes with a
    // trailing "…" so the progress percentage always stays visible (both
    // stacks); (8) the Downloaded-page crash (duplicate LazyColumn keys from
    // the denormalized content grouping) fixed by grouping on the stable
    // contentId.
    //
    // ── Task 58 (round 18) — the both-stacks-debug + downloads + share ────
    // 0.4.6 fixes the v0.4.5 device findings + lands the two requested
    // features (doc cloudstream-v2/09): (1) the debug toolkit now covers BOTH
    // extension stacks — the aniyomi ResolverSheet + in-player QualitySheet
    // gain the same gated copy-report/row-copy/raw-URL affordances (shared
    // DebugPreferences flags, default OFF, live-collected), (2) the CS
    // subtitle live-view + accuracy fixes — settings apply while PAUSED
    // (hoisted live-style state; the non-reactive prefs read + ticker
    // equality-dedup were the root cause), MPV-unit-parity border math
    // (linear borderSize/55, no 0.15 saturation), per-line ASS BorderStyle=3
    // background boxes hugging glyph bounds (padding = border width, no fixed
    // dp), shadow drawn IN ADDITION to the border, no maxLines truncation,
    // fontScale now scales the Media3 view too, (3) the CloudStream
    // DOWNLOADS port — the details page's download button opens the CS
    // resolve sheet in DOWNLOAD mode, a pick enqueues through the SAME
    // source-agnostic engine (queue/service/SAF/notifications/downloads
    // screen/download-state chips + MPV offline playback ride the
    // mainId|episodeKey identity), DASH links filtered + counted, and (4) the
    // .moviebox.WHITECAT plugin share/import — Share action on every plugin
    // detail page (FileProvider ACTION_SEND), the exported
    // PluginImportActivity (VIEW/SEND filters, ONE confirm dialog, repo
    // linkage when a catalog match exists, untrusted record) + the
    // pending-nav hand-off to the plugin's detail page.
    //
    // ── Task 53 (round 13) — the playback-fixes release ─────────────────────
    // 0.4.1 root-causes every v0.4.0 device finding (doc cloudstream-v2/04):
    // RC-1 vendored M3u8Helper's invented referer param (AniKoto's 0-links,
    // 19 s silent walk), RC-2 the player's default Mobile-Chrome UA (the 428
    // class on UA-picky CDNs) + clean-retry profile, RC-3 the collectAsState
    // one-dispatch lag that replayed the previous episode's link (generation
    // lock + engine hard-reset), RC-4 upstream's 120 s loadLinks timeout,
    // RC-6/7 the AnymeX-pattern resolve sheet + Sources/Audio&Subs sheets,
    // RC-8 the diagnosability overhaul (request-profile logging, one filter).
    // ── Task 54 (round 14) — the watch-page UI-parity release ───────────────
    // 0.4.2 makes the CloudStream stack LOOK like the aniyomi stack (doc
    // cloudstream-v2/05): the resolve sheet renders in the aniyomi
    // ResolverSheet's design (server accordion + quality chips + RobotoFamily),
    // the CS watch screen becomes a real two-mode watch PAGE (pill bar + 16:9
    // player + currently-playing description + episode rows + fullscreen
    // controls with lock/canvas-seekbar/speed sheet), and the player sheets
    // adopt the Qualities-and-Servers / Subtitles / Speed sheet languages.
    // Aniyomi remains byte-untouched — parity via replicated design tokens.
    // ── Task 61 (round 21) — the QoL release ─────────────────────────────────
    // 0.4.9 lands the v0.4.8 device round's QoL batch (doc cloudstream-v2/12):
    // the plugin icons NEVER render blank (SubcomposeAsyncImage fallbacks on
    // the extensions list/detail/import-confirm/added pages + export.json only
    // carries http(s) iconUrls), the Format-sources menu opens ABOVE the heading
    // with the exact "Format sources" label + a guaranteed label↔toggle gap,
    // real SEARCH PAGINATION (approach-bottom pre-fetch + the Loading-more
    // footer) on AniList + aniyomi extensions + CloudStream search, the
    // CloudStream browse sections RANDOMIZE on every search-page entry, the
    // section titles open CATEGORY SUBPAGES (heading + grid + infinite scroll),
    // the image loader rides a dedicated 2-concurrent-request client with dim
    // card placeholders (the performance pass), PULL-TO-REFRESH on the search
    // page (the CS browse cache invalidated first), the library category chips
    // auto-scroll the selection into view + the text-width underline rework, the
    // downloaded-episodes UI (collapsed by default, animated expand, separators,
    // the two-step morph delete, expand-left/delete-right, the EP count tag), and
    // the ads system (the real sponsor URL, 5s min-time, the offline deferral).
    // ── Task 62 (round 22) — the v0.4.9 device round: stability + linkage ──
    // 0.4.10 fixes the post-plugin-import CRASH (the stale-closure double
    // push of ExtensionsSettingsKey under AnimatedContent → "Key
    // ExtensionsSettingsKey was used multiple times"), links MANUALLY
    // installed .cs3 plugins to repositories added LATER (the ordered
    // CsPluginIdentity ladder: exact name → linked repo name → URL → file
    // hash → normalized names; the linkage back-fill + the duplicate-row
    // removal), floats the Format-sources menu fully ABOVE the heading /
    // OUTSIDE the bottom sheet, retrains the CloudStream section
    // RANDOMIZATION (only on a true search-tab exit + return or a
    // pull-to-refresh; the arrangement is PERSISTED and restored across app
    // restarts; the smart shuffle keeps the FIRST FOUR of every category
    // unique across categories), restores the library chip UNDERLINE
    // (fillMaxWidth collapsed to 0 inside the LazyRow — IntrinsicSize.Min),
    // lands the library performance pass (H1/H2/M1/M2/M3/M4: off-main bulk DB
    // mutations, the debounced off-main filter+sort, the snapshotFlow PTR
    // threshold, the hoisted scroll-gated shared-element registration, the
    // root recomposition split), shows "(N Episodes Downloaded)" on the
    // downloaded cards, and adds FOCAL-POINT pinch zoom to the cover viewer
    // (the fingers stay glued to the image point under them; pan kept).
    const val versionCode = 75
    const val versionName = "0.4.10"

    // HARD RULE (CORE_RULES.md §8, updated D-251 per user instruction): ONLY
    // arm64-v8a in SHIPPED APKs. No armeabi-v7a, no x86/x86_64.
    // EXCEPTION (user-authorized, D-246 emulator-testing support): a TEST-ONLY
    // x86_64 build is produced in CI via `-PemulatorX64Build=true` — it goes to a
    // SEPARATE artifact and never ships. The main APK stays arm64-v8a-only.
    val abiFilters = listOf("arm64-v8a")

    /** ABIs for the CI emulator-test build (native x86_64 — no ARM translation). */
    val emulatorAbiFilters = listOf("x86_64")

    // JVM target for Kotlin + Java
    const val jvmTarget = "17"
}
