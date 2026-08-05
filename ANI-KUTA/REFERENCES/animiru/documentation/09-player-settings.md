# 09 — Player Preferences

> All player-related preferences, how they're persisted, and how
> they're applied to MPV (init time vs runtime).

## 1. Persistence mechanism — Injekt + SharedPreferences

Animiru uses **Injekt** for dependency injection and a custom
`PreferenceStore` interface backed by **SharedPreferences**. This is the
Tachiyomi/Mihon heritage — not DataStore.

Each preference file is a Kotlin class with `Preference<T>` properties:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt:9-94
class PlayerPreferences(
    preferenceStore: PreferenceStore,
) {
    val preserveWatchingPosition: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_preserve_watching_position",
        false,
    )
    val switchOnFailure: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_player_switch_on_failure",
        true,
    )
    val progressPreference: Preference<Float> = preferenceStore.getFloat("pref_progress_preference", 0.85F)
    /* ... etc ... */
}
```

The `PreferenceStore` interface (in `:core:common`) provides typed
getters:
- `getBoolean(key, default)`
- `getInt(key, default)`
- `getFloat(key, default)`
- `getString(key, default)`
- `getStringSet(key, default)`
- `getEnum(key, default)` — stores enum by name.

Each `Preference<T>` is a reactive wrapper:
- `.get(): T` — read current value.
- `.set(value: T)` — write.
- `.isSet(): Boolean` — has been explicitly set (vs default).
- `.changes(): Flow<T>` — reactive changes.
- `.deleteAndGet(): T` — reset to default and return the default.

The `Preference` is registered in `PreferenceModule` (DI):

```kotlin
// (not read directly, but pattern is)
class PreferenceModule : InjektModule {
    override fun register() {
        addSingleton("playerPreferences", PlayerPreferences(get()))
        addSingleton("subtitlePreferences", SubtitlePreferences(get()))
        // etc.
    }
}
```

Then injected wherever needed:
```kotlin
private val playerPreferences: PlayerPreferences = Injekt.get()
```

> ANI-KUTA: ANI-KUTA uses Hilt + DataStore. The `PreferenceStore`
> interface is similar to DataStore<Preferences> but older. For
> ANI-KUTA, the same pattern can be implemented with DataStore — each
> `Preference<T>` becomes a `DataStore<Preferences>` entry with a
> `Flow<T>` accessor.

## 2. The six preference classes

Animiru splits player prefs across six classes:

| Class | File | Scope |
|-------|------|-------|
| `PlayerPreferences` | `settings/PlayerPreferences.kt` | General player: orientation, PiP, controls, skip intro, hoster display |
| `AudioPreferences` | `settings/AudioPreferences.kt` | Audio: languages, pitch, channels, volume boost, delay |
| `SubtitlePreferences` | `settings/SubtitlePreferences.kt` | Subtitles: font, size, colors, delay, ASS override |
| `DecoderPreferences` | `settings/DecoderPreferences.kt` | Decoder: hwdec, gpu-next, debanding, video filters |
| `GesturePreferences` | `settings/GesturePreferences.kt` | Gestures: double-tap, drag, media keys |
| `AdvancedPlayerPreferences` | `settings/AdvancedPlayerPreferences.kt` | Advanced: mpv.conf, input.conf, user files, stats page |

## 3. `PlayerPreferences` — full listing

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/PlayerPreferences.kt:9-94
class PlayerPreferences(
    preferenceStore: PreferenceStore,
) {
    val preserveWatchingPosition: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_preserve_watching_position",
        false,
    )
    val switchOnFailure: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_player_switch_on_failure",
        true,
    )
    val progressPreference: Preference<Float> = preferenceStore.getFloat("pref_progress_preference", 0.85F)
    val defaultPlayerOrientationType: Preference<PlayerOrientation> = preferenceStore.getEnum(
        "pref_default_player_orientation_type_key",
        PlayerOrientation.SensorLandscape,
    )

    // Controls

    val allowGestures: Preference<Boolean> = preferenceStore.getBoolean("pref_allow_gestures_in_panels", false)
    val showLoadingCircle: Preference<Boolean> = preferenceStore.getBoolean("pref_show_loading", true)
    val showCurrentChapter: Preference<Boolean> = preferenceStore.getBoolean("pref_show_current_chapter", true)
    val rememberPlayerBrightness: Preference<Boolean> = preferenceStore.getBoolean("pref_remember_brightness", false)
    val playerBrightnessValue: Preference<Float> = preferenceStore.getFloat("player_brightness_value", -1.0F)
    val rememberPlayerVolume: Preference<Boolean> = preferenceStore.getBoolean("pref_remember_volume", false)
    val playerVolumeValue: Preference<Int> = preferenceStore.getInt("player_volume_value_v2", -1)

    // Hoster

    val showFailedHosters: Preference<Boolean> = preferenceStore.getBoolean("pref_show_failed_hosters", false)
    val showEmptyHosters: Preference<Boolean> = preferenceStore.getBoolean("pref_show_empty_hosters", false)

    // Display

    val playerFullscreen: Preference<Boolean> = preferenceStore.getBoolean("player_fullscreen", true)
    val hideControls: Preference<Boolean> = preferenceStore.getBoolean("player_hide_controls", false)
    val displayVolPer: Preference<Boolean> = preferenceStore.getBoolean("pref_display_vol_as_per", true)
    val showSystemStatusBar: Preference<Boolean> = preferenceStore.getBoolean("pref_show_system_status_bar", false)
    val reduceMotion: Preference<Boolean> = preferenceStore.getBoolean("pref_reduce_motion", false)
    val playerTimeToDisappear: Preference<Int> = preferenceStore.getInt("pref_player_time_to_disappear", 4000)
    val panelOpacity: Preference<Int> = preferenceStore.getInt("pref_panel_opacity", 60)

    // Skip intro button

    val enableSkipIntro: Preference<Boolean> = preferenceStore.getBoolean("pref_enable_skip_intro", true)
    val autoSkipIntro: Preference<Boolean> = preferenceStore.getBoolean("pref_enable_auto_skip_ani_skip", false)
    val enableNetflixStyleIntroSkip: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_enable_netflixStyle_aniskip",
        false,
    )
    val waitingTimeIntroSkip: Preference<Int> = preferenceStore.getInt("pref_waiting_time_aniskip", 5)
    val aniSkipEnabled: Preference<Boolean> = preferenceStore.getBoolean("pref_enable_ani_skip", false)
    val disableAniSkipOnChapters: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_disabled_ani_skip_chapters",
        true,
    )

    // PiP

    val enablePip: Preference<Boolean> = preferenceStore.getBoolean("pref_enable_pip", true)
    val pipEpisodeToasts: Preference<Boolean> = preferenceStore.getBoolean("pref_pip_episode_toasts", true)
    val pipOnExit: Preference<Boolean> = preferenceStore.getBoolean("pref_pip_on_exit", false)
    val pipReplaceWithPrevious: Preference<Boolean> = preferenceStore.getBoolean("pip_replace_with_previous", false)

    // External player

    val alwaysUseExternalPlayer: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_always_use_external_player",
        false,
    )
    val externalPlayerPreference: Preference<String> = preferenceStore.getString("external_player_preference", "")

    // Non-preferences

    val playerSpeed: Preference<Float> = preferenceStore.getFloat("pref_player_speed", 1f)
    val speedPresets: Preference<Set<String>> = preferenceStore.getStringSet(
        "default_speed_presets",
        setOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0", "3.5", "4.0"),
    )
    val invertDuration: Preference<Boolean> = preferenceStore.getBoolean("invert_duration", false)
    val aspectState: Preference<VideoAspect> = preferenceStore.getEnum("pref_player_aspect_state", VideoAspect.Fit)

    // Old

    val autoplayEnabled: Preference<Boolean> = preferenceStore.getBoolean("pref_auto_play_enabled", false)
}
```

