package com.meshmobile.call

class VoiceCallSession {
    var active: Boolean = false
        private set

    fun start() { active = true }
    fun stop() { active = false }
}
