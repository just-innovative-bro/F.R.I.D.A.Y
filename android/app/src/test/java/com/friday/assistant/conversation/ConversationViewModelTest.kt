package com.friday.assistant.conversation

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun successfulResponseIsPersistedAndMovesThroughRequestStates() = runTest(dispatcher) {
        val store = TestStore()
        val backend = TestBackend {
            it()
            ConversationResponse("Response from configured backend")
        }
        val viewModel = ConversationViewModel(ConversationRepository(store, backend))
        runCurrent()

        viewModel.send("  Hello Friday  ")
        assertEquals(ConversationStatus.SENDING, viewModel.state.value.status)
        runCurrent()

        assertEquals(ConversationStatus.IDLE, viewModel.state.value.status)
        assertEquals(
            listOf(MessageRole.USER, MessageRole.ASSISTANT),
            viewModel.state.value.messages.map { it.role }
        )
        assertEquals("Hello Friday", store.messages.first().content)
        assertEquals(
            "Response from configured backend",
            store.messages.last().content
        )
        assertEquals(1, backend.calls)
        assertFalse(viewModel.state.value.canRetry)
    }

    @Test
    fun exposesRespondingWhileTheActualReplyIsBeingPersisted() = runTest(dispatcher) {
        val assistantSaveStarted = CompletableDeferred<Unit>()
        val allowAssistantSave = CompletableDeferred<Unit>()
        val store = TestStore(
            beforeSave = { messages ->
                if (messages.lastOrNull()?.role == MessageRole.ASSISTANT) {
                    assistantSaveStarted.complete(Unit)
                    allowAssistantSave.await()
                }
            }
        )
        val backend = TestBackend {
            it()
            ConversationResponse("Actual reply")
        }
        val viewModel = ConversationViewModel(ConversationRepository(store, backend))
        runCurrent()

        viewModel.send("Question")
        runCurrent()

        assertTrue(assistantSaveStarted.isCompleted)
        assertEquals(ConversationStatus.RESPONDING, viewModel.state.value.status)
        assertEquals("Actual reply", viewModel.state.value.messages.last().content)

        allowAssistantSave.complete(Unit)
        advanceUntilIdle()

        assertEquals(ConversationStatus.IDLE, viewModel.state.value.status)
        assertEquals("Actual reply", store.messages.last().content)
    }

    @Test
    fun failedRequestKeepsUserMessageAndRetryCanSucceed() = runTest(dispatcher) {
        val store = TestStore()
        var shouldFail = true
        val backend = TestBackend {
            it()
            if (shouldFail) {
                throw ConversationException("The backend is unavailable.")
            }
            ConversationResponse("Recovered response")
        }
        val viewModel = ConversationViewModel(ConversationRepository(store, backend))
        runCurrent()

        viewModel.send("Question")
        runCurrent()

        assertEquals(ConversationStatus.ERROR, viewModel.state.value.status)
        assertEquals("The backend is unavailable.", viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.canRetry)
        assertEquals(listOf(MessageRole.USER), store.messages.map { it.role })

        shouldFail = false
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(ConversationStatus.IDLE, viewModel.state.value.status)
        assertEquals("Recovered response", store.messages.last().content)
        assertEquals(2, backend.calls)
    }

    @Test
    fun ignoresEmptyMessagesAndPreventsDuplicateInFlightRequests() = runTest(dispatcher) {
        val store = TestStore()
        val response = CompletableDeferred<ConversationResponse>()
        val backend = TestBackend {
            it()
            response.await()
        }
        val viewModel = ConversationViewModel(ConversationRepository(store, backend))
        runCurrent()

        viewModel.send("   ")
        assertTrue(viewModel.state.value.messages.isEmpty())
        viewModel.send("Question")
        viewModel.send("Duplicate")
        runCurrent()

        assertEquals(ConversationStatus.THINKING, viewModel.state.value.status)
        assertEquals(1, backend.calls)
        assertEquals(listOf("Question"), viewModel.state.value.messages.map { it.content })

        response.complete(ConversationResponse("Answer"))
        advanceUntilIdle()
        assertEquals(ConversationStatus.IDLE, viewModel.state.value.status)
    }

    @Test
    fun clearingViewModelCancelsTheInFlightConversationRequest() = runTest(dispatcher) {
        val requestStarted = CompletableDeferred<Unit>()
        var requestCancelled = false
        val store = TestStore()
        val backend = TestBackend { onWaiting ->
            onWaiting()
            requestStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                requestCancelled = true
            }
        }
        val viewModel = ConversationViewModel(ConversationRepository(store, backend))
        val viewModelStore = ViewModelStore().apply {
            put("conversation", viewModel)
        }
        runCurrent()

        viewModel.send("Question")
        runCurrent()
        assertTrue(requestStarted.isCompleted)
        assertEquals(ConversationStatus.THINKING, viewModel.state.value.status)

        viewModelStore.clear()
        runCurrent()

        assertTrue(requestCancelled)
        assertEquals(listOf(MessageRole.USER), store.messages.map { it.role })
    }

    @Test
    fun newConversationClearsPersistentAndInMemoryHistory() = runTest(dispatcher) {
        val store = TestStore(
            initialMessages = listOf(
                ConversationMessage(role = MessageRole.USER, content = "Old message")
            )
        )
        val viewModel = ConversationViewModel(
            ConversationRepository(store, TestBackend { ConversationResponse("unused") })
        )
        runCurrent()
        assertEquals(1, viewModel.state.value.messages.size)

        viewModel.startNewConversation()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.messages.isEmpty())
        assertTrue(store.messages.isEmpty())
    }

    private class TestStore(
        initialMessages: List<ConversationMessage> = emptyList(),
        private val beforeSave: suspend (List<ConversationMessage>) -> Unit = {}
    ) : ConversationStore {
        private val savedMessages = MutableStateFlow(initialMessages)
        val messages: List<ConversationMessage> get() = savedMessages.value

        override suspend fun load(): List<ConversationMessage> = savedMessages.value

        override suspend fun save(messages: List<ConversationMessage>) {
            beforeSave(messages)
            savedMessages.value = messages.toList()
        }
    }

    private class TestBackend(
        private val action: suspend (onWaiting: () -> Unit) -> ConversationResponse
    ) : ConversationBackend {
        var calls = 0
            private set

        override fun configurationError(): String? = null

        override suspend fun complete(
            request: ConversationRequest,
            onWaitingForResponse: () -> Unit
        ): ConversationResponse {
            calls++
            return action(onWaitingForResponse)
        }
    }
}