### Defaults worth noting

- `progressPreference = 0.85F` — episode is marked "seen" at 85% playback.
- `defaultPlayerOrientationType = SensorLandscape` — default orientation.
- `playerTimeToDisappear = 4000` — controls auto-hide after 4s.
- `panelOpacity = 60` — right-side panels are 60% opaque.
- `waitingTimeIntroSkip = 5` — Netflix-style skip waits 5 seconds.
- `playerBrightnessValue = -1.0F` — sentinel for "use system brightness".
- `playerVolumeValue = -1` — sentinel for "use system volume".
- `speedPresets` — 12 default presets from 0.25× to 4.0×.

### The "Non-preferences" section

The `playerSpeed`, `speedPresets`, `invertDuration`, `aspectState`, and
`autoplayEnabled` are grouped as "Non-preferences" because they're
**runtime state that happens to be persisted**, not user-configured
settings. For example:
- `playerSpeed` changes when the user picks a speed in the PlaybackSpeedSheet.
- `aspectState` changes when the user cycles aspect ratio.
- `invertDuration` toggles when the user taps the duration timer.

These don't have a settings screen entry — they're driven by in-player
UI.

## 4. `AudioPreferences`

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AudioPreferences.kt:9-28
class AudioPreferences(
    preferenceStore: PreferenceStore,
) {
    val preferredAudioLanguages: Preference<String> = preferenceStore.getString("pref_audio_lang", "")
    val enablePitchCorrection: Preference<Boolean> = preferenceStore.getBoolean("pref_audio_pitch_correction", true)
    val audioChannels: Preference<AudioChannels> = preferenceStore.getEnum("pref_audio_config", AudioChannels.AutoSafe)
    val volumeBoostCap: Preference<Int> = preferenceStore.getInt("pref_audio_volume_boost_cap", 30)

    // Non-preferences

    val audioDelay: Preference<Int> = preferenceStore.getInt("pref_audio_delay", 0)
}

