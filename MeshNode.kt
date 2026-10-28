package com.meshmobile.mesh

import java.util.UUID

class MeshNode(val nodeId: String = UUID.randomUUID().toString()) {
    private val peers = mutableSetOf<String>()

    fun addPeer(peerId: String) { peers += peerId }
    fun removePeer(peerId: String) { peers -= peerId }
    fun peers(): Set<String> = peers.toSet()
}
