package com.meshmobile.mesh

data class MeshPacket(
    val id: String,
    val source: String,
    val destination: String,
    val ttl: Int,
    val payload: ByteArray
)
