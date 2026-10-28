package com.meshmobile.mesh

interface MeshTransport {
    fun startDiscovery()
    fun stopDiscovery()
    fun send(peerId: String, packet: MeshPacket): Boolean
}
