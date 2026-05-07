package com.tiberiptv.fire

object RemoteActionGuard {
    private var currentLabel: String? = null

    @JvmStatic
    @Synchronized
    fun tryAcquire(label: String?): Boolean {
        if (currentLabel != null) {
            return false
        }
        currentLabel = if (label.isNullOrEmpty()) "remote" else label
        return true
    }

    @JvmStatic
    @Synchronized
    fun release(label: String?) {
        val active = currentLabel ?: return
        if (label.isNullOrEmpty() || active == label) {
            currentLabel = null
        }
    }

    @JvmStatic
    @Synchronized
    fun isActive(): Boolean = currentLabel != null

    @JvmStatic
    @Synchronized
    fun activeLabel(): String = currentLabel.orEmpty()
}
