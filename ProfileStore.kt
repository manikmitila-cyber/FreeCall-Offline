package com.meshmobile.profile

class ProfileStore {
    private val cache = mutableMapOf<String, UserProfile>()

    fun save(profile: UserProfile) {
        cache[profile.userId] = profile
    }

    fun get(userId: String): UserProfile? = cache[userId]

    fun all(): List<UserProfile> = cache.values.toList()
}
