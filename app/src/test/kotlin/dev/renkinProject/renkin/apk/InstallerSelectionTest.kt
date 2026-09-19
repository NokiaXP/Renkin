package dev.renkinProject.renkin.apk

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import dev.renkinProject.renkin.data.ExternalInstallerComponentKey
import dev.renkinProject.renkin.data.InstallMethod
import dev.renkinProject.renkin.data.InstallMethodKey
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallerSelectionTest {

    @Test
    fun missingPreferencesFallBackToTheSystemInstaller() {
        assertEquals(InstallerSelection(InstallMethod.SYSTEM), emptyPreferences().installerSelection())
    }

    @Test
    fun storedMethodAndComponentAreReadTogether() {
        val preferences = preferencesOf(
            InstallMethodKey to InstallMethod.EXTERNAL.ordinal,
            ExternalInstallerComponentKey to "com.example/.Install"
        )

        assertEquals(
            InstallerSelection(InstallMethod.EXTERNAL, "com.example/.Install"),
            preferences.installerSelection()
        )
    }
}
