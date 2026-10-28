package com.meshmobile.profile

/**
 * Local profile-photo abstraction.
 * The selected/cropped image is stored locally and its path is advertised
 * through the encrypted mesh profile packet.
 */
class ProfilePhotoManager {
    var localPhotoPath: String? = null
        private set

    fun setPhoto(path: String) {
        localPhotoPath = path
    }

    fun removePhoto() {
        localPhotoPath = null
    }
}
