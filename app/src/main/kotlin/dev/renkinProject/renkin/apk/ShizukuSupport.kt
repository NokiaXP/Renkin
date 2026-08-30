package dev.renkinProject.renkin.apk

import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku

enum class ShizukuState {
    UNSUPPORTED,
    NOT_RUNNING,
    OUTDATED,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    READY,
    ERROR
}

/** Owns the Shizuku binder/permission checks that Ackpine deliberately leaves to its host app. */
object ShizukuSupport {
    private const val PERMISSION_REQUEST_CODE = 0x524B

    fun state(): ShizukuState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return ShizukuState.UNSUPPORTED
        return runCatching {
            when {
                !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
                Shizuku.isPreV11() -> ShizukuState.OUTDATED
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                    ShizukuState.READY
                Shizuku.shouldShowRequestPermissionRationale() ->
                    ShizukuState.PERMISSION_DENIED
                else -> ShizukuState.PERMISSION_REQUIRED
            }
        }.getOrDefault(ShizukuState.ERROR)
    }

    fun requestPermission(): Boolean {
        val current = state()
        if (current != ShizukuState.PERMISSION_REQUIRED &&
            current != ShizukuState.PERMISSION_DENIED
        ) return false
        return runCatching {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            true
        }.getOrDefault(false)
    }

    fun observe(onChanged: () -> Unit): AutoCloseable {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return AutoCloseable {}
        val mainHandler = Handler(Looper.getMainLooper())
        val notifyChanged: () -> Unit = { mainHandler.post { onChanged() } }
        val binderReceived = Shizuku.OnBinderReceivedListener { notifyChanged() }
        val binderDead = Shizuku.OnBinderDeadListener { notifyChanged() }
        val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == PERMISSION_REQUEST_CODE) notifyChanged()
        }

        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        return AutoCloseable {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
    }
}
