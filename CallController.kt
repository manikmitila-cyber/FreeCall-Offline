package com.meshmobile.call

class CallController {
    var state: CallState = CallState.IDLE
        private set

    var peerUserId: String? = null
        private set

    fun call(userId: String) {
        peerUserId = userId
        state = CallState.RINGING
    }

    fun accept() { state = CallState.CONNECTING }
    fun connected() { state = CallState.CONNECTED }
    fun end() {
        state = CallState.ENDED
        peerUserId = null
    }
}
