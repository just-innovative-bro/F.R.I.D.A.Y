package com.friday.assistant.conversation

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationStoreTest {
    @Test
    fun persistsOrderedMessagesAndTimestampsAcrossStoreInstances() = runBlocking {
        val directory = Files.createTempDirectory("friday-conversation-test").toFile()
        try {
            val file = directory.resolve("history.json")
            val expected = listOf(
                ConversationMessage(
                    id = "user-1",
                    role = MessageRole.USER,
                    content = "Hello",
                    timestampMillis = 1_700_000_000_000
                ),
                ConversationMessage(
                    id = "assistant-1",
                    role = MessageRole.ASSISTANT,
                    content = "A real reply",
                    timestampMillis = 1_700_000_000_100
                )
            )

            JsonFileConversationStore(file).save(expected)

            assertEquals(expected, JsonFileConversationStore(file).load())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun missingHistoryStartsEmptyAndCanBeCleared() = runBlocking {
        val directory = Files.createTempDirectory("friday-conversation-clear-test").toFile()
        try {
            val store = JsonFileConversationStore(directory.resolve("history.json"))
            assertTrue(store.load().isEmpty())
            store.save(listOf(ConversationMessage(role = MessageRole.USER, content = "Saved")))
            store.save(emptyList())
            assertTrue(store.load().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}
