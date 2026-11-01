package com.confused.anikuta.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-403 (round 28): the wizard step machine's contract — the step ORDER
 * (the UI's stepIndex arithmetic depends on it) and the permission-step
 * classification (the skip affordance + the finish summary's grouping key
 * off it).
 *
 * ROUND 135: RESTORE joins between STORAGE and NOTIFICATIONS (the backup
 * can be brought back right after the storage folder is verified) — the
 * wizard is now 8 steps (welcome + 7). The removed wizardIndex ordinal
 * (nothing consumed it — the screens pass explicit stepNumbers) stays
 * removed; the order assertion covers the progress contract.
 */
class OnboardingStepTest {

    @Test
    fun `the wizard runs welcome, theme, storage, restore, three permission steps, finish — in order`() {
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.THEME,
                OnboardingStep.STORAGE,
                OnboardingStep.RESTORE,
                OnboardingStep.NOTIFICATIONS,
                OnboardingStep.BATTERY,
                OnboardingStep.OVERLAY,
                OnboardingStep.FINISH,
            ),
            OnboardingStep.ordered,
        )
    }

    @Test
    fun `the permission steps are exactly the four skippable ones`() {
        OnboardingStep.ordered.forEach { step ->
            val expected = step == OnboardingStep.STORAGE ||
                step == OnboardingStep.NOTIFICATIONS ||
                step == OnboardingStep.BATTERY ||
                step == OnboardingStep.OVERLAY
            assertEquals("isPermissionStep($step)", expected, step.isPermissionStep)
        }
    }

    @Test
    fun `the restore step sits right after storage and is not a permission step`() {
        assertEquals(
            OnboardingStep.ordered.indexOf(OnboardingStep.STORAGE) + 1,
            OnboardingStep.ordered.indexOf(OnboardingStep.RESTORE),
        )
        assertFalse(OnboardingStep.RESTORE.isPermissionStep)
    }

    @Test
    fun `exactly one welcome and one finish exist`() {
        assertEquals(1, OnboardingStep.ordered.count { it == OnboardingStep.WELCOME })
        assertEquals(1, OnboardingStep.ordered.count { it == OnboardingStep.FINISH })
    }

    @Test
    fun `the welcome is not a permission step`() {
        assertFalse(OnboardingStep.WELCOME.isPermissionStep)
        assertFalse(OnboardingStep.THEME.isPermissionStep)
        assertFalse(OnboardingStep.FINISH.isPermissionStep)
        assertTrue(OnboardingStep.STORAGE.isPermissionStep)
    }
}
