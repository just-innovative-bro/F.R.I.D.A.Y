package com.friday.assistant.conversation

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationBackendTest {
    @Test
    fun sendsConfiguredHistoryAndParsesStandardChatResponse() = withServer(
        statusCode = 200,
        responseBody = """{"reply":"Actual server answer","status":"success"}"""
    ) { server ->
        val receivedRequest = AtomicReference<String>()
        val receivedAuthorization = AtomicReference<String>()
        server.createContext("/assistant/chat") { exchange ->
            receivedAuthorization.set(exchange.requestHeaders.getFirst("Authorization"))
            receivedRequest.set(exchange.requestBody.bufferedReader().use { it.readText() })
            exchange.respond(
                200,
                """{"reply":"Actual server answer","status":"success"}"""
            )
        }
        server.start()

        val backend = HttpJsonConversationBackend {
            BackendConfiguration(
                endpointUrl = server.endpoint("/assistant/chat"),
                accessToken = "test-server-token"
            )
        }
        val request = ConversationRequest(
            listOf(
                ConversationMessage(
                    id = "u1",
                    role = MessageRole.USER,
                    content = "Earlier question",
                    timestampMillis = 10
                ),
                ConversationMessage(
                    id = "a1",
                    role = MessageRole.ASSISTANT,
                    content = "Earlier answer",
                    timestampMillis = 20
                ),
                ConversationMessage(
                    id = "u2",
                    role = MessageRole.USER,
                    content = "latest \"question\"",
                    timestampMillis = 30
                )
            )
        )
        var awaitingResponse = false

        val response = runBlocking {
            backend.complete(request) { awaitingResponse = true }
        }

        assertEquals(ConversationResponse("Actual server answer"), response)
        assertTrue(awaitingResponse)
        assertEquals("Bearer test-server-token", receivedAuthorization.get())
        val serialized = JsonParser.parseString(receivedRequest.get()).asJsonObject
        assertEquals("latest \"question\"", serialized.get("message").asString)
        val history = serialized.getAsJsonArray("messages")
        assertEquals(3, history.size())
        assertEquals("user", history[0].asJsonObject.get("role").asString)
        assertEquals("assistant", history[1].asJsonObject.get("role").asString)
        assertEquals("Earlier answer", history[1].asJsonObject.get("content").asString)
    }

    @Test
    fun reportsHttpErrorsWithoutExposingResponseBody() = withServer(
        statusCode = 503,
        responseBody = "sensitive server diagnostic"
    ) { server ->
        server.createContext("/chat") { exchange ->
            exchange.respond(503, "sensitive server diagnostic")
        }
        server.start()
        val backend = backend(server.endpoint("/chat"))

        val failure = runCatching {
            runBlocking {
                backend.complete(userRequest()) {}
            }
        }.exceptionOrNull()

        assertTrue(failure is ConversationException)
        assertEquals(
            "The F.R.I.D.A.Y. AI provider is not ready or unavailable.",
            failure?.message
        )
        assertTrue(failure?.message?.contains("sensitive") == false)
    }

    @Test
    fun rejectsMalformedAndEmptyResponseBodies() = withServer(
        statusCode = 200,
        responseBody = """{"reply":"","status":"success"}"""
    ) { server ->
        server.createContext("/empty") { exchange ->
            exchange.respond(200, """{"reply":"","status":"success"}""")
        }
        server.start()
        val emptyResponse = runCatching {
            runBlocking {
                backend(server.endpoint("/empty")).complete(userRequest()) {}
            }
        }.exceptionOrNull()
        assertEquals("The backend returned an empty response.", emptyResponse?.message)

        server.stop(0)
        withServer(statusCode = 200, responseBody = "not json") { malformedServer ->
            malformedServer.createContext("/malformed") { exchange ->
                exchange.respond(200, "not json")
            }
            malformedServer.start()
            val malformedResponse = runCatching {
                runBlocking {
                    backend(malformedServer.endpoint("/malformed")).complete(userRequest()) {}
                }
            }.exceptionOrNull()
            assertEquals(
                "The backend returned an invalid JSON response.",
                malformedResponse?.message
            )
        }
    }

    @Test
    fun requiresEndpointAndAccessTokenConfiguration() {
        val backend = HttpJsonConversationBackend {
            BackendConfiguration(
                endpointUrl = "",
                accessToken = null
            )
        }

        assertNotNull(backend.configurationError())
        val failure = runCatching {
            runBlocking {
                backend.complete(userRequest()) {}
            }
        }.exceptionOrNull()
        assertTrue(failure is ConversationException)
    }

    private fun backend(endpoint: String) = HttpJsonConversationBackend {
        BackendConfiguration(
            endpointUrl = endpoint,
            accessToken = "test-server-token"
        )
    }

    private fun userRequest() = ConversationRequest(
        listOf(ConversationMessage(role = MessageRole.USER, content = "Hello"))
    )

    private fun withServer(
        statusCode: Int,
        responseBody: String,
        block: (HttpServer) -> Unit
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.respond(statusCode, responseBody)
        }
        try {
            block(server)
        } finally {
            server.stop(0)
        }
    }

    private fun HttpServer.endpoint(path: String): String =
        "http://127.0.0.1:${address.port}$path"

    private fun com.sun.net.httpserver.HttpExchange.respond(
        statusCode: Int,
        body: String
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        sendResponseHeaders(statusCode, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
