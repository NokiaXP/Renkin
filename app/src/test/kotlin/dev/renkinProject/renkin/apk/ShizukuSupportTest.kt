package dev.renkinProject.renkin.apk

import android.app.Application
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ShizukuSupportTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun installManager(packageName: String) {
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply { this.packageName = packageName }
        )
    }

    @Test
    fun withoutBinderOrManagerShizukuIsNotInstalled() {
        assertNull(ShizukuSupport.managerPackage(context))
        assertEquals(ShizukuState.NOT_INSTALLED, ShizukuSupport.state(context))
        assertFalse(ShizukuSupport.openManager(context))
    }

    @Test
    fun installedManagerWithoutBinderIsNotRunning() {
        installManager("moe.shizuku.privileged.api")

        assertEquals("moe.shizuku.privileged.api", ShizukuSupport.managerPackage(context))
        assertEquals(ShizukuState.NOT_RUNNING, ShizukuSupport.state(context))
    }

    @Test
    fun shizukuPlusManagerIsRecognised() {
        installManager("af.shizuku.plus.api")

        assertEquals("af.shizuku.plus.api", ShizukuSupport.managerPackage(context))
    }

    @Test
    fun permissionIsNotRequestedWhileShizukuIsNotRunning() {
        installManager("moe.shizuku.privileged.api")

        assertFalse(ShizukuSupport.requestPermission(context))
    }
}
