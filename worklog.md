---
Task ID: LIB-TABS
Agent: full-stack-developer
Task: Add category tabs to LibraryScreen + long-press delete/rename

Work Log:
- Read the existing LibraryScreen.kt (1252 lines) and the LibraryViewModel.kt to confirm the exact API surface (categories / selectedCategoryId / categoryToManage StateFlows + selectCategory / showCategoryManagement / dismissCategoryManagement / deleteCategory / renameCategory / createCategory methods).
- Cross-checked the LibraryCategory data class in core/content/ContentModels.kt — confirmed fields (id, name, displayOrder, isPermanent, createdAt).
- Cross-checked existing usage of `combinedClickable`, `AlertDialog`, `OutlinedTextField`, and `TextButton` in CategoryPickerSheet.kt / DetailsScreen.kt / ExtensionsSettingsScreen.kt to keep style consistent (RobotoFamily, RoundedCornerShape(20) for AlertDialogs, MaterialTheme.colorScheme.surface containerColor).
- Added imports to LibraryScreen.kt: `combinedClickable`, `LazyRow`, `AlertDialog`, `OutlinedTextField`, `TextButton`, `Icons.Filled.Add`, `Icons.Filled.Delete`, `Icons.Filled.Edit`, `TextStyle`, and `LibraryCategory`.
- Collected the three new StateFlows (`categories`, `selectedCategoryId`, `categoryToManage`) inside the `LibraryScreen` composable and added a local `showCreateCategoryDialog` flag for the "+" pill dialog.
- Inserted a `CategoryTabsRow` call between the animated SearchField block and the content Box, gated by `categories.size >= 2` so a single Default-only library shows no tabs. The long-press handler explicitly skips permanent categories (Default) — they get no management dialog at all.
- Inserted `CategoryManagementDialog` (driven by `categoryToManage`) and `CreateCategoryDialog` (driven by local flag) at the bottom of the root Box, alongside `CustomizeSheet`. They forward to `viewModel::renameCategory`, `viewModel::deleteCategory`, `viewModel::dismissCategoryManagement`, and `viewModel::createCategory`.
- Implemented the new private composables below `LibraryScreen`:
    * `CategoryTabsRow` — LazyRow with horizontal padding 16dp, 8dp spacing; renders "All" pill + one pill per category (keyed by `category.id`) + trailing "Add" pill.
    * `CategoryPill` — rounded Surface, primary bg when selected, surfaceVariant otherwise; uses `combinedClickable` with `onLongClick: (() -> Unit)?` so "All" passes null. Reuses the existing press-scale animation pattern (animateFloatAsState 0.94 → 1, FastOutSlowInEasing, Motion.DurationShort).
    * `AddCategoryPill` — trailing "+" pill with surfaceVariant 0.5 alpha, clickable only.
    * `CategoryManagementDialog` — single AlertDialog that internally switches between MENU / RENAME / DELETE_CONFIRM modes via a local `ManageMode` enum + `remember(mode)`-keyed `renameText` (pre-populated with the current name when entering RENAME mode). MENU shows two `ManagementOptionRow`s (Rename with Edit icon, Delete with Delete icon + error tint). RENAME shows an OutlinedTextField + Save. DELETE_CONFIRM shows a buildString warning that adapts to itemCount ("X item(s) in this category will be removed from it.").
    * `ManagementOptionRow` — small Surface row with icon + label, parameterised tint so Delete can be red.
    * `CreateCategoryDialog` — single OutlinedTextField + Create button (disabled while the trimmed name is empty).
