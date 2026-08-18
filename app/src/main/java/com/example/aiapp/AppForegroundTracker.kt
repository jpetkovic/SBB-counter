package com.example.aiapp

/**
 * Detects a genuine fresh app launch (new process), as opposed to returning
 * to the app from the background or switching tabs within an already-running session.
 */
object AppForegroundTracker {
    private var pendingFreshStart = true

    fun consumePendingReset(): Boolean {
        if (!pendingFreshStart) return false
        pendingFreshStart = false
        return true
    }
}
