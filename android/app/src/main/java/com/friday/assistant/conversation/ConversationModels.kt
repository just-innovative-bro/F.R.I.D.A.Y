package com.friday.assistant.conversation

import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT
}

data class ConversationMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class ConversationRequest(
    val messages: List<ConversationMessage>
)

data class ConversationResponse(
    val content: String
)

enum class ConversationStatus {
    IDLE,
    SENDING,
    THINKING,
    RESPONDING,
    ERROR
}

data class ConversationUiState(
    val messages: List<ConversationMessage> = emptyList(),
    val status: ConversationStatus = ConversationStatus.IDLE,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val isRestoring: Boolean = true
)

open class ConversationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
