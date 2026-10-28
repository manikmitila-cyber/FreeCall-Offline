package com.meshmobile.profile

data class UserProfile(
    val userId: String,
    val phoneNumber: String,
    val username: String,
    val displayName: String,
    val profilePhotoPath: String? = null
)
