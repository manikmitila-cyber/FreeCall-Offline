package com.meshmobile.mesh

data class MeshCallPacket(
    val packetId: String,
    val sourceUserId: String,
    val destinationUserId: String,
    val type: String, // CALL_OFFER, CALL_ACCEPT, CALL_END, AUDIO
    val ttl: Int = 8,
    val audioBytes: ByteArray? = null
)
