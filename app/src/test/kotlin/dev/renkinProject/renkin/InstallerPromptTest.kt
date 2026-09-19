package dev.renkinProject.renkin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstallerPromptTest {

    @Test
    fun firstUnconfirmedBuildPromptsForAChoice() {
        assertEquals(
            MainViewModel.InstallerPromptReason.FIRST_CHOICE,
            installerPromptReason(askEveryTime = false, choiceConfirmed = false)
        )
    }

    @Test
    fun confirmedChoiceSkipsThePrompt() {
        assertNull(installerPromptReason(askEveryTime = false, choiceConfirmed = true))
    }

    @Test
    fun askEveryTimeAlwaysPrompts() {
        assertEquals(
            MainViewModel.InstallerPromptReason.ASK_EVERY_TIME,
            installerPromptReason(askEveryTime = true, choiceConfirmed = true)
        )
    }
}
