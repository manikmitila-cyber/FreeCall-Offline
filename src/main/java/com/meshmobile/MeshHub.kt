package com.meshmobile

import android.content.Context

/** One shared NearbyManager for the whole app and the background service. */
object MeshHub {
    @Volatile private var instance: NearbyManager? = null

    fun get(context: Context, displayName: String, username: String, phone: String): NearbyManager {
        val app = context.applicationContext
        app.getSharedPreferences("freecall_nearby", Context.MODE_PRIVATE).edit()
            .putString("dn", displayName)
            .putString("un", username)
            .putString("ph", phone)
            .apply()
        return instance ?: synchronized(this) {
            instance ?: NearbyManager(app, displayName, username, phone).also { instance = it }
        }
    }

    /** Rebuild from saved profile, used by the background service and after reboot. */
    fun restore(context: Context): NearbyManager? {
        val p = context.applicationContext.getSharedPreferences("freecall_nearby", Context.MODE_PRIVATE)
        val dn = p.getString("dn", null) ?: return null
        return get(context, dn, p.getString("un", "") ?: "", p.getString("ph", "") ?: "")
    }
}