- Removed `@OptIn(ExperimentalFoundationApi::class)` and the `ExperimentalFoundationApi` import after verifying that DetailsScreen.kt and ExtensionsSettingsScreen.kt already use `combinedClickable` without the opt-in (stable in this project's Compose version).

Stage Summary:
- LibraryScreen.kt now renders a horizontal scrollable category tabs row (LazyRow) above the grid/list whenever 2+ categories exist. Selected pill has primary bg + onPrimary text; tapping calls `viewModel.selectCategory(id)` ("All" passes null).
- Long-pressing a non-permanent category pill opens a 3-mode management AlertDialog (menu → rename / delete-confirm) that wires into `viewModel.renameCategory` / `viewModel.deleteCategory` / `viewModel.dismissCategoryManagement`. Permanent ("Default") pills do nothing on long-press.
- A trailing "+" pill opens a `CreateCategoryDialog` that wires into `viewModel.createCategory`.
- No other files (including the ViewModel) were modified. The `onNavigateToDetails: (Int) -> Unit` signature is unchanged.
- Did NOT build the project (per task instructions — this is an Android project and the dashboard build is irrelevant). All new imports are confirmed used; existing code style (RobotoFamily, MaterialTheme.colorScheme, Motion token durations, scale-on-press animation) is preserved.

---
Task ID: LIB-REWRITE
Agent: full-stack-developer
Task: Rewrite LibraryScreen to use LibraryEntry + fix category tabs + live reload

Work Log:
- Read worklog.md (prior LIB-TABS agent record) and LibraryEntry.kt to confirm the new data shape: `mainId: String`, `anilistId: Int?`, `sourceId: Long?`, `animeUrl: String?`, `title`, `coverUrl`, `averageScore`, `episodes`, `seasonYear`, `status` + computed `hasAniListId` / `hasExtensionSource`.
- Read LibraryViewModel.kt to confirm the new API surface: `LibraryState.Success(entries: List<LibraryEntry>)` (no longer `anime`), `loadLibrary()`, `categoryCounts: StateFlow<Map<Long, Int>>`, `totalEntries: StateFlow<Int>`, `showCategoryCounts: StateFlow<Boolean>`, `setShowCategoryCounts`, and `deleteCategoryAndMoveToDefault`. Did NOT modify the ViewModel.
- Read the full LibraryScreen.kt (1754 lines) end-to-end to map every usage of `AniListAnime`, `it.id`, `s.anime`, `displayName`, and the existing pill-style tabs before editing.
- Imports: removed `com.confused.anikuta.core.anilist.model.AniListAnime`, `androidx.compose.material.icons.filled.Add`, and `com.confused.anikuta.core.designsystem.component.CollapsingHeader`. Added `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.foundation.layout.RowScope`, and `androidx.compose.foundation.layout.statusBarsPadding`.
- LibraryScreen signature: changed `onNavigateToDetails: (Int) -> Unit` → `onNavigateToDetails: (LibraryEntry) -> Unit`. Added `LaunchedEffect(Unit) { viewModel.loadLibrary() }` at the very top of the composable so the library refreshes whenever the screen becomes visible again (e.g. user bookmarks from the details page then navigates back).
- Collected three new StateFlows: `categoryCounts`, `showCategoryCounts`, `totalEntries`. Removed the local `showCreateCategoryDialog` flag (no more "+" pill).
- Replaced the `CollapsingHeader` call with a new local `LibraryHeader` composable (subtitle-aware). Passes `subtitle = if (showTotalEntries) "$totalEntries in Library" else null` so the total count appears under the title when the toggle is on.
- Category tabs section: rewrote the visibility logic per D-140:
    * `categoriesWithItems = categories.count { (categoryCounts[it.id] ?: 0) > 0 }`
    * `showAllTab = categoriesWithItems >= 2` — only render "All" when 2+ categories are populated.
    * `visibleCategories = categories.filter { if (it.isPermanent) count > 0 else true }` — "Default" hidden when empty; user-created categories always visible.
    * Tabs row rendered iff `visibleCategories.isNotEmpty()`. No "+" pill — categories are created from the details page (long-press bookmark), not here.
- CategoryManagementDialog: added `onDeleteMoveToDefault: () -> Unit` parameter; wired to `viewModel.deleteCategoryAndMoveToDefault(category.id)`. The DELETE_CONFIRM mode now packs two TextButtons into the confirmButton Row — "Delete" (error color, hard-delete) and "Move to Default" (primary color, migrate-then-delete). The warning text explains both options.
- Replaced the bubble/pill-style `CategoryTabsRow` + `CategoryPill` + `AddCategoryPill` trio with a single text-based `CategoryTabsRow` + `CategoryTab` pair:
    * `CategoryTabsRow` — LazyRow, 16dp horizontal padding, 20dp spacing; renders optional "All" + one tab per visible category (keyed by `category.id`). Optional item count "(n)" appended to the label when `showCounts` is true.
    * `CategoryTab` — Column with Text (14sp, RobotoFamily; selected = primary + ExtraBold, unselected = onSurfaceVariant + Medium) + a 20dp × 2dp underline indicator (primary when selected, transparent otherwise). Uses `combinedClickable` so long-press fires management dialog for non-"All" tabs. No background, no scale animation — pure text+underline like the old project.
- Removed `CreateCategoryDialog` composable entirely (no longer reachable since the "+" pill is gone).
- Updated `LibraryGrid`, `LibraryGridCard`, `LibraryList`, `LibraryListRow`:
    * Parameter types changed from `List<AniListAnime>` / `AniListAnime` / `(Int) -> Unit` → `List<LibraryEntry>` / `LibraryEntry` / `(LibraryEntry) -> Unit`.
    * LazyGrid/LazyColumn keys changed from `{ it.id }` → `{ it.mainId }` — fixes the "Key 0 already used" crash when multiple extension-only entries (all anilistId=0) coexist.
    * Display field accessors: `anime.displayName` → `anime.title`. The nullable fields (`coverUrl`, `averageScore`, `seasonYear`, `episodes`, `status`) keep the same names since LibraryEntry exposes them with identical types.
    * Click handlers now pass the whole `LibraryEntry` (`onClick(anime)`) so `MainActivity` can route to AniList-details or Extension-details based on `hasAniListId` / `hasExtensionSource`.
- Updated `LibraryState.Success` references: `s.anime` → `s.entries` in both the grid/list dispatch and the `categoryToManage?.let { ... itemCount = (state as? LibraryState.Success)?.entries?.size ?: 0 }` lookup.
- Added a `LibraryHeader` private composable that mirrors `CollapsingHeader`'s collapsing animation (32sp → 24sp, animated paddingTop/Bottom, statusBarsPadding) but wraps the title in a Column so an optional subtitle (12sp Medium, onSurfaceVariant) renders under it. Layout: Column with weight(1f) + actions() slot, SpaceBetween.
- CustomizeSheet: added `showCategoryCounts: Boolean` and `onShowCategoryCountsChange: (Boolean) -> Unit` parameters; threaded them through to `displayBadgesTab`. Added a new SwitchRow "Show category counts on tabs" right after the existing "Show total entries in header" toggle so the user can flip the count badges on/off. Wired the CustomizeSheet call to `viewModel::setShowCategoryCounts`.
- Updated the top-of-file KDoc to reflect the new LibraryHeader (with subtitle), the category tabs redesign, and the D-140 LibraryEntry migration.

Stage Summary:
- LibraryScreen.kt (1791 lines) now fully consumes `LibraryEntry` instead of `AniListAnime`. `onNavigateToDetails` is `(LibraryEntry) -> Unit` — `MainActivity` will route based on `hasAniListId`/`hasExtensionSource` (separate change). The "Key 0 already used" crash is fixed (LazyGrid keys on `mainId`).
- Live reload: `LaunchedEffect(Unit) { viewModel.loadLibrary() }` at the top of `LibraryScreen` refreshes the library every time the screen enters composition (i.e. on resume / back-navigation).
- Category tabs: redesigned from bubbles/pills to text+underline. "All" only shows when 2+ categories have items; "Default" hidden when empty; no "+" pill. Optional "(n)" count badge per tab when `showCategoryCounts` is on. Long-press still fires the management dialog (permanent categories ignored).
- Long-press management dialog now offers 3 delete options — Cancel / Delete (hard) / Move to Default (migrate). The RENAME flow is unchanged.
- LibraryHeader is a new local composable (the design-system `CollapsingHeader` has no subtitle slot, and the task forbids modifying modules outside the library). It reproduces the collapsing animation and adds the "{n} in Library" subtitle under the title when `showTotalEntries` is on.
- CustomizeSheet gains a "Show category counts on tabs" toggle alongside the existing toggles; the underlying `setShowCategoryCounts` preference was already in the ViewModel.
- No other files modified. Did NOT build the project (per task instructions). All new imports are confirmed used; the removed `AniListAnime` / `Icons.Filled.Add` / `CollapsingHeader` imports have no remaining references (verified with grep). Existing code style (RobotoFamily, MaterialTheme.colorScheme, Motion tokens, scale-on-press animation for non-tab elements) is preserved.
