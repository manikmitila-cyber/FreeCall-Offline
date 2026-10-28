package com.meshmobile.mesh

data class MeshMessagePacket(
    val packetId: String,
    val sourceUserId: String,
    val destinationUserId: String,
    val text: String,
    val ttl: Int = 8
)
