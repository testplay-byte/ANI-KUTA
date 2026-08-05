# 09 — Rebuild Notes

> What to carry over, redesign, and drop when rebuilding ANI-KUTA from scratch.
> This is the guiding reference for Phase 1 (architecture planning).

---

## ✅ Carry Over (proven patterns worth keeping)

### Architecture
1. **Two-tier identity (ADR-050)** — `LocalId` + `ContentId`. This is the backbone of source-agnostic data. MUST keep.
2. **Pluggable registries** — `List<T>` in Koin for every extension point. Adding a provider = one class + one Koin line. Excellent pattern.
3. **Gateway interfaces** — declared in `:core:*`, impl in `:data:*`. Keeps core free of data deps. Keep.
4. **Modular Gradle architecture** — `:app`, `:core:*`, `:data:*`, `:feature:*`. The split is good.
5. **Convention plugins** (buildSrc) — `anikuta.library.gradle.kts`, `anikuta.library.compose.gradle.kts`. Reduces boilerplate. Keep.
6. **Reactive everything** — `Flow`/`StateFlow` throughout. UI always reflects state. Keep.

### Features
7. **Single MPV instance** (ADR-025) — fullscreen is overlay swap, not navigation. Player keeps playing when "minimized". Excellent UX. Keep.
8. **In-place source switching** — AniList ↔ Extension without leaving details page. Powerful. Keep (but simplify the VM).
9. **Aniyomi extension compat** (ADR-029) — loads existing Aniyomi extensions. Huge ecosystem benefit. Keep.
10. **Setup wizard** — first-launch onboarding. Good UX. Keep (but split the 1840-LOC file).
11. **Backup with Aniyomi format translator** — import/export Aniyomi backups. Keep.

### Implementation Details
12. **`ScrollBlurOverlay`** — gradient scrim, GPU-cheap, no `RenderEffect`. Keep.
13. **`HlsDownloader` PNG header stripping** — handles anti-scraping CDNs. Keep.
14. **Dispatchers injected** (`DispatcherProvider`) — testable. Keep.
15. **Phased DB migration system** in `App.kt` — one-shot preference flags, idempotent, try/catch. Keep the pattern.

---

## 🔄 Redesign (good idea, needs better execution)

### Code Quality
1. **`WatchScreen.kt` (2386 LOC)** — split into smaller composables. One file = one responsibility.
2. **`AnimeDetailsViewModel` (1013 LOC)** — too complex. Split into sub-VMs or use a coordinator pattern.
3. **`SetupWizard` (1840 LOC, 1 file)** — split into one file per step.
4. **Stale READMEs** — `:feature:settings` says "NOT YET IMPLEMENTED" but is fully built. Enforce README updates as part of "done".
5. **`:data:history` unused** — implemented but UI reads `WatchProgressStore` instead. Either use it or remove it. Pick one.
6. **5 version catalogs** — `libs/androidx/compose/kotlinx/anikutaLibs`. Consolidate to fewer (1-2) for simplicity.

### Navigation
7. **Voyager migration was planned but not done** — hand-rolled state machine in `MainActivity.kt`. The new project should use Voyager (or Compose Navigation) from day one.
8. **Voyager 1.0.1 lacks `rememberNavigator()`** — back stack lost on Activity recreate. Use a newer version or Compose Navigation.

### Testing
9. **No unit tests** — zero feature modules have tests. The new project MUST have tests for core logic (repositories, managers, resolvers).
10. **No UI tests** — critical flows (browse → details → watch) should have UI tests.

### Build
11. **R8/minify off** — enable in release for the new project (smaller APK, security).
12. **Non-transitive R class** — `android.nonTransitiveRClass=false` in old project. Set to `true` for smaller APK.
13. **No baseline profile** — add a `:baselineprofile` module for faster startup.
14. **No configuration cache** — enable `org.gradle.configuration-cache=true`.

---

## ❌ Drop (don't carry over)

