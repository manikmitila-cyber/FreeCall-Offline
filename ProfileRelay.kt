package com.meshmobile.mesh

import com.meshmobile.profile.ProfilePacket

class ProfileRelay {
    fun shouldRelay(packetId: String, seen: MutableSet<String>): Boolean {
        return seen.add(packetId)
    }

    fun receiveProfile(packet: ProfilePacket) {
        // Persist/cached profile data locally in the next implementation stage.
    }
}
