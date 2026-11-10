package com.friday.assistant.conversation

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ConversationStore {
    suspend fun load(): List<ConversationMessage>

    suspend fun save(messages: List<ConversationMessage>)
}

class JsonFileConversationStore(
    private val file: File,
    private val gson: Gson = Gson()
) : ConversationStore {
    private val lock = Any()

    override suspend fun load(): List<ConversationMessage> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!file.exists()) {
                return@synchronized emptyList()
            }

            try {
                val type = object : TypeToken<List<ConversationMessage>>() {}.type
                val messages: List<ConversationMessage>? =
                    gson.fromJson(file.readText(StandardCharsets.UTF_8), type)
                messages ?: throw ConversationException("Saved conversation history is invalid.")
                if (messages.any {
                        it.id.isBlank() || it.content.isBlank() || it.timestampMillis < 0
                    }
                ) {
                    throw ConversationException("Saved conversation history is invalid.")
                }
                messages
            } catch (exception: JsonParseException) {
                throw ConversationException("Saved conversation history could not be read.", exception)
            } catch (exception: IOException) {
                throw ConversationException("Saved conversation history could not be read.", exception)
            }
        }
    }

    override suspend fun save(messages: List<ConversationMessage>) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val parent = file.absoluteFile.parentFile
                ?: throw ConversationException("Conversation history could not be saved.")
            val temporaryFile = File(parent, "${file.name}.tmp")

            try {
                Files.createDirectories(parent.toPath())
                Files.write(
                    temporaryFile.toPath(),
                    gson.toJson(messages).toByteArray(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
                try {
                    Files.move(
                        temporaryFile.toPath(),
                        file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        temporaryFile.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
                Unit
            } catch (exception: IOException) {
                throw ConversationException("Conversation history could not be saved.", exception)
            } finally {
                temporaryFile.delete()
            }
        }
    }
}