enum class AudioChannels(val titleRes: StringResource, val property: String, val value: String) {
    Auto(AYMR.strings.pref_player_audio_channels_auto, "audio-channels", "auto-safe"),
    AutoSafe(AYMR.strings.pref_player_audio_channels_auto_safe, "audio-channels", "auto"),
    Mono(AYMR.strings.pref_player_audio_channels_mono, "audio-channels", "mono"),
    Stereo(AYMR.strings.pref_player_audio_channels_stereo, "audio-channels", "stereo"),
    ReverseStereo(AYMR.strings.pref_player_audio_channels_reverse_stereo, "af", "pan=[stereo|c0=c1|c1=c0]"),
}
```

`AudioChannels` is interesting — most values set `audio-channels`, but
`ReverseStereo` sets `af` (audio filter) instead, with a `pan` filter
that swaps left/right channels. The `property` field tells the VM which
MPV property to set.

## 5. `SubtitlePreferences`

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/SubtitlePreferences.kt:18-68
class SubtitlePreferences(
    preferenceStore: PreferenceStore,
) {
    val preferredSubLanguages: Preference<String> = preferenceStore.getString("pref_subtitle_lang", "")
    val subtitleWhitelist: Preference<String> = preferenceStore.getString("pref_subtitle_whitelist", "")
    val subtitleBlacklist: Preference<String> = preferenceStore.getString("pref_subtitle_blacklist", "")
    val subtitleBlackBars: Preference<Boolean> = preferenceStore.getBoolean("pref_subtitle_black_bars", false)
    val subtitleSystemFonts: Preference<Boolean> = preferenceStore.getBoolean("pref_subtitle_system_fonts", false)

    // Non-preferences

    val screenshotSubtitles: Preference<Boolean> = preferenceStore.getBoolean("pref_screenshot_subtitles", false)

    val subtitleFont: Preference<String> = preferenceStore.getString("pref_subtitle_font", "Sans Serif")
    val subtitleFontSize: Preference<Int> = preferenceStore.getInt("pref_subtitles_font_size", 55)
    val subtitleFontScale: Preference<Float> = preferenceStore.getFloat("pref_sub_scale", 1f)
    val subtitleBorderSize: Preference<Int> = preferenceStore.getInt("pref_sub_border_size", 3)
    val boldSubtitles: Preference<Boolean> = preferenceStore.getBoolean("pref_bold_subtitles", false)
    val italicSubtitles: Preference<Boolean> = preferenceStore.getBoolean("pref_italic_subtitles", false)

    val textColorSubtitles: Preference<Int> = preferenceStore.getInt("pref_text_color_subtitles", Color.White.toArgb())

    val borderColorSubtitles: Preference<Int> = preferenceStore.getInt(
        "pref_border_color_subtitles",
        Color.Black.toArgb(),
    )
    val borderStyleSubtitles: Preference<SubtitlesBorderStyle> = preferenceStore.getEnum(
        "pref_border_style_subtitles",
        SubtitlesBorderStyle.OutlineAndShadow,
    )
    val shadowOffsetSubtitles: Preference<Int> = preferenceStore.getInt("sub_shadow_offset", 0)
    val backgroundColorSubtitles: Preference<Int> = preferenceStore.getInt(
        "pref_background_color_subtitles",
        Color.Transparent.toArgb(),
    )

    val subtitleJustification: Preference<SubtitleJustification> = preferenceStore.getEnum(
        "pref_sub_justify",
        SubtitleJustification.Auto,
    )
    val subtitlePos: Preference<Int> = preferenceStore.getInt("pref_sub_pos", 100)

    val overrideSubsASS: Preference<SubtitleAssOverride> = preferenceStore.getEnum(
        "pref_override_subtitles_ass_enum",
        SubtitleAssOverride.No,
    )

    val subtitlesDelay: Preference<Int> = preferenceStore.getInt("pref_subtitles_delay", 0)
    val subtitlesSpeed: Preference<Float> = preferenceStore.getFloat("pref_subtitles_speed", 1f)
    val subtitlesSecondaryDelay: Preference<Int> = preferenceStore.getInt("pref_subtitles_secondary_delay", 0)
}
```

