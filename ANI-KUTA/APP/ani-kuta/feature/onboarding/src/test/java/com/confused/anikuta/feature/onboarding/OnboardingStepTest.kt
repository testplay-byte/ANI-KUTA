package com.confused.anikuta.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-403 (round 28): the wizard step machine's contract — the step ORDER
 * (the UI's stepIndex arithmetic depends on it), the permission-step
 * classification (the skip affordance + the finish summary's grouping key
 * off it), and the wizardIndex ordinals (the progress segments).
 */
class OnboardingStepTest {

    @Test
    fun `the wizard runs welcome, theme, three permission steps, finish — in order`() {
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.THEME,
                OnboardingStep.STORAGE,
                OnboardingStep.NOTIFICATIONS,
                OnboardingStep.BATTERY,
                OnboardingStep.FINISH,
            ),
            OnboardingStep.ordered,
        )
    }

    @Test
    fun `the three middle steps are the skippable permission steps`() {
        OnboardingStep.ordered.forEach { step ->
            val expected = step == OnboardingStep.STORAGE ||
                step == OnboardingStep.NOTIFICATIONS ||
                step == OnboardingStep.BATTERY
            assertEquals("isPermissionStep($step)", expected, step.isPermissionStep)
        }
    }

    @Test
    fun `wizardIndex matches the ordinal — the progress math is stable`() {
        OnboardingStep.ordered.forEachIndexed { index, step ->
            assertEquals(index, step.wizardIndex)
        }
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
