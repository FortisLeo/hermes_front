package com.rishitgoklani.hermes_front_mobile.domain

enum class BotState { Idle, Listening, Thinking, Speaking, Working, Error }

data class ChatMessage(val id: Long, val fromUser: Boolean, val text: String)
