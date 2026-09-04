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
    // Task 64 (round 24 — THE ORDERED RE-DO after the v0.4.11 revert):
    // baseline reverted to ba3c6937 (v0.4.10) per the user's instruction,
    // then the improvements re-implemented ONE BY ONE with per-item CI
    // verification: (A) the library performance deep pass, take two (image
    // fetch cap 2→12 total/8 per-host; the in-memory category switch — the
    // full-set cache; per-cell animation fast paths; the shared-element gate
    // lambda; the index-only velocity signal), (B) the library chips
    // full-name fix (IntrinsicSize.Min sized multi-word names to ONE WORD —
    // IntrinsicSize.Max + no ellipsis) + the centered auto-scroll, (C) the
    // downloads tag (no parentheses, bold count only) + the console-log
    // family removed (the Debug page + all its other functionality stays),
    // (D) the genres radar rework (bigger heading, the dedicated genre
    // section below it, the category filter with the honest empty/fallback
    // ladder — the section can never disappear), (E) the CS browse category
    // identity (original shelf indexes pre-compaction + same-title merges),
    // (F) the watch-activity weekday-label clipping fix, and (G) the
    // update-check LIVE status notification + the content-update history
    // page (JSON file — NO database changes this round).
    //
    // Task 65 (round 25 — the seven device findings, doc cloudstream-v2/16):
    // (A) the SCHEDULE duplicate-key crash — the getUpcomingSchedule family
    // INNER JOINed library_item (one row PER CATEGORY) so an anime in two
    // categories doubled every schedule row → identical LazyColumn keys →
    // IllegalArgumentException; now EXISTS semi-joins + defensive dedupes
    // (+ the UpdatesScreen keys include audioVariant — sub/dub rows collide
    // by design); (B) the library LIST-mode scroll jank — DetailTagRow's
    // per-row LazyRow (SubcomposeLayout + saveable state + gesture nodes per
    // row for 2-5 fixed pills) replaced by a plain Row + horizontalScroll +
    // the tag list remembered per (entry, config, theme) — the structural
    // difference vs the smooth grid modes, 50-268ms frames + 16MB GC per
    // fling in the user's logcat; (C) the downloads delete UX — the icon
    // morph normalizes DeleteForever's edge-to-edge glyph (the 3x perceived
    // jump) + the exit choreography (settle pulse → slide-out + fade → VM
    // delete) + animateItem so the cards below glide up; (D) the heatmap
    // weekday labels — the 8sp Text inherited bodyLarge's 24sp line box in a
    // 14dp slot (the REAL residual clip); explicit lineHeight + a -1dp
    // optical lift to the exact cell-row centers; (E) the details metadata
    // stack — up to four icon rows (year → rating → status → episodes),
    // hidden per-fact when unavailable, CS 0-10 scores normalized to %;
    // (F) the CS browse category bleeding + PHASED loading — HomePageList
    // NAME matching (providers that ignore MainPageRequest.name returned the
    // whole home into every row), the progressive skeleton→per-shelf→final
    // pipeline with shimmer rows + an N-of-M status line, and put() carries
    // the display forward so background refreshes never re-arrange mid-view;
    // (G) D-388 the update-notifications module rework — audible results
    // channel, rich per-anime BigText notifications with next-check info +
    // the cover as large icon + the history deep-link, the pinned next-check
    // card (live countdown + WorkManager's real fire time + the due anime
    // with covers + the how), 12h/device-time formatting, covers + Details
    // navigation on every history row, the dedicated Settings→Updates row +
    // check-now, and the Debug→Update Check History button.
    //
    // Task 66 (round 26 — the four v0.4.13 device findings, doc
    // cloudstream-v2/17): (A) D-389 the armed delete icon GROWS to 3x — the
    // round-25 0.65x "normalization" was the exact inverse of the request
    // (animateFloatAsState 1f→3f over 220ms, draw-phase scale so the row
    // geometry + tap target stay stable); (B) D-392 the robust delete logic
    // — the .data.json remove is now a 3-attempt ladder (normal write →
    // fresh-index retry → nuclear delete-recreate), deleting the LAST
    // episode removes the WHOLE series folder (identity-checked safety
    // ladder: never the SAF root, never a format folder, mainId re-confirmed
    // at the last moment) + sweeps every DB row, delete-all is ONE atomic
    // folder operation instead of an N-walk loop, and every phase logs its
    // outcome; (C) D-390 the dedicated CsBrowseLoader module — the round-25
    // bleeding fix failed because shelfLists' all-lists fallback still poured
    // name-ignoring providers' whole home into every row, and ALL shelves
    // fetched at once read as "rushing"; now the plan skeleton (zero
    // network) → STRICTLY SEQUENTIAL shelf fetches in the provider's own
    // order → a strict name matcher (exact → fuzzy → EMPTY, never
    // all-lists) → static-home snapshot detection (ONE fetch replaces N) →
    // a duplicate-content safety net, with the category subpages sharing the
    // matcher; (D) D-391 the completed smart-update system + the
    // release-aware next check — ScheduleEngine (re-)aims one-shot checks
    // for EVERY future airing the moment it discovers them (7-day horizon,
    // airingAt + the per-anime LEARNED delay, WorkManager-tagged), the
    // history countdown = min(the earliest smart one-shot, the periodic
    // fire), the "releasing before the next check" list is filtered by
    // next_airing_at (only what's REALLY releasing — each row shows EP n +
    // the release time + countdown), and the engine summary + notification
    // next-check line are release-aware.
    //
    // Task 67 (round 27 — the five v0.4.14 device findings, doc
    // cloudstream-v2/18): (A) D-397 the armed delete glyph grows 2.5x IN THE
    // LAYOUT PHASE — the round-26 draw-phase scale(3f) painted a 48dp glyph
    // out of a 16dp box (the rounded card Surface clipped its top/left/right
    // + the rasterized layer blurred, and 3x overshot the re-spec); the
    // glyph's dp size (16→40dp / 20→50dp) AND the IconButton frame (32→48dp /
    // 36→56dp) now animate as real MEASURED size, so nothing can clip and the
    // vector re-rasters crisply; (B) D-397b the episode rows are key()'d by
    // episodeKey — the forEachIndexed Column's POSITIONAL remember slots made
    // the row moving up after a delete inherit the dead row's exit Animatables
    // (alpha=0, translated away — "its content disappears" until re-expand);
    // (C) D-393 the DISK-TRUTH file deletion — the round-26 flow deleted
    // files only via the .data.json URIs, so a stale/missing entry silently
    // skipped the file deletion while the DB row still died ("the files are
    // there"); Phase 2 now adds deleteEpisodeFilesOnDisk() — a
    // census→delete→verify sweep of episodes/ + subtitles/ (+ legacy root
    // files) keyed on the episode number FROM THE DB ROW, matching the
    // canonical filename token (full-token regex so EP 1 never matches
    // E00001.5) with retry rounds + a survivors report (empty = the on-disk
    // guarantee), and the series-folder cleanup decides by the DB alone
    // (dbRemaining==0 — immune to .data.json ghosts, never nukes files whose
    // rows are live) with a playable-files disk check before the row sweep;
    // (D) D-395 the search top bar is a nested-scroll reveal LATCH — any
    // downward delta collapses, any upward delta reveals, content-mode
    // transitions re-reveal (the old OR-of-all-three-scroll-states expression
    // latched collapsed from a dead Idle column's remembered ScrollState and
    // only re-expanded at the literal top); (E) D-396 the update-check history
    // records + resolves the smart-schedule MATH per series — the check-time
    // record (nextAiringEpisode/At, learnedOffsetMs, expectedCheckAt = airing
    // + the same clamped offset as SmartReleaseScheduler) + the LANDED
    // WorkManager one-shot fire time resolved live via the new per-anime
    // sr_main_<mainId> tag, rendered as a 4-line fact panel (Next release /
    // Learned delay / Calculated / Landed + drift).
    //
    // Task 68 (round 28 — the five v0.4.15 device findings + THE ONBOARDING
    // WIZARD, doc cloudstream-v2/19): (A) D-399 the delete-button armed
    // choreography is now ONE synchronized animation — the round-27 version
    // ran THREE independent animations (a 150ms AnimatedContent glyph morph
    // inside a 220ms size grow + the frame grow at each call site), and the
    // morph finishing early made the grow read as a two-stage stutter; ONE
    // armedProgress (260ms, Motion.EasingEmphasized) now drives the glyph
    // size, the IconButton frame, AND a staggered overlap-free crossfade
    // (the 2.5x layout-phase growth + crisp re-raster stay); (B) D-400
    // tapping ANYWHERE outside the armed button now disarms it — the armed
    // state is hoisted to the SCREEN level (one armed button in the whole
    // list) + an ancestor pointerInput interceptor observes every pointer
    // DOWN and disarms whenever the touch's window position is OUTSIDE the
    // armed button's own reported window-space rect; (C) D-401 THE
    // DATA.JSON-FIRST DELETION PIPELINE (the user's explicit spec: update
    // the .data.json, THEN delete the content) — the round-27 order deleted
    // the files BEFORE the .data.json write (the exact SAF stale-URI window),
    // the 1-episode "success" was a false positive (the last-episode folder
    // delete removed the whole .data.json so the entry-removal write never
    // had to land), and NOTHING serialized concurrent deletes (two
    // read-modify-writes resurrected each other's entries and BOTH computed
    // dbRemaining>0 so neither fired the last-episode folder cleanup). Now:
    // the phases run DB capture → locate → capture entry → UPDATE+VERIFY
    // the .data.json FIRST (while the tree is untouched) → file deletes
    // (captured URIs + the disk sweep) → series-folder cleanup → DB row LAST;
    // DefaultDownloadManager.deleteMutex serializes every delete +
    // DownloadStorageProvider.treeMutex serializes ALL tree mutations
    // (.data.json write/upsert/remove/replace + folder delete + disk sweep);
    // the two silent-success holes are closed via the unit-tested
    // DeletionMatching (key → episodeNumber key-drift reconciliation;
    // STRICT verification — a null re-read is a FAILURE); (D) D-402 the
    // search top-bar reveal SIGN FIX — the round-27 latch read Compose's
    // nested-scroll available.y as the scroll-position delta when it is the
    // FINGER displacement (proven from the pinned material3 PullToRefresh
    // source: positive y = "Swiping down" = the pull) — every reported
    // symptom mapped 1:1 to the inversion; the branches swap to the standard
    // app-bar semantics (finger up into the content collapses, finger down
    // toward the top / the P2R pull reveals), a derivedStateOf at-top
    // force-reveal guarantees the bar at the very top, and the decision is
    // the unit-tested searchBarNextCollapsed; (E) D-403 THE ONBOARDING SETUP
    // WIZARD — the new feature:onboarding module replaces the deleted
    // every-launch FirstRunSetupDialog: a custom NON-Material animated
    // welcome (the time-driven aurora canvas + particle field + the
    // staggered ANI-KUTA wordmark + the gradient-glow CTA), a LIVE theme
    // picker (8 curated cards mapping to ThemeMode/AccentPreset/amoled —
    // the whole app re-themes as the user taps), the three verified +
    // skippable permission steps (folder/notifications/battery — REAL
    // system checks re-verified on every ON_RESUME, never "we asked once"
    // flags) + a finish summary; AppPreferences.onboardingCompleted gates
    // the start destination, and the no-download-folder gate (all FIVE
    // download call sites) shows a clear error dialog with an inline picker
    // that VERIFIES the pick and RETRIES the download automatically. The CI
    // unit-test list now also runs :core:download, :feature:anime-search:impl
    // and :feature:onboarding tests. The update-check history improvements
    // are DEFERRED per the user ("let's work on it later").
    //
    // Task 69 (round 29 — the v0.4.16 device findings, doc cloudstream-v2/20):
    // (A) D-404 THE data.json DELETION INTEGRITY SYSTEM — the root cause
    // behind EVERY "data.json not updated / corrupted" report since round 25
    // was ONE primitive: openOutputStream(uri, "w") does NOT truncate on AOSP
    // ExternalStorageProvider (FileSystemProvider.openDocument →
    // ParcelFileDescriptor.parseMode("w") = MODE_WRITE_ONLY, no
    // MODE_TRUNCATE) — every write that SHRANK the .data.json left
    // new-json-head + old-json-tail = the user's "corrupted" file, which then
    // made findContentFolder (which only matches PARSEABLE jsons) skip the
    // folder and the LAST-episode delete skip every disk phase; the 1-episode
    // case always looked clean only because the folder-delete path proceeds
    // on unreadable json. The fix: every SAF write now opens "wt" (truncate)
    // + a post-write byte-length check; readDataJsonIndexed SALVAGES a
    // corrupted file (the string/escape-aware balanced-brace
    // DataJsonRepair.salvageCompleteJsonHead recovers the complete head —
    // the app self-heals the exact file v0.4.16 left on disk); the delete
    // flow REBUILDS the .data.json from DB TRUTH (rows-for-anime minus the
    // deleted row, metadata enriched from the existing entries — no matching,
    // no key drift, no ghosts) via the VERIFIED rewrite ladder
    // (rewriteDataJsonEpisodes: truncating write → strict re-read with
    // salvage DISABLED + exact (key,number) set equality → retry → nuclear
    // delete+recreate); findContentFolderByTitle locates folders whose json
    // is destroyed beyond salvage (the title fallback — delete-all + the
    // per-episode path), and DeletionMatching.matchRemoval stays for the
    // URI-delete entry capture. Unit-tested in DataJsonRepairTest (the
    // salvage table, the rebuild table incl. the exact device scenarios, the
    // strict equality table).
    // (B) D-405 THE ONBOARDING WIZARD v2 — the welcome background is now
    // five large MORPHING ORGANIC BLOB shapes (8 wobble control points each,
    // closed Catmull-style cubic paths, soft radial fills, Lissajous center
    // drifts, richer jewel colors) + a rotating outline-geometry layer (the
    // report: "animated shapes… blobs moving around… with some different
    // colors"); the "offline-first anime streaming" bottom line is REMOVED
    // (the version moved to the finish step); the tagline smoothly ROTATES
    // ("Your anime. Your rules." / "Your content. Your rules." / "I don't
    // make any promises." / "Don't expect anything." / "It is what it is.");
    // the theme step is a horizontal SNAP CAROUSEL with LIVE application on
    // settle + the System/Light/Dark mode row + the "further customize in
    // the settings later" note; the permission steps get a big centered icon,
    // ONE combined bottom button ("Skip for now" → "Continue" once granted),
    // and the Allow action DISAPPEARS once verified (replaced by a granted
    // state — the folder step keeps "Change folder"); the finish step is a
    // 2×2 summary grid; and the BROWSE PRELOADER warms the 3 sections' data
    // + every cover into Coil at the exact render sizes WHILE the wizard
    // runs, so "Start watching" lands on a fully materialized Browse.
    // (C) D-406 the wizard ⇄ app handoff crossfades (250ms emphasized) —
    // quick to enter, no visual cut from the animated canvas.
    //
    // Task 70 (round 30 — the v0.4.17 device findings, doc cloudstream-v2/21):
    // the device round CONFIRMED the data.json deletion system FULLY RESOLVED
    // (1-of-5 entry removal clean, repeated deletes clean, the last two
    // deleting the whole folder — "fully satisfactory") — no deletion code
    // touched this round. D-406 the wizard rework:
    // (A) THE BUTTON-AT-THE-TOP BUG (structural): the permission + finish
    // steps each emitted TWO root-level layouts (a fillMaxSize content
    // Column, then a SEPARATE bottom-CTA Column after it) and AnimatedContent
    // stacks root children at TopStart — the "Skip for now" / "Start
    // watching" CTAs OVERLAPPED the top of the screen. Every step is now ONE
    // root Column with the content weight(1f) and the CTA INSIDE it, pinned
    // to the bottom.
    // (B) THE WELCOME BACKGROUND STUTTER, fixed at the root: (1) the old
    // engine's two infinite-transition phases RAN 0→2π then WRAPPED to 0
    // while the blobs multiplied them by non-integer speeds — sin(2π×1.7)≠
    // sin(0), so every silhouette SNAPPED and every center TELEPORTED on a
    // fixed 11s/24s schedule (the "keeps resetting / jumps into frames");
    // the new engine runs ONE MONOTONIC CLOCK (frame-nano deltas, clamped
    // to 64ms so backgrounding PAUSES the art) — nothing wraps, ever. (2)
    // the old draw pass allocated a fresh Path + Array + 8 Offsets per blob
    // per frame (~50 objects/frame → GC churn → the "skipped frames"); the
    // new pass pre-allocates every Path, reuses FloatArrays, and caches the
    // radial brushes per (width, accent) — ZERO steady-state allocation, and
    // the clock is read ONLY inside drawBehind (draw-phase invalidation,
    // zero recompositions). The motion now matches the spec: shapes MORPH
    // between organic blobs and rounded polygons (per-blob side counts,
    // staged seamless cycles) and SPLIT — two halves born at the same
    // center with the same shape, drifting apart on a precessing axis with
    // wobble phases diverging only in proportion to the split, then merging
    // back — no pop at either end.
    // (C) THE THEME STEP: the Light/Dark toggle at the very top is an exact
    // replica of the appearance page's SegmentedToggle with EXACTLY two
    // options (no System — a SYSTEM pref initializes to the system's actual
    // mode); the carousel below shows only the selected mode's themes (every
    // choice now carries a mode bucket) as exact PalettePreviewCard replicas
    // (128×198) with the CENTER CARD BIG and the side cards at 76% + faded
    // alpha (a draw-phase graphicsLayer scale on the live scroll offset);
    // mode flips apply the new bucket's default card in one shot so the app
    // never flashes a mismatched accent during the 220ms settle debounce.
    // (D) THE PERMISSION STEPS stripped of every description the report
    // named; the big icon MORPHS into a check on grant (one clean line —
    // "Folder verified" — no repetition, no folder tree); the folder
    // re-pick is a FULL button; the combined Skip→Continue button sits at
    // the BOTTOM. Also: the finish CTA at the bottom, the step progress
    // numbering fixed (storage no longer duplicates the theme step's 1/5),
    // the back button's 48dp touch target, and the AutoMirrored arrow.
    // v0.4.19 (D-407, round 31 — the wizard-polish + subtitle-system round):
    // (A) THE WELCOME ART: the round-31 report — "when they combine together
    // with each other, they suddenly change their shades… the globes can
    // transform into different shapes and get merged with the different
    // random places and such, not a simple fixed path" — fixed at the root:
    // the blob layer now composites with BlendMode.Screen (overlaps blend
    // like light — the liquid merge), the split child is alpha-ramped with
    // the split itself (the double-draw shade POP at birth/merge is
    // mathematically gone), each center wanders on THREE incommensurate
    // harmonics (never retraces; blobs genuinely cross at different places),
    // each blob cycles through a SEQUENCE of polygon shapes (staged
    // crossfades — hexagon → triangle → pentagon → square), and the radius
    // breathes. Still zero steady-state allocations + the single monotonic
    // clock.
    // (B) THE THEME CAROUSEL centered on the LazyRow's cross axis — the
    // report: "not aligned to the top with the light and dark buttons but
    // centered between the bottom one and the above one".
    // (C) THE PERMISSION STEPS: the granted state (morphed check + label)
    // renders in an explicitly-CENTERED AnimatedContent over full-width
    // children — the report's left-aligned/"glitched" granted state is
    // structurally impossible now; the folder step shows the FULL readable
    // folder path ("Internal storage › ANI-KUTA › …") in a glass panel under
    // "Folder verified".
    // (D) THE ADS FIRST-OPEN GRACE: the report — "for the very first time
    // the user opens up the application and clicks on any of the contents,
    // he should not be shown the advertisement pop-up… afterwards the normal
    // advertisement system will work" — a persisted one-per-install flag
    // (AdPreferences.consumeFirstOpenGrace) lets the FIRST gated navigation
    // through with no interstitial + no cooldown; every later navigation
    // follows the normal 6h-cooldown system.
    // (E) THE DOWNLOADED-EPISODE SUBTITLES — THE core fix: the details-page
    // hand-off passed "" for subtitle tracks when playing a downloaded
    // episode (the files were on disk; the player never looked). ONE shared
    // resolver (DownloadManager.resolveSubtitleTracks: DB subtitleUris → the
    // episode's dedicated subtitles/ folder disk scan, labels from the
    // filenames) now feeds the details hand-off, the downloads hand-off, and
    // the in-player episode switch (which also drops its "Subtitle N"
    // generics).
    // (F) THE MANUAL SUBTITLE IMPORT — the report: "add a permanent option
    // there: the option to add subtitles manually… pick any kind of subtitle
    // files (VTT, SRT, or any other relevant ones). After selecting those
    // files, those subtitles will start to show up properly" — a permanent
    // "Add subtitle file" row in the subtitle sheet → the multi-select SAF
    // picker → each file validated (.srt/.vtt/.ass/.ssa/.sub/.ttml) →
    // PERSISTED into the downloaded episode's subtitles/ folder (DB row +
    // .data.json — the disk-truth chain the scanner rebuilds from) → staged
    // → MPV sub-add with "select" (activates immediately, appears in the
    // refreshed list). Streamed episodes get session-scoped staging.
    // v0.4.20 (D-408, round 32 — the subtitle-system maturity round; the
    // wizard was APPROVED end-to-end this round — zero wizard changes):
    // (A) THE DOWNLOADED-SUBTITLE DETECTION made BULLETPROOF — the v0.4.19
    // report: "It did not show me the subtitles in the subtitles category.
    // This was a huge issue." resolveSubtitleTracks is now a LAYERED chain:
    // (0) a stale in-memory cache is reloaded from the DB once (the
    // download-just-completed → open race); (1) the DB row's subtitleUris;
    // when empty the disk chain — (2) the episode's OWN video file location
    // (the most direct truth: immune to .data.json corruption + mainId
    // drift, via the new findSubtitleFilesForEpisodeNearVideo SAF walk),
    // (3) the mainId manifest walk, (4) the title fallback
    // (findContentFolderByTitle — the delete flow's proven locator).
    // labelForUri fixed to read the on-disk FILE NAME (the last / segment —
    // the round-31 version read the whole decoded document path, so every
    // label silently fell back to "Subtitle N").
    // (B) THE SHEET'S "AVAILABLE IN STORAGE" SECTION — the belt-and-braces
    // listing: when the sheet opens, the episode's on-disk subtitle files
    // (resolved through the same layered resolver, minus the ones already
    // loaded as MPV tracks) are listed; tapping one loads it via the PROVEN
    // manual-import path (stage → sub-add "select" → refresh) — "when the
    // user clicks on those subtitles will be loaded from storage onto the
    // player and will be shown exactly like how they currently are".
    // (C) THE "Add subtitle file" ROW — the report: "it should be shown
    // below the subtitle settings but above the off button… its description
    // should not be shown" — moved to the FIRST list item (below the fixed
    // Settings row, above Off) + the description line removed.
    // (D) THE SUBTITLE SETTINGS LOADING FIX — the report: "When I opened a
    // new episode, my old subtitle settings were not applied directly… I had
    // to change something" — the reliable LIVE apply
    // (AnikutaMPVView.applySubtitlePreferences, setProperty* for numerics)
    // now runs on FILE_LOADED + the PLAYBACK_RESTART fallback (every new
    // file, episode switch, fresh screen) instead of ONLY from the settings
    // sheet's on-change callback.
    // (E) THE REMEMBERED SUBTITLE SELECTION — the report: "make it remember
    // the location of the selected subtitle files… that subtitle will be
    // selected and will be pre-applied on that" — a per-series memory
    // (PlayerPreferences.get/setPreferredSubtitleTrack: "" = none, "off" =
    // explicitly off, else the track's label) persisted on every sheet
    // selection (incl. Off), manual import, and storage-row load; pre-applied
    // via the new PlayerObserver.onTracksLoaded hook after EVERY track-list
    // reload (matches the label against the live tracks and sets sid).
    // (F) THE IMPORT DEDUP — the report: "another copy of that subtitle file
    // is created in the subtitles folder of that specific series" — a dedup
    // phase (document-id or filename match) before the copy: picking the
    // episode's OWN subtitle file returns the EXISTING track (no _manual_
    // duplicate; the DB row is repaired if it was missing the file).
    // Also: SubtitleEngine.guessExtension gained .ttml (MPV detects external
    // sub formats by extension).
    // ── v1.1.1 (D-409..D-414, round 33 — THE FIRST PUBLISHABLE RELEASE) ─────
    // The v0.4.x device-QA line (testplay-byte) is CLOSED; streaming/
    // CLOUDSTREAM-V2 merged into main and release/1.1.1 cut from it. The
    // publishable line: (D-409) the debug bubble removed COMPLETELY (module +
    // source sets + every call site; show-sources/copy-button/update-check
    // history KEPT); (D-410) the empty episode-check run is SILENT (the
    // "nothing was due" notification only fired when totalChecked > 0);
    // (D-411) the in-app updater + release links point at the published repo
    // Confused-Creature-180/ANI-KUTA (APK-only); (D-412) release logging OFF
    // (Logger + the com.lagradost.api.Log facade; E kept); (D-413) R8 full
    // mode + resource shrinking + the plugin-compat keep rules + the
    // keystore.properties release-signing plumbing; (D-414) logcat/seeker/
    // androidx-media/truetype-parser/stray-rxjava deps removed + the
    // hardcoded deps moved into the catalog. versionCode jumps to the
    // major*10000+minor*100+patch scheme (1.1.1 → 10101; 10101 > 85 keeps the
    // install-over line monotonic for sideload-onto-v0.4.20 devices).
    const val versionCode = 10101
    const val versionName = "1.1.1"

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
