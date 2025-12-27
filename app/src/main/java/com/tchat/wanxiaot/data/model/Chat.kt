package com.tchat.wanxiaot.data.model

data class Chat(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<Message> = emptyList()
)
