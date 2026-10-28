package com.meshmobile.account

import com.meshmobile.profile.UserProfile

class AccountManager {
    private var profile: UserProfile? = null

    fun createAccount(phoneNumber: String, username: String, displayName: String): UserProfile {
        val id = "u_" + username.lowercase().replace(Regex("[^a-z0-9_]"), "")
        return UserProfile(id, phoneNumber, username, displayName).also { profile = it }
    }

    fun currentProfile(): UserProfile? = profile

    fun updateProfilePhoto(path: String) {
        profile = profile?.copy(profilePhotoPath = path)
    }
}
