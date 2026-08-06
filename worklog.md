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

---
Task ID: LIB-FIXES
Agent: full-stack-developer
Task: Library UI fixes + multi-select mode

Work Log:
- Read worklog.md (prior LIB-TABS + LIB-REWRITE records) to understand the existing structure of LibraryScreen.kt (1790 lines) — text+underline category tabs, LibraryHeader with subtitle slot, CategoryManagementDialog with 3-mode MENU/RENAME/DELETE_CONFIRM flow, grid/list views keyed by mainId.
- Read the updated LibraryViewModel.kt (694 lines) end-to-end to confirm the D-141 API surface: isSelectionMode / selectedMainIds / showMultiSelectCategorySheet / showDeleteConfirmation StateFlows + enterSelectionMode / toggleSelection / selectAll / clearSelection / invertSelection / exitSelectionMode / showMultiSelectCategorySheet / dismissMultiSelectCategorySheet / addSelectedToCategory / removeSelectedFromCategory / showDeleteConfirmation / dismissDeleteConfirmation / deleteSelected / getCategoriesForSelected methods. Also confirmed selectCategory now uses reloadFromCache (no network on tab switches) and loadLibrary is still the fresh-data path.
- Read CategoryPickerSheet.kt on the details page to mirror its AlertDialog style (surface bg, RoundedCornerShape(20), LazyColumn of checkbox rows with primary check icon on primaryContainer bg) for the new MultiSelectCategoryPicker.
- Imports: added `androidx.compose.foundation.border` (for the selected-card border overlay). All other symbols used by the new code (Icons.Filled.Check, CircleShape, AlertDialog, Surface, TextButton, HorizontalDivider, LazyColumn, items, heightIn, etc.) were already imported. `clickable` is still used (HeaderActionButton, ManagementOptionRow, CustomizeSheet tab strip, Move-to-Default button, MultiSelectCategoryPicker rows); `combinedClickable` now also used in LibraryGridCard + LibraryListRow (in addition to the existing CategoryTab).
- LibraryScreen composable:
    * Collected 4 new StateFlows (isSelectionMode, selectedMainIds, showMultiSelectCategorySheet, showDeleteConfirmation).
    * Added `onEntryClick` + `onEntryLongClick` lambdas that branch on isSelectionMode: in selection mode tap → toggleSelection, long-press → no-op; outside selection mode tap → onNavigateToDetails, long-press → enterSelectionMode(mainId).
    * Header title now computed via `when`: "X selected" in selection mode → "X in Library" when showTotalEntries is on → "Library" otherwise. subtitle is now always null (the count IS the title, no separate "Library" title above it).
    * Added an AnimatedVisibility "quick options" row (Select All / Clear / Invert text buttons, primary color) between the header and the search bar — visible only in selection mode.
    * Wrapped CategoryTabsRow in a Column with a 1dp HorizontalDivider (outlineVariant.copy(alpha = 0.3f)) below it; the whole block is now gated by `!isSelectionMode` so tabs disappear in selection mode (quick options row replaces them).
    * Threaded isSelectionMode + selectedMainIds + onEntryClick + onEntryLongClick through LibraryGrid and LibraryList.
    * CategoryManagementDialog call now passes `itemCount = categoryCounts[category.id] ?: 0` (was `(state as? LibraryState.Success)?.entries?.size ?: 0`) so the count reflects the TRUE category total even when the view is filtered by search or another category.
    * Added 3 new conditional overlays at the bottom of the root Box: SelectionBottomBar (AnimatedVisibility, Alignment.BottomCenter, padding(bottom = 90.dp) so it sits above the floating nav pill), MultiSelectCategoryPicker (gated by showMultiSelectCategorySheet, computes selectedMap via remember(categories, selectedMainIds) { viewModel.getCategoriesForSelected() }), DeleteSelectedDialog (gated by showDeleteConfirmation, count = selectedMainIds.size).
- CategoryTabsRow label format: changed `"${category.name} ($count)"` → `"[$count] ${category.name}"` so the count appears on the LEFT in square brackets ("[3] Default") when showCategoryCounts is on.
- CategoryManagementDialog DELETE_CONFIRM mode: removed the 2-button Row (Delete + Move to Default) from confirmButton — confirmButton is now just "Delete" (error color); dismissButton stays "Cancel" (onSurfaceVariant, left). Moved "Move to Default" into the text content as a full-width primary-tinted Surface button BELOW the warning text, gated by `if (itemCount > 0)` so an empty category only shows Cancel + Delete. Updated the warning copy to point at the new button position ("Use \"Move to Default\" below to keep them.").
- LibraryGrid + LibraryGridCard:
    * Signature: added `isSelectionMode: Boolean`, `selectedMainIds: Set<String>`, `onClickEntry`, `onLongClickEntry` params; renamed the card's `onClick` to keep it as the per-card callback. Grid bottom contentPadding is now `if (isSelectionMode) 160.dp else 90.dp` to reserve space for the bottom action bar.
    * Card: switched `.clickable` → `.combinedClickable` (onLongClick wired up). Added a `matchParentSize()` Box with `Modifier.border(2.dp, primary, RoundedCornerShape(12.dp))` when isSelected — drawn on top of the cover image + title gradient so the border is actually visible. Added a 22dp circular badge at TopEnd when in selection mode: primary bg + check icon when selected, semi-transparent surface bg (empty circle) when not — so the user can see that tapping will select.
