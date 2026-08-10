package com.confused.anikuta.feature.extensionssettings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import org.koin.compose.koinInject

/**
 * Phase 3: Source Preferences screen — Compose-native redesign.
 *
 * Instead of using PreferenceFragmentCompat (which renders Android XML preferences
 * with default styling that doesn't match ANI-KUTA's design language), this screen
 * walks the PreferenceScreen tree after setupPreferenceScreen() populates it, then
 * renders Compose-native equivalents with ANI-KUTA styling: lime accent, warm darks,
 * translucent cards, rounded corners, Roboto font.
 *
 * Supported preference types:
 * - SwitchPreferenceCompat / CheckBoxPreference → Switch row
 * - ListPreference → Clickable row → AlertDialog with radio options
 * - EditTextPreference → Clickable row → AlertDialog with text field
 * - SeekBarPreference → Clickable row → AlertDialog with slider
 * - PreferenceCategory → Section header
 * - Preference (plain) → Clickable row
 */
@Composable
fun SourcePreferencesScreen(
    sourceId: Long,
    onBack: () -> Unit,
    extensionManager: ExtensionManager = koinInject(),
) {
    val source = remember(sourceId) { extensionManager.getSource(sourceId) }
    val sourceName = source?.name ?: "Source Preferences"
    val ctx = LocalContext.current
    val prefs = remember(sourceId) { mutableStateOf<SharedPreferences?>(null) }
    val preferenceScreen = remember(sourceId) { mutableStateOf<PreferenceScreen?>(null) }

    // Build the preference screen once (on first composition).
    androidx.compose.runtime.LaunchedEffect(sourceId) {
        if (source is ConfigurableAnimeSource) {
            val sp = source.sourcePreferences()
            prefs.value = sp
            val pm = androidx.preference.PreferenceManager(ctx)
            pm.preferenceDataStore = com.confused.anikuta.feature.extensionssettings.preference.SharedPreferencesDataStore(sp)
            val screen = pm.createPreferenceScreen(ctx)
            source.setupPreferenceScreen(screen)
            preferenceScreen.value = screen
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = sourceName,
                collapsed = false,
                actions = { BackAction(onBack) },
            )

            val screen = preferenceScreen.value
            val sp = prefs.value
            if (screen != null && sp != null) {
                PreferenceList(screen = screen, sharedPreferences = sp)
            } else if (source is ConfigurableAnimeSource) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "This source has no settings.",
                        fontFamily = RobotoFamily,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        ScrollBlurOverlay(
            scrollOffset = { 0f },
            backgroundColor = MaterialTheme.colorScheme.background,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Preference list renderer — walks the PreferenceScreen tree
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PreferenceList(
    screen: PreferenceScreen,
    sharedPreferences: SharedPreferences,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Walk the preference screen children.
        val count = screen.preferenceCount
        items(count) { index ->
            val pref = screen.getPreference(index)
            PreferenceItemRenderer(pref, sharedPreferences)
        }
    }
}

@Composable
private fun PreferenceItemRenderer(
    pref: Preference,
    sharedPreferences: SharedPreferences,
) {
    when (pref) {
        is PreferenceCategory -> PreferenceCategoryRenderer(pref, sharedPreferences)
        is SwitchPreferenceCompat -> SwitchPreferenceRenderer(pref, sharedPreferences)
        is CheckBoxPreference -> SwitchPreferenceRenderer(pref, sharedPreferences, isCheckBox = true)
        is MultiSelectListPreference -> MultiSelectListPreferenceRenderer(pref, sharedPreferences)
        is ListPreference -> ListPreferenceRenderer(pref, sharedPreferences)
        is EditTextPreference -> EditTextPreferenceRenderer(pref, sharedPreferences)
        is SeekBarPreference -> SeekBarPreferenceRenderer(pref, sharedPreferences)
        else -> PlainPreferenceRenderer(pref)
    }
}

// ── Category (section header) ─────────────────────────────────────────────────

@Composable
private fun PreferenceCategoryRenderer(
    category: PreferenceCategory,
    sharedPreferences: SharedPreferences,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = category.title?.toString() ?: "",
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        // Render children.
        val count = category.preferenceCount
        for (i in 0 until count) {
            val child = category.getPreference(i)
            PreferenceItemRenderer(child, sharedPreferences)
        }
    }
}

// ── Switch / Checkbox ─────────────────────────────────────────────────────────

@Composable
private fun SwitchPreferenceRenderer(
    pref: Preference,
    sharedPreferences: SharedPreferences,
    isCheckBox: Boolean = false,
) {
    val key = pref.key ?: return
    val title = pref.title?.toString() ?: ""
    val summary = pref.summary?.toString()
    // defaultValue is a protected property — use reflection-free approach.
    val defaultBool = when (pref) {
        is SwitchPreferenceCompat -> pref.getSharedPreferences()?.getBoolean(key, false) ?: false
        is CheckBoxPreference -> pref.getSharedPreferences()?.getBoolean(key, false) ?: false
        else -> false
    }

    var checked by remember(key) {
        mutableStateOf(sharedPreferences.getBoolean(key, defaultBool))
    }

    PreferenceCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                checked = !checked
                sharedPreferences.edit().putBoolean(key, checked).apply()
            }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (summary != null) {
                    Text(
                        summary,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    sharedPreferences.edit().putBoolean(key, it).apply()
                },
            )
        }
    }
}

// ── List Preference (dropdown / radio dialog) ─────────────────────────────────