1. **Ads system** (`:core:ads`) — unless the user explicitly wants ads. The "poison ad" concept is unusual; confirm before rebuilding.
2. **`Injekt` dual DI** — only needed for Aniyomi extension compat. If we can isolate it to `:core:source-api` only, do that. Don't let it spread to the host app.
3. **Empty stub modules** — old project had 5+ stubs that were removed in Phase 9. Don't create stubs in the new project; add modules when they have content.
4. **`agent-ctx/` session notes** — the old project's Z.ai Code session notes are not structured. The new project's `AGENT-CONTEXT/` is the proper replacement.
5. **`DOCS/` with only 2 files** — too sparse. The new project's documentation lives in `AGENT-CONTEXT/knowledge/` + `REFERENCES/old-kuta/DOCUMENTATION/`.

---

## 🆕 Add (new project improvements)

1. **Proper Compose Navigation** (Voyager or Compose Navigation) from day one — no hand-rolled state machine.
2. **Hilt instead of Koin** — our CORE_RULES.md (D-009) already decided Hilt. (Note: old project used Koin for Aniyomi extension compat — we may need Koin alongside Hilt for extensions, or isolate it.)
3. **Room instead of SQLDelight** — our tech stack (D-009) decided Room. SQLDelight works but Room is more standard.
4. **Retrofit instead of raw OkHttp** — our tech stack decided Retrofit.
5. **Unit tests from day one** — at least for repositories, managers, resolvers.
6. **ktlint/detekt** — code quality enforcement.
7. **GitHub Actions CI for tests** — not just APK builds.
8. **Modular UI customization** — the user wants highly customizable UI. Build a theme engine (`:core:design`) from the start, per our `knowledge/architecture.md`.

---

## ⚠️ Key Decisions for Phase 1

These need user input before architecture planning:

1. **Ads system** — keep or drop? (The old project has a full ad system.)
2. **Koin + Hilt** — use both (Hilt for app, Koin for extension compat)? Or Hilt only with extension isolation?
3. **Room vs SQLDelight** — our stack says Room, but old project's SQLDelight schema is proven. Confirm switch?
4. **Voyager vs Compose Navigation** — which for the new project?
5. **Aniyomi extension compat** — is this a hard requirement? It shapes `:core:source-api` significantly.
6. **Manga reader** — still deferred? Or plan for it now?
7. **Notifications** — build from the start or add later?

---

## Module Mapping (old → new, proposed)

| Old Module | New Module | Notes |
|-----------|-----------|-------|
| `:app` | `:app` | Keep, use Hilt + Voyager. |
| `:core:common` | `:core:common` | Keep, add `ContentId`/`LocalId` from day one. |
| `:core:designsystem` | `:core:ui` + `:core:design` | Split: design tokens vs UI components (per our architecture). |
| `:core:database` | `:core:storage` | Switch to Room. |
| `:core:preferences` | `:core:preferences` | Keep. |
| `:core:source-api` | `:core:source-api` | Keep if Aniyomi compat is required. |
| `:core:player` | `:core:player` | Keep, MPV wrapper. |
| `:core:anilist` | `:core:anilist` | Keep. |
| `:core:tracker` | `:core:tracker` | Keep. |
| `:core:download` | `:core:download` | Keep. |
| `:core:backup` | `:core:backup` | Keep. |
| `:core:video-resolver` | `:core:video-resolver` | Keep. |
| `:core:episode-metadata` | `:core:episode-metadata` | Keep. |
| `:core:provider-api` | `:core:provider-api` | Keep (pluggable metadata). |
| `:core:update-checker` | `:core:update-checker` | Keep. |
| `:core:ads` | ❓ | Only if user wants ads. |
| `:core:app-update` | `:core:app-update` | Keep. |
| `:data:anime` | `:data:anime` | Keep. |
| `:data:extension` | `:data:extension` | Keep. |
| `:data:history` | `:data:history` | Keep — but actually USE it this time. |
| `:feature:*` | `:feature:*` | Keep all active features, split large files. |

---

## Summary

The old project is **well-architected** but has **code quality issues** (huge files, stale docs, no tests). The core patterns (two-tier identity, pluggable providers, gateway interfaces, single MPV instance) are excellent and should be carried over. The rebuild should focus on:

1. **Splitting large files** (WatchScreen, AnimeDetailsVM, SetupWizard).
2. **Adding tests** from day one.
3. **Using our decided tech stack** (Hilt, Room, Retrofit) — with Koin isolation for extension compat if needed.
4. **Proper navigation** (Voyager or Compose Navigation) — no hand-rolled state machine.
5. **Keeping the good patterns** (ContentId, pluggable registries, reactive everything).
