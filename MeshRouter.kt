package com.meshmobile.mesh

class MeshRouter {
    private val seen = mutableSetOf<String>()

    fun shouldForward(packet: MeshPacket): Boolean {
        if (packet.id in seen) return false
        seen += packet.id
        return packet.ttl > 0
    }
}