@Composable
private fun ListPreferenceRenderer(
    pref: ListPreference,
    sharedPreferences: SharedPreferences,
) {
    val key = pref.key ?: return
    val title = pref.title?.toString() ?: ""
    val entries = pref.entries ?: emptyArray()
    val entryValues = pref.entryValues ?: emptyArray()
    val defaultStr = pref.getSharedPreferences()?.getString(key, null) ?: ""

    var currentValue by remember(key) {
        mutableStateOf(sharedPreferences.getString(key, defaultStr) ?: defaultStr)
    }
    var showDialog by remember { mutableStateOf(false) }

    val currentEntry = entries.getOrElse(
        entryValues.indexOfFirst { it == currentValue }.takeIf { it >= 0 } ?: 0
    ) { "" }

    PreferenceCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { showDialog = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (currentEntry.isNotEmpty()) {
                    Text(
                        currentEntry.toString(),
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    entries.forEachIndexed { index, entry ->
                        val isSelected = entryValues[index] == currentValue
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.border.BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth().clickable {
                                currentValue = entryValues[index].toString()
                                sharedPreferences.edit().putString(key, currentValue).apply()
                                showDialog = false
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Radio circle indicator.
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent,
                                    border = androidx.compose.foundation.border.BorderStroke(
                                        width = 2.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    if (isSelected) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Surface(
                                                shape = androidx.compose.foundation.shape.CircleShape,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(8.dp),
                                            ) {}
                                        }
                                    }
                                }
                                Spacer(Modifier.size(12.dp))
                                Text(
                                    entry.toString(),
                                    fontFamily = RobotoFamily,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

// ── MultiSelectList Preference (checkbox dialog — multiple selection) ─────────

@Composable
private fun MultiSelectListPreferenceRenderer(
    pref: MultiSelectListPreference,
    sharedPreferences: SharedPreferences,
) {
    val key = pref.key ?: return
    val title = pref.title?.toString() ?: ""
    val entries = pref.entries ?: emptyArray()
    val entryValues = pref.entryValues ?: emptyArray()

    var selectedValues by remember(key) {
        mutableStateOf(
            sharedPreferences.getStringSet(key, emptySet<String>()) ?: emptySet(),
        )
    }
    var showDialog by remember { mutableStateOf(false) }

    val selectedCount = selectedValues.size
    val summaryText = if (selectedCount > 0) {
        entries.filterIndexed { i, _ ->
            entryValues.getOrNull(i)?.toString() in selectedValues
        }.joinToString(", ") { it.toString() }
    } else {
        "None selected"
    }

    PreferenceCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { showDialog = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "$selectedCount selected: $summaryText",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (showDialog) {
        var workingSet by remember { mutableStateOf(selectedValues.toMutableSet()) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                LazyColumn {
                    items(entries.size) { index ->
                        val entry = entries[index]
                        val value = entryValues[index].toString()
                        val isSelected = value in workingSet
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                workingSet = if (isSelected) {
                                    workingSet - value
                                } else {
                                    workingSet + value
                                }.toMutableSet()
                            }.padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Custom checkbox circle.
                            Surface(
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.size(12.dp))
                            Text(
                                entry.toString(),
                                fontFamily = RobotoFamily,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedValues = workingSet.toSet()
                    sharedPreferences.edit().putStringSet(key, selectedValues).apply()
                    showDialog = false
                }) {
                    Text("OK", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

// ── EditText Preference ──────────────────────────────────────────────────────

@Composable
private fun EditTextPreferenceRenderer(
    pref: EditTextPreference,
    sharedPreferences: SharedPreferences,
) {
    val key = pref.key ?: return
    val title = pref.title?.toString() ?: ""
    val defaultStr = pref.getSharedPreferences()?.getString(key, null) ?: ""

    var currentValue by remember(key) {
        mutableStateOf(sharedPreferences.getString(key, defaultStr) ?: defaultStr)
    }
    var showDialog by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(currentValue) }

    PreferenceCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                textValue = currentValue
                showDialog = true
            }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (currentValue.isNotEmpty()) {
                    Text(
                        currentValue,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    currentValue = textValue
                    sharedPreferences.edit().putString(key, currentValue).apply()
                    showDialog = false
                }) {
                    Text("OK", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

// ── SeekBar Preference ───────────────────────────────────────────────────────

@Composable
private fun SeekBarPreferenceRenderer(
    pref: SeekBarPreference,
    sharedPreferences: SharedPreferences,
) {
    val key = pref.key ?: return
    val title = pref.title?.toString() ?: ""
    val max = pref.max
    val min = 0
    val defaultInt = pref.getSharedPreferences()?.getInt(key, 0) ?: 0

    var currentValue by remember(key) {
        mutableStateOf(sharedPreferences.getInt(key, defaultInt))
    }
    var showDialog by remember { mutableStateOf(false) }

    PreferenceCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { showDialog = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "$currentValue",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }

    if (showDialog) {
        var sliderValue by remember { mutableStateOf(currentValue.toFloat()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${sliderValue.toInt()}",
                        fontFamily = RobotoFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = min.toFloat()..max.toFloat(),
                        steps = max - 1,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    currentValue = sliderValue.toInt()
                    sharedPreferences.edit().putInt(key, currentValue).apply()
                    showDialog = false
                }) {
                    Text("OK", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

// ── Plain preference (clickable row, no value) ───────────────────────────────

@Composable
private fun PlainPreferenceRenderer(pref: Preference) {
    val title = pref.title?.toString() ?: ""
    val summary = pref.summary?.toString()

    PreferenceCard {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { /* could trigger pref's OnPreferenceClickListener */ }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (summary != null) {
                    Text(
                        summary,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

// ── Shared card wrapper ──────────────────────────────────────────────────────

@Composable
private fun PreferenceCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

// ── Back action ──────────────────────────────────────────────────────────────

@Composable
private fun BackAction(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
