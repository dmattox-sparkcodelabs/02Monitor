package com.o2monitor.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Broadcast receiver to start the relay service on device boot.
 * Stub implementation - to be completed in Phase 2.4.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // TODO: Start RelayService if auto-start is enabled
        }
    }
}
