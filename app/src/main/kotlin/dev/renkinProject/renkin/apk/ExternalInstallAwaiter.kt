package dev.renkinProject.renkin.apk

import java.util.concurrent.atomic.AtomicLong

object ExternalInstallAwaiter {
    private val foregroundGeneration = AtomicLong(0L)

    fun currentGeneration(): Long = foregroundGeneration.get()

    fun onRenkinForegrounded() {
        foregroundGeneration.incrementAndGet()
    }
}
