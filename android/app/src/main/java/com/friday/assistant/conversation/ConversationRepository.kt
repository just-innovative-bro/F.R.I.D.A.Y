package com.friday.assistant.conversation

class ConversationRepository(
    private val store: ConversationStore,
    private val backend: ConversationBackend
) {
    fun backendConfigurationError(): String? = backend.configurationError()

    suspend fun load(): List<ConversationMessage> = store.load()

    suspend fun save(messages: List<ConversationMessage>) {
        store.save(messages)
    }

    suspend fun respond(
        messages: List<ConversationMessage>,
        onWaitingForResponse: () -> Unit
    ): ConversationResponse = backend.complete(
        ConversationRequest(messages),
        onWaitingForResponse
    )
}
