package com.meshmobile.chat

class ChatSession(private val peerUserId: String) {
    private val messages = mutableListOf<ChatMessage>()

    fun send(text: String, sender: String): ChatMessage {
        val msg = ChatMessage(
            id = "m_" + System.nanoTime(),
            sender = sender,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        messages += msg
        return msg
    }

    fun history(): List<ChatMessage> = messages.toList()
}
