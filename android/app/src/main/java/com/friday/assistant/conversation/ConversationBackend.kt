package com.friday.assistant.conversation

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

data class BackendConfiguration(
    val endpointUrl: String,
    val accessToken: String?
)

interface ConversationBackend {
    fun configurationError(): String?

    suspend fun complete(
        request: ConversationRequest,
        onWaitingForResponse: () -> Unit
    ): ConversationResponse
}

class HttpJsonConversationBackend(
    private val configuration: () -> BackendConfiguration
) : ConversationBackend {
    override fun configurationError(): String? {
        val savedConfiguration = try {
            configuration()
        } catch (_: SecureTokenException) {
            return "The saved backend token cannot be unlocked. Re-enter it in Connection settings."
        }
        val endpoint = try {
            URI(savedConfiguration.endpointUrl.trim())
        } catch (_: IllegalArgumentException) {
            return "The F.R.I.D.A.Y. backend URL is invalid."
        }

        if (!endpoint.isAbsolute ||
            endpoint.host.isNullOrBlank() ||
            endpoint.scheme?.lowercase() !in setOf("http", "https") ||
            endpoint.userInfo != null
        ) {
            return "Enter the complete backend URL, such as http://<PC-LAN-IP>:8765/api/chat."
        }
        if (savedConfiguration.accessToken.isNullOrBlank()) {
            return "Enter the F.R.I.D.A.Y. server access token in Connection settings."
        }
        return null
    }

    override suspend fun complete(
        request: ConversationRequest,
        onWaitingForResponse: () -> Unit
    ): ConversationResponse = withContext(Dispatchers.IO) {
        configurationError()?.let { throw ConversationException(it) }
        val savedConfiguration = configuration()
        val latestUserMessage = request.messages.lastOrNull { it.role == MessageRole.USER }
            ?: throw ConversationException("A user message is required.")
        if (request.messages.lastOrNull()?.role != MessageRole.USER) {
            throw ConversationException("The latest conversation message must be from the user.")
        }

        val endpoint = try {
            URL(savedConfiguration.endpointUrl.trim())
        } catch (exception: IllegalArgumentException) {
            throw ConversationException("The F.R.I.D.A.Y. backend URL is invalid.", exception)
        }
        val connection = try {
            endpoint.openConnection() as? HttpURLConnection
                ?: throw ConversationException("The configured backend does not support HTTP.")
        } catch (exception: IOException) {
            throw networkFailure(exception)
        }

        runInterruptible {
            try {
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.connectTimeout = REQUEST_TIMEOUT_MILLIS
                connection.readTimeout = REQUEST_TIMEOUT_MILLIS
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer ${savedConfiguration.accessToken}"
                )
                val requestBody = FridayChatRequest(
                    message = latestUserMessage.content,
                    messages = request.messages.map {
                        FridayChatMessage(it.role.name.lowercase(), it.content)
                    }
                )
                connection.outputStream.use { output ->
                    output.write(
                        GSON.toJson(requestBody).toByteArray(StandardCharsets.UTF_8)
                    )
                }

                onWaitingForResponse()
                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    val errorBody = connection.errorStream
                        ?.bufferedReader(StandardCharsets.UTF_8)
                        ?.use { it.readText() }
                    throw backendFailure(statusCode, errorBody)
                }

                val responseBody = connection.inputStream
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
                decodeResponse(responseBody)
            } catch (exception: SocketTimeoutException) {
                throw ConversationException("The F.R.I.D.A.Y. backend request timed out.", exception)
            } catch (exception: UnknownHostException) {
                throw ConversationException(
                    "The F.R.I.D.A.Y. backend could not be reached. Check the URL and network connection.",
                    exception
                )
            } catch (exception: ConnectException) {
                throw ConversationException(
                    "The F.R.I.D.A.Y. backend is unavailable at the configured address.",
                    exception
                )
            } catch (exception: IOException) {
                throw ConversationException(
                    "The network request failed. Check the local network connection.",
                    exception
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun decodeResponse(responseBody: String): ConversationResponse {
        val response = try {
            JsonParser.parseString(responseBody).asJsonObject
        } catch (exception: Exception) {
            if (exception !is JsonParseException && exception !is IllegalStateException) {
                throw exception
            }
            throw ConversationException("The backend returned an invalid JSON response.", exception)
        }
        val status = response.get("status")
        val reply = response.get("reply")
        if (status == null || !status.isJsonPrimitive || status.asString != "success") {
            throw ConversationException("The backend did not confirm a successful response.")
        }
        if (reply == null || !reply.isJsonPrimitive || !reply.asJsonPrimitive.isString) {
            throw ConversationException("The backend response did not contain reply text.")
        }
        val content = reply.asString.trim()
        if (content.isEmpty()) {
            throw ConversationException("The backend returned an empty response.")
        }
        return ConversationResponse(content)
    }

    private fun backendFailure(statusCode: Int, responseBody: String?): ConversationException {
        if (statusCode == 401) {
            return ConversationException(
                "The server access token was rejected. Check Connection settings."
            )
        }
        val serverMessage = responseBody?.let(::parseSafeServerError)
        return when {
            serverMessage != null -> ConversationException(serverMessage)
            statusCode == 503 ->
                ConversationException("The F.R.I.D.A.Y. AI provider is not ready or unavailable.")
            else -> ConversationException("The F.R.I.D.A.Y. backend returned HTTP $statusCode.")
        }
    }

    private fun parseSafeServerError(body: String): String? = try {
        val error = JsonParser.parseString(body).asJsonObject.get("error")
        if (error != null && error.isJsonPrimitive && error.asJsonPrimitive.isString) {
            error.asString.takeIf { it.isNotBlank() }?.take(MAX_ERROR_LENGTH)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun networkFailure(exception: IOException): ConversationException = when (exception) {
        is SocketTimeoutException ->
            ConversationException("The F.R.I.D.A.Y. backend request timed out.", exception)
        is UnknownHostException ->
            ConversationException(
                "The F.R.I.D.A.Y. backend could not be reached. Check the URL and network connection.",
                exception
            )
        is ConnectException ->
            ConversationException(
                "The F.R.I.D.A.Y. backend is unavailable at the configured address.",
                exception
            )
        else ->
            ConversationException(
                "The network request failed. Check the local network connection.",
                exception
            )
    }

    private data class FridayChatMessage(
        val role: String,
        val content: String
    )

    private data class FridayChatRequest(
        val message: String,
        val messages: List<FridayChatMessage>
    )

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 30_000
        const val MAX_ERROR_LENGTH = 300
        val GSON = com.google.gson.Gson()
    }
}
