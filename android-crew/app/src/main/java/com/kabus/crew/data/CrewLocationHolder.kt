package com.kabus.crew.data

import android.location.Location

/**
 * Single source of truth for the latest real device fix.
 *
 * LocationService pushes every fused callback result here after also uploading
 * it, so the "My Device" map shows exactly the position the server has been
 * sent - one GPS request in the foreground service, one shared value.
 */
object CrewLocationHolder {
    @Volatile
    var lastLocation: Location? = null

    private val listeners = mutableListOf<(Location) -> Unit>()

    fun onLocation(l: Location) {
        lastLocation = l
        synchronized(listeners) {
            for (listener in listeners.toList()) {
                try {
                    listener(l)
                } catch (_: Throwable) {
                    // a dead UI observer must never break the upload path
                }
            }
        }
    }

    fun addListener(listener: (Location) -> Unit): () -> Unit {
        synchronized(listeners) {
            listeners.add(listener)
        }
        return {
            synchronized(listeners) {
                listeners.remove(listener)
            }
        }
    }
}