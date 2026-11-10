package com.friday.assistant.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val repository: ConversationRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(ConversationUiState())
    val state: StateFlow<ConversationUiState> = mutableState.asStateFlow()
    private var failedUserMessageId: String? = null

    fun backendConfigurationError(): String? = repository.backendConfigurationError()

    init {
        viewModelScope.launch {
            try {
                val messages = repository.load()
                mutableState.update { it.copy(messages = messages, isRestoring = false) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.update {
                    it.copy(
                        status = ConversationStatus.ERROR,
                        errorMessage = exception.toUserMessage(),
                        isRestoring = false
                    )
                }
            }
        }
    }

    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty() || mutableState.value.isBusy()) return

        val userMessage = ConversationMessage(
            role = MessageRole.USER,
            content = content
        )
        val updatedMessages = mutableState.value.messages + userMessage
        submit(userMessage, updatedMessages)
    }

    fun retry() {
        if (mutableState.value.isBusy()) return
        val failedId = failedUserMessageId ?: return
        val failedMessage = mutableState.value.messages.lastOrNull {
            it.id == failedId && it.role == MessageRole.USER
        } ?: return
        submit(failedMessage, mutableState.value.messages)
    }

    fun startNewConversation() {
        if (mutableState.value.isBusy()) return
        viewModelScope.launch {
            try {
                repository.save(emptyList())
                failedUserMessageId = null
                mutableState.value = ConversationUiState(isRestoring = false)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.update {
                    it.copy(
                        status = ConversationStatus.ERROR,
                        errorMessage = exception.toUserMessage(),
                        canRetry = false
                    )
                }
            }
        }
    }

    private fun submit(
        userMessage: ConversationMessage,
        messages: List<ConversationMessage>
    ) {
        failedUserMessageId = userMessage.id
        mutableState.value = mutableState.value.copy(
            messages = messages,
            status = ConversationStatus.SENDING,
            errorMessage = null,
            canRetry = false,
            isRestoring = false
        )

        viewModelScope.launch {
            try {
                repository.save(messages)
                val response = repository.respond(messages) {
                    mutableState.update { current ->
                        if (current.status == ConversationStatus.SENDING) {
                            current.copy(status = ConversationStatus.THINKING)
                        } else {
                            current
                        }
                    }
                }

                val completedMessages = messages + ConversationMessage(
                    role = MessageRole.ASSISTANT,
                    content = response.content
                )
                mutableState.update {
                    it.copy(
                        messages = completedMessages,
                        status = ConversationStatus.RESPONDING,
                        errorMessage = null,
                        canRetry = false
                    )
                }
                try {
                    repository.save(completedMessages)
                    failedUserMessageId = null
                    mutableState.update {
                        it.copy(status = ConversationStatus.IDLE, canRetry = false)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    failedUserMessageId = null
                    mutableState.update {
                        it.copy(
                            status = ConversationStatus.ERROR,
                            errorMessage =
                                "F.R.I.D.A.Y. replied, but the conversation could not be saved.",
                            canRetry = false
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.update {
                    it.copy(
                        status = ConversationStatus.ERROR,
                        errorMessage = exception.toUserMessage(),
                        canRetry = true
                    )
                }
            }
        }
    }

    private fun Exception.toUserMessage(): String =
        (this as? ConversationException)?.message
            ?: "The conversation could not be completed. Please try again."

    private fun ConversationUiState.isBusy(): Boolean =
        status == ConversationStatus.SENDING ||
            status == ConversationStatus.THINKING ||
            status == ConversationStatus.RESPONDING

    class Factory(
        private val repository: ConversationRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(ConversationViewModel::class.java)) {
                throw IllegalArgumentException("Unsupported ViewModel type: ${modelClass.name}")
            }
            return ConversationViewModel(repository) as T
        }
    }
}
