package com.meshmobile.chat

data class ChatMessage(
    val id: String,
    val sender: String,
    val text: String,
    val timestamp: Long
)
