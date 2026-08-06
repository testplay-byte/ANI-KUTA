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
