# REMOVING THE TORRENT SYSTEM (the user's explicit removability order)

The torrent feature is built to be excised cleanly if it ever causes problems.
Six seams, all documented, no hidden entanglements:

1. **Delete the module**: `core/torrent/` (everything — engine dependency, wiring, prefs).
2. **`settings.gradle.kts`**: remove the `include(":core:torrent")` line.
3. **`app/build.gradle.kts`** + **`feature/cs-watch/impl/build.gradle.kts`**:
   remove the `implementation(project(":core:torrent"))` lines.
4. **`AnikutaApp.kt`**: remove `torrentModule` from the module list + the
   `TorrServerLoader(...).cleanupStaleCache()` call (+ the two imports).
5. **`feature/cs-watch/impl/CsWatchViewModel.kt`**: remove the injected `TorrentGateway`
   param + the torrent-prep block in `requestPlay` (torrent picks then behave like the
   pre-torrent eras: the resolver's filter re-hides the links) + the status-poll field +
   the sheet's torrent badge param.
6. **`data/cloudstream/.../CloudstreamLinkResolver.kt`**: revert the `allowTorrentLinks`
   param to hard-disabled (delete the passthrough branch — the TORRENT/MAGNET hide+count
   branch is the original code, kept intact behind the flag).
   **`core/cs-player/.../CsLinkModels.kt`**: delete the `CsLinkType.TORRENT` enum value.
7. **`app/.../settings/TorrentSettingsScreen.kt`**: delete the file + the Settings-hub row +
   the search-index entry + the nav branch/Key.

Leftovers: the two pref keys (`torrent_enabled`, `torrent_disclaimer_accepted`) are inert
booleans in the prefs store — harmless; a data cleanup can drop them later.
The APK loses the whole native Go engine (the size drop is immediate on the next build).
