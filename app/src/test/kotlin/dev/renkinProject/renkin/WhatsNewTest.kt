package dev.renkinProject.renkin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {

    @Test
    fun existingUserWithoutHistorySeesCurrentRelease() {
        assertTrue(
            shouldShowWhatsNew(
                lastSeenVersion = null,
                onboardingSeen = true,
                installedVersion = LATEST_WHATS_NEW_VERSION_CODE
            )
        )
    }

    @Test
    fun freshInstallDoesNotStackReleaseNotesOverOnboarding() {
        assertFalse(
            shouldShowWhatsNew(
                lastSeenVersion = null,
                onboardingSeen = false,
                installedVersion = LATEST_WHATS_NEW_VERSION_CODE
            )
        )
    }

    @Test
    fun newerReleaseShowsOnlyAfterItIsInstalled() {
        assertFalse(
            shouldShowWhatsNew(
                lastSeenVersion = LATEST_WHATS_NEW_VERSION_CODE - 1,
                onboardingSeen = true,
                installedVersion = LATEST_WHATS_NEW_VERSION_CODE - 1
            )
        )
        assertTrue(
            shouldShowWhatsNew(
                lastSeenVersion = LATEST_WHATS_NEW_VERSION_CODE - 1,
                onboardingSeen = true,
                installedVersion = LATEST_WHATS_NEW_VERSION_CODE
            )
        )
    }

    @Test
    fun dismissedReleaseDoesNotAppearAgain() {
        assertFalse(
            shouldShowWhatsNew(
                lastSeenVersion = LATEST_WHATS_NEW_VERSION_CODE,
                onboardingSeen = true,
                installedVersion = LATEST_WHATS_NEW_VERSION_CODE
            )
        )
        assertFalse(
            shouldShowWhatsNew(
                lastSeenVersion = LATEST_WHATS_NEW_VERSION_CODE + 1,
                onboardingSeen = true,
                installedVersion = LATEST_WHATS_NEW_VERSION_CODE + 1
            )
        )
    }
}