- LibraryList + LibraryListRow:
    * Same signature changes as the grid equivalents.
    * Row: switched `.clickable` → `.combinedClickable`. Added a `Modifier.background(primary.copy(alpha = 0.1f))` (via `.then(if isSelected ... else Modifier)`) when selected for a subtle tinted-bg indicator. Added an 18dp circular check badge at the TopEnd corner of the cover thumbnail (same primary/surface pattern as the grid card, slightly smaller to fit the 56×80 thumbnail).
- Added 3 new private composables at the end of the file:
    * `SelectionBottomBar` — Surface (surface bg, shadowElevation 8dp) + Row(SpaceBetween) with three TextButtons: Cancel (onSurfaceVariant, left) / Category (primary, center) / Delete (error, right). Modifier is passed in so the caller can position it above the nav pill.
    * `MultiSelectCategoryPicker` — AlertDialog mirroring CategoryPickerSheet's style (surface bg, 20dp rounded, "Categories" title, LazyColumn of checkbox rows keyed by category.id, heightIn(max = 300.dp), primaryContainer bg when checked / surfaceVariant 0.3 when unchecked, 22dp checkbox with primary check icon, "Done" confirmButton). onToggle(categoryId, isChecked) — caller decides remove vs add based on isChecked. No "New category" button (creating one here wouldn't auto-add the selected items).
    * `DeleteSelectedDialog` — AlertDialog with title "Delete from library?", text "Delete X entr(y/ies) from library?", confirmButton "Delete" (error), dismissButton "Cancel" (onSurfaceVariant).
- Updated the top-of-file KDoc to reflect the new header title logic (X in Library as MAIN heading), the quick options row, the [3] Default count format, the 1dp divider below tabs, the D-141 multi-select flow (long-press → SelectionBottomBar + pickers), and the reloadFromCache-vs-loadLibrary split.
- Did NOT modify any other files (per task instructions). Did NOT build the project (per task instructions — this is an Android project). All new imports are confirmed used; existing code style (RobotoFamily, MaterialTheme.colorScheme, Motion tokens, scale-on-press animation for cards/rows, AlertDialog shape RoundedCornerShape(20)) is preserved.

Stage Summary:
- LibraryScreen.kt grew from 1790 → 2311 lines. All 6 requested changes are implemented:
  1. Header MAIN heading is now "X in Library" (when showTotalEntries on) or "Library" (when off) — no separate title + subtitle. In selection mode it becomes "X selected".
  2. Category tab count format is "[3] Default" (left, square brackets) when showCategoryCounts is on.
  3. Delete dialog (CategoryManagementDialog DELETE_CONFIRM) now has Cancel (dismissButton, left) + Delete (confirmButton, error, right) + a full-width "Move to Default" button below the warning — only shown when categoryCounts[category.id] > 0. Empty categories get just Cancel + Delete.
  4. A 1dp HorizontalDivider (outlineVariant.copy(alpha = 0.3f)) sits below the category tabs row, separating tabs from the grid.
  5. D-141 multi-select: long-press a card/row → enterSelectionMode; header swaps to "X selected"; quick options row (Select All / Clear / Invert) replaces the tabs; a SelectionBottomBar (Cancel / Category / Delete) fades in at Alignment.BottomCenter (padding 90dp to clear the nav pill); tapping entries toggles selection instead of navigating; selected cards get a 2dp primary border + a checkmark badge in the top-right corner (list rows get a tinted bg + a smaller check badge on the thumbnail). MultiSelectCategoryPicker reuses the CategoryPickerSheet AlertDialog style with per-category checkboxes (tapping checked → removeSelectedFromCategory, unchecked → addSelectedToCategory, initial state from getCategoriesForSelected via remember). DeleteSelectedDialog shows "Delete X entries from library?" with Cancel + Delete.
  6. No UI change for reloadFromCache — LaunchedEffect on resume still calls loadLibrary() (fresh data), tab switches go through selectCategory (reloadFromCache, no network). Added a comment in the LaunchedEffect explaining the split.
- Known limitation: the parent MainActivity still renders the floating AnikutaBottomNavBar on top of LibraryScreen content; SelectionBottomBar is positioned with padding(bottom = 90.dp) so it sits ABOVE the nav pill's reserved area and stays fully visible/usable, but the nav pill is still rendered behind it. Fully hiding the nav pill would require a MainActivity change (out of scope for this task).
