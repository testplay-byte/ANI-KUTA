package com.confused.anikuta.feature.onboarding

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * D-403 (round 28): NavKey for the onboarding setup wizard.
 *
 * The first-run destination: AppRoot starts here while
 * `AppPreferences.onboardingCompleted` is false (the flag is set when the
 * wizard's Finish step calls [OnboardingScreen]'s `onFinished`). Not a root
 * tab — the bottom nav + the update sheet never render on it.
 */
@Serializable
object OnboardingKey : NavKey
