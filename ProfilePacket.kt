package com.meshmobile.profile

data class ProfilePacket(
    val userId: String,
    val username: String,
    val displayName: String,
    val phoneNumber: String,
    val photoBytes: ByteArray? = null
)
