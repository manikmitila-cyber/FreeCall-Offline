package com.meshmobile.contacts

data class Contact(
    val userId: String,
    val username: String,
    val phoneNumber: String,
    val displayName: String,
    val profilePhotoPath: String? = null
)