Colors are stored as `Int` (ARGB). The `Color.White.toArgb()` and
`Color.Black.toArgb()` calls produce `0xFFFFFFFF` and `0xFF000000`
respectively.

The `subtitlePos = 100` default means subtitles render at the bottom of
the screen. Setting to 0 puts them at the top.

## 6. `DecoderPreferences`

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/DecoderPreferences.kt:8-28
class DecoderPreferences(
    preferenceStore: PreferenceStore,
) {
    val tryHWDecoding: Preference<Boolean> = preferenceStore.getBoolean("pref_try_hwdec", true)
    val gpuNext: Preference<Boolean> = preferenceStore.getBoolean("pref_gpu_next", false)
    val useYUV420P: Preference<Boolean> = preferenceStore.getBoolean("use_yuv420p", true)

    val debanding: Preference<Debanding> = preferenceStore.getEnum("pref_video_debanding", Debanding.None)
    val debandIterations: Preference<Int> = preferenceStore.getInt("deband_iterations", 1)
    val debandThreshold: Preference<Int> = preferenceStore.getInt("deband_threshold", 48)
    val debandRange: Preference<Int> = preferenceStore.getInt("deband_range", 16)
    val debandGrain: Preference<Int> = preferenceStore.getInt("deband_grain", 32)

    // Non-preferences

    val brightnessFilter: Preference<Int> = preferenceStore.getInt("pref_player_filter_brightness")
    val saturationFilter: Preference<Int> = preferenceStore.getInt("pref_player_filter_saturation")
    val contrastFilter: Preference<Int> = preferenceStore.getInt("pref_player_filter_contrast")
    val gammaFilter: Preference<Int> = preferenceStore.getInt("pref_player_filter_gamma")
    val hueFilter: Preference<Int> = preferenceStore.getInt("pref_player_filter_hue")
}
```

The video filter preferences (`brightnessFilter`, etc.) have no default
value — they default to mpv's defaults (0 for each). The
`PreferenceStore.getInt(key)` without a default returns 0 if unset.

## 7. `GesturePreferences`

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/GesturePreferences.kt:8-55
class GesturePreferences(
    preferenceStore: PreferenceStore,
) {
    // Sliders
    val gestureVolumeBrightness: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_gesture_volume_brightness",
        true,
    )
    val swapVolumeBrightness: Preference<Boolean> = preferenceStore.getBoolean("pref_swap_volume_and_brightness", false)

    // Seeking

    val gestureHorizontalSeek: Preference<Boolean> = preferenceStore.getBoolean("pref_gesture_horizontal_seek", true)
    val showSeekBar: Preference<Boolean> = preferenceStore.getBoolean("pref_show_seekbar", false)
    val defaultIntroLength: Preference<Int> = preferenceStore.getInt("pref_default_intro_length", 85)
    val skipLengthPreference: Preference<Int> = preferenceStore.getInt("pref_skip_length_preference", 10)
    val playerSmoothSeek: Preference<Boolean> = preferenceStore.getBoolean("pref_player_smooth_seek", false)

    // Double tap

    val leftDoubleTapGesture: Preference<SingleActionGesture> = preferenceStore.getEnum(
        "pref_left_double_tap",
        SingleActionGesture.Seek,
    )
    val centerDoubleTapGesture: Preference<SingleActionGesture> = preferenceStore.getEnum(
        "pref_center_double_tap",
        SingleActionGesture.PlayPause,
    )
    val rightDoubleTapGesture: Preference<SingleActionGesture> = preferenceStore.getEnum(
        "pref_right_double_tap",
        SingleActionGesture.Seek,
    )

    // Media controls

    val mediaPreviousGesture: Preference<SingleActionGesture> = preferenceStore.getEnum(
        "pref_media_previous",
        SingleActionGesture.Switch,
    )
    val mediaPlayPauseGesture: Preference<SingleActionGesture> = preferenceStore.getEnum(
        "pref_media_playpause",
        SingleActionGesture.PlayPause,
    )
    val mediaNextGesture: Preference<SingleActionGesture> = preferenceStore.getEnum(
        "pref_media_next",
        SingleActionGesture.Switch,
    )
}
```

