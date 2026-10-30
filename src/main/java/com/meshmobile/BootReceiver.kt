package com.meshmobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restart the background service after reboot, if the user already has an account. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (MeshHub.restore(context) != null) MeshService.start(context)
    }
}
