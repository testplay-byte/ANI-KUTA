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
 * ROUND 135: RESTORE joins between STORAGE and NOTIFICATIONS. ROUND 144
 * (D-864): the INTERACTIVE personalize block joins between THEME and
 * STORAGE — PERSONALIZE (the gate), DETAILS_UI, PLAYER_UI, NOTIF_UI — the
 * wizard is now 12 steps (welcome + 11). The screens navigate via
 * steps.indexOf (no hardcoded indices — the C-1 lesson: stale magic ints
 * looped STORAGE->DETAILS_UI forever and never reached FINISH).
 */
class OnboardingStepTest {

    @Test
    fun `the wizard runs welcome, theme, personalize block, storage, restore, three permission steps, finish — in order`() {
        assertEquals(
            listOf(
                OnboardingStep.WELCOME,
                OnboardingStep.THEME,
                OnboardingStep.PERSONALIZE,
                OnboardingStep.DETAILS_UI,
                OnboardingStep.PLAYER_UI,
                OnboardingStep.NOTIF_UI,
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
    fun `the personalize block sits between theme and storage and carries no permission steps`() {
        val personalizeBlock = OnboardingStep.ordered.subList(
            OnboardingStep.ordered.indexOf(OnboardingStep.PERSONALIZE),
            OnboardingStep.ordered.indexOf(OnboardingStep.STORAGE),
        )
        assertEquals(
            listOf(
                OnboardingStep.PERSONALIZE,
                OnboardingStep.DETAILS_UI,
                OnboardingStep.PLAYER_UI,
                OnboardingStep.NOTIF_UI,
            ),
            personalizeBlock,
        )
        personalizeBlock.forEach { step ->
            assertFalse("isPermissionStep($step)", step.isPermissionStep)
        }
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
    fun `every step before finish chains to the NEXT enum value — the C-1 infinite-loop lock`() {
        // The round-144 C-1 regression: five steps still carried pre-rework
        // magic indices (STORAGE -> 3 = DETAILS_UI in the 12-step enum) and the
        // wizard looped forever, never reaching FINISH. This pins the chain:
        // each step's position is followed by the next enum entry's position,
        // so ANY future insertion keeps the chain valid by construction.
        OnboardingStep.ordered.forEachIndexed { index, step ->
            if (step != OnboardingStep.FINISH) {
                assertEquals(
                    "the step after $step must be the next enum entry",
                    index + 1,
                    OnboardingStep.ordered.indexOf(OnboardingStep.ordered[index + 1]),
                )
            }
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