Defaults:
- `defaultIntroLength = 85` seconds — AniSkip's default intro length.
- `skipLengthPreference = 10` seconds — double-tap seek duration.
- All gestures default to sensible values (left/right = seek, center =
  play/pause, media = switch episode).

## 8. `AdvancedPlayerPreferences`

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/settings/AdvancedPlayerPreferences.kt:6-16
class AdvancedPlayerPreferences(
    preferenceStore: PreferenceStore,
) {
    val mpvUserFiles: Preference<Boolean> = preferenceStore.getBoolean("mpv_scripts", false)
    val mpvConf: Preference<String> = preferenceStore.getString("pref_mpv_conf", "")
    val mpvInput: Preference<String> = preferenceStore.getString("pref_mpv_input", "")

    // Non-preference

    val playerStatisticsPage: Preference<Int> = preferenceStore.getInt("pref_player_statistics_page", 0)
}
```

Three fields:
- `mpvUserFiles` — whether to copy user-provided scripts/shaders/fonts
  from SAF storage to `filesDir/mpv/`.
- `mpvConf` — the user's custom `mpv.conf` content (stored as a string).
- `mpvInput` — the user's custom `input.conf` content.

The `mpvConf` and `mpvInput` are written to `filesDir/mpv/mpv.conf` and
`input.conf` by `MPVPlayer.init` (see `03-mpv-initialization.md §1`).

## 9. Init-time vs runtime application

### Init-time (applied once when MPVPlayer is created)

These options are set via `mpv.setOptionString(name, value)` in
`MPVPlayer.init`. They affect the next `loadfile` and can't be changed
mid-playback (mpv ignores `setPropertyString` for these).

| Option | Source preference |
|--------|-------------------|
| `vo` | `decoderPreferences.gpuNext` (gpu vs gpu-next) |
| `profile` | Hardcoded `"fast"` (skipped if in mpv.conf) |
| `hwdec` | `decoderPreferences.tryHWDecoding` |
| `vf` (yuv420p) | `decoderPreferences.useYUV420P` |
| `msg-level` | `networkPreferences.verboseLogging` |
| `idle` | Hardcoded `"yes"` |
| `ytdl` | Hardcoded `"no"` |
| `tls-verify` | Hardcoded `"yes"` (skipped if in mpv.conf) |
| `tls-ca-file` | Hardcoded path (skipped if in mpv.conf) |
| `sid` | Hardcoded `"no"` (VM handles selection) |
| `aid` | Hardcoded `"no"` (VM handles selection) |
| `demuxer-max-bytes` | 64 or 32 MiB based on Android version |
| `demuxer-max-back-bytes` | Same |
| `screenshot-directory` | `Pictures/` |
| `brightness`/`saturation`/`contrast`/`gamma`/`hue` | `decoderPreferences.*Filter` |
| `speed` | `playerPreferences.playerSpeed` |
| `vd-lavc-film-grain` | Hardcoded `"cpu"` (mpv bug workaround) |
| `vf` (deband) OR `deband` | `decoderPreferences.debanding` |
| `alang` | `audioPreferences.preferredAudioLanguages` |
| `audio-delay` | `audioPreferences.audioDelay` |
| `audio-pitch-correction` | `audioPreferences.enablePitchCorrection` |
| `volume-max` | `audioPreferences.volumeBoostCap + 100` |
| `audio-channels` / `af` | `audioPreferences.audioChannels` |
| `sub-delay` | `subtitlePreferences.subtitlesDelay` |
| `sub-speed` | `subtitlePreferences.subtitlesSpeed` |
| `secondary-sub-delay` | `subtitlePreferences.subtitlesSecondaryDelay` |
| `sub-font` | `subtitlePreferences.subtitleFont` |
| `sub-ass-override` | `subtitlePreferences.overrideSubsASS` |
| `sub-ass-justify` | Hardcoded `"yes"` if override != No |
| `sub-font-size` | `subtitlePreferences.subtitleFontSize` |
| `sub-bold` | `subtitlePreferences.boldSubtitles` |
| `sub-italic` | `subtitlePreferences.italicSubtitles` |
| `sub-justify` | `subtitlePreferences.subtitleJustification` |
| `sub-color` | `subtitlePreferences.textColorSubtitles` |
| `sub-back-color` | `subtitlePreferences.backgroundColorSubtitles` |
| `sub-outline-color` | `subtitlePreferences.borderColorSubtitles` |
| `sub-outline-size` | `subtitlePreferences.subtitleBorderSize` |
| `sub-border-style` | `subtitlePreferences.borderStyleSubtitles` |
| `sub-shadow-offset` | `subtitlePreferences.shadowOffsetSubtitles` |
| `sub-pos` | `subtitlePreferences.subtitlePos` |
| `sub-scale` | `subtitlePreferences.subtitleFontScale` |
| `sub-ass-force-margins` | `subtitlePreferences.subtitleBlackBars` |
| `sub-use-margins` | `subtitlePreferences.subtitleBlackBars` |
| `http-header-fields` | Per-video (set before each `loadfile`) |
| `start` | Per-video (set before each `loadfile`) |

### Runtime (applied via `setPropertyX` during playback)

These can be changed mid-playback. The SubtitleSettingsPanel, panels,
and sheets change these.

| Property | Changed by |
|----------|-----------|
| `speed` | PlaybackSpeedSheet |
| `pause` | play/pause button, sleep timer |
| `volume` | volume slider, gesture |
| `chapter` | ChaptersSheet |
| `sid` / `secondary-sid` | SubtitleTracksSheet |
| `aid` | AudioTracksSheet |
| `hwdec` | MoreSheet (decoder chips) |
| `audio-channels` / `af` | MoreSheet (audio channels chips) |
| `audio-pitch-correction` | PlaybackSpeedSheet |
| `sub-bold` | SubtitleSettingsPanel |
| `sub-italic` | SubtitleSettingsPanel |
| `sub-justify` | SubtitleSettingsPanel |
| `sub-font` | SubtitleSettingsPanel |
| `sub-font-size` | SubtitleSettingsPanel |
| `sub-border-style` | SubtitleSettingsPanel |
| `sub-outline-size` | SubtitleSettingsPanel |
| `sub-shadow-offset` | SubtitleSettingsPanel |
| `sub-color` | SubtitleSettingsPanel (color picker) |
| `sub-outline-color` | SubtitleSettingsPanel (color picker) |
| `sub-back-color` | SubtitleSettingsPanel (color picker) |
| `sub-scale` | SubtitleSettingsPanel |
| `sub-pos` | SubtitleSettingsPanel |
| `sub-delay` | SubtitleDelayPanel |
| `secondary-sub-delay` | SubtitleDelayPanel |
| `sub-speed` | SubtitleDelayPanel |
| `audio-delay` | AudioDelayPanel |
| `deband` | VideoSettingsPanel (deband toggle) |
| `brightness`/`saturation`/`contrast`/`gamma`/`hue` | VideoSettingsPanel (filter sliders) |
| `panscan` / `video-aspect-override` | Aspect ratio button |
| `android-surface-size` | MpvSurface (on surfaceChanged) |
| `force-window` | MpvSurface (on surfaceCreated/Destroyed) |
| `vid` | MpvSurface (on surfaceCreated/Destroyed) |

### Mixed (some options can be set either way)

- `sub-ass-override` — set at init, but the panel also calls
  `setPropertyString("sub-ass-override", it.value)`. The runtime change
  only takes effect on the **next file load** (mpv limitation).
- `hwdec` — set at init, but MoreSheet also calls
  `setPropertyString("hwdec", it.value)`. Mid-playback hwdec switch is
  supported by mpv (with caveats).

## 10. Per-video preferences (not in preference files)

Some state is per-video, not per-session:
- `http-header-fields` — set from `Video.headers` before each `loadfile`.
- `start` — set from episode's `last_second_seen` before each `loadfile`.
- `force-media-title` — set after file load to show anime + episode name.
- `user-data/current-anime/*` — written for Lua bridge + AniSkip.

These aren't persisted to SharedPreferences; they're recomputed per
video.

## 11. The `PlayerSettingsScreen` — settings UI

The settings UI lives in
`app/src/main/java/eu/kanade/tachiyomi/ui/setting/PlayerSettingsScreen.kt`
(not read in detail for this doc). It's a Voyager screen that uses the
standard `SettingsScreen` infrastructure to render preference items
(toggles, sliders, dropdowns) bound to the `Preference<T>` objects.

> ANI-KUTA: ANI-KUTA's settings UI should mirror this structure — one
> screen per preference class, with sections matching the comments in
> each preference file (e.g. "Controls", "Display", "PiP", "Skip intro").

## 12. Quirks + warnings

1. **`playerVolumeValue_v2`** — the `_v2` suffix indicates a migration.
   The old key (`player_volume_value`) was an `Int` with different
   semantics. There's a migration in
   `app/src/main/java/mihon/core/migration/migrations/` that handles
   this. ANI-KUTA starting fresh can use a clean key name.

2. **`subtitleFont = "Sans Serif"` (string)** — not a file path, but a
   fontconfig family name. The fontconfig aliases in `fonts.conf` map
   `"Sans Serif"` → `Roboto`. If the user picks a font that doesn't
   exist, mpv falls back to its compiled-in default.

3. **`rememberPlayerBrightness` / `rememberPlayerVolume`** — when on,
   the last brightness/volume is persisted across sessions. The
   `playerBrightnessValue = -1.0F` and `playerVolumeValue = -1` are
   sentinels for "not yet set" (use system value).

4. **`panelOpacity = 60`** — percentage, applied as
   `surface.copy(panelOpacity / 100f)`. The Compose Card's container
   color is the theme surface color blended with this opacity.

5. **`playerTimeToDisappear = 4000`** — milliseconds, not seconds.
   Easy to misread.

6. **`progressPreference = 0.85F`** — float in `[0, 1]`. At 85%
   playback, the episode is marked "seen". Setting to `1.0` requires
   watching the entire episode.

7. **`speedPresets` is a `Set<String>`** — strings, not floats. This
   is because `PreferenceStore.getStringSet` is the only set type.
   The VM converts: `speedPresets.map { it.toFloat() }.sorted()`.

8. **`aspectState` is a non-preference** — it's runtime state (cycles
   Fit → Stretch → Crop), but persisted so it survives across sessions.
   No settings UI for it.

9. **`invertDuration` is a non-preference** — toggled by tapping the
   duration timer in the seekbar. Persisted but no settings UI.

10. **`playerSpeed` is a non-preference** — set by the PlaybackSpeedSheet's
    "Make default" button. Persisted but no settings UI for direct edit.

11. **No migration for new prefs** — when a new preference is added,
    its default value is returned by `PreferenceStore.get*()` if the
    key doesn't exist. No explicit migration needed. But if a
    preference's **key** or **type** changes, a migration class in
    `mihon/core/migration/migrations/` is required (see
    `VideoPlayerPreferenceMigration.kt`, `MovePlayerPreferencesMigration.kt`,
    etc.).

12. **`overrideSubsASS` default is `No`** — this means ASS subtitles
    are rendered with their original styling by default. Most users
    probably want `Scale` or `Force` to apply their font/size prefs to
    ASS subs, but the default preserves the original look.

13. **`tryHWDecoding = true`** — default on. If a video fails to play
    with hwdec, the user can turn this off to force software decoding.
    The `switchOnFailure = true` default also helps — if hwdec fails,
    Animiru tries the next-best video.

14. **`useYUV420P = true`** — default on. This forces the video filter
    `vf=format=yuv420p`, which some devices need for hwdec to work
    with 10-bit content. It can cause quality loss on other content.
