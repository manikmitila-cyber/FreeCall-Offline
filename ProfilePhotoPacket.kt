package com.meshmobile.profile

data class ProfilePhotoPacket(
    val packetId: String,
    val userId: String,
    val mimeType: String,
    val imageBytes: ByteArray,
    val ttl: Int = 4
)
