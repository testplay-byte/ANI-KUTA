package com.confused.anikuta.feature.extensionssettings

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.forEach
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.feature.extensionssettings.preference.SharedPreferencesDataStore
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Phase 4: Source Preferences screen.
 *
 * Renders the extension's own settings via [PreferenceFragmentCompat] embedded
 * in Compose via [AndroidView] + [FragmentContainerView].
 *
 * Ported from animiru's SourcePreferencesScreen + SourcePreferencesFragment.
 * Adaptations: Voyager Screen → Composable function; Injekt → Koin;
 * SourceManager → ExtensionManager; removed incognito IME helper.
 */
@Composable
fun SourcePreferencesScreen(
    sourceId: Long,
    onBack: () -> Unit,
    extensionManager: ExtensionManager = koinInject(),
) {
    val source = remember(sourceId) {
        extensionManager.getSource(sourceId)
    }
    val sourceName = source?.name ?: "Source Preferences"

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = sourceName,
                collapsed = false,
                actions = { BackAction(onBack) },
            )

            if (source is ConfigurableAnimeSource) {
                FragmentContainer(
                    sourceId = sourceId,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
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

/**
 * Compose wrapper for [FragmentContainerView] + [SourcePreferencesFragment].
 * From https://stackoverflow.com/questions/60520145/fragment-container-in-jetpack-compose/70817794#70817794
 */
@Composable
private fun FragmentContainer(
    sourceId: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fragmentManager = (context as androidx.fragment.app.FragmentActivity).supportFragmentManager

    val containerId by rememberSaveable {
        mutableIntStateOf(View.generateViewId())
    }
    var initialized by rememberSaveable { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FragmentContainerView(ctx).apply { id = containerId }
        },
        update = { view ->
            if (!initialized) {
                fragmentManager.commit {
                    add(view.id, SourcePreferencesFragment.getInstance(sourceId), null)
                }
                initialized = true
            }
        },
    )
}

/**
 * The [PreferenceFragmentCompat] that calls [ConfigurableAnimeSource.setupPreferenceScreen]
 * to populate the extension's preferences.
 */
class SourcePreferencesFragment : PreferenceFragmentCompat(), org.koin.core.component.KoinComponent {

    private val extensionManager: ExtensionManager by inject()

    override fun getContext(): Context? {
        val superCtx = super.getContext() ?: return null
        // Use the AndroidX preference theme overlay (avoids needing preferenceTheme in the activity theme).
        return ContextThemeWrapper(superCtx, androidx.preference.R.style.PreferenceThemeOverlay)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = populateScreen()
    }

    private fun populateScreen(): PreferenceScreen {
        val sourceId = requireArguments().getLong(SOURCE_ID)
        val source = extensionManager.getSource(sourceId)
        val sourceScreen = preferenceManager.createPreferenceScreen(requireContext())

        if (source is ConfigurableAnimeSource) {
            val dataStore = SharedPreferencesDataStore(source.sourcePreferences())
            preferenceManager.preferenceDataStore = dataStore

            source.setupPreferenceScreen(sourceScreen)
            sourceScreen.forEach { pref ->
                pref.isIconSpaceReserved = false
                pref.isSingleLineTitle = false
                if (pref is DialogPreference && pref.dialogTitle.isNullOrEmpty()) {
                    pref.dialogTitle = pref.title
                }
            }
        }

        return sourceScreen
    }

    companion object {
        private const val SOURCE_ID = "source_id"

        fun getInstance(sourceId: Long): SourcePreferencesFragment {
            return SourcePreferencesFragment().apply {
                arguments = Bundle().apply {
                    putLong(SOURCE_ID, sourceId)
                }
            }
        }
    }
}

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
