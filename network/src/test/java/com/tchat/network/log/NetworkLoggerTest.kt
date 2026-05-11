package com.tchat.network.log

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLoggerTest {

    @After
    fun tearDown() {
        NetworkLogger.setEnabledForTests(null)
        NetworkLogger.clear()
    }

    @Test
    fun `logger truncates oversized request and response bodies`() {
        NetworkLogger.setEnabledForTests(true)
        val largeBody = "x".repeat(5000)

        val requestId = NetworkLogger.logRequest(
            provider = "OpenAI",
            model = "test-model",
            url = "https://example.com",
            headers = emptyMap(),
            body = largeBody
        )
        NetworkLogger.logResponse(
            requestId = requestId,
            responseCode = 200,
            responseBody = largeBody,
            durationMs = 10
        )

        val log = NetworkLogger.getLogs().single()
        assertTrue(log.requestBody.length < largeBody.length)
        assertTrue(log.requestBody.contains("[truncated"))
        assertTrue((log.responseBody ?: "").contains("[truncated"))
    }

    @Test
    fun `logger keeps no entries when disabled`() {
        NetworkLogger.setEnabledForTests(false)

        val requestId = NetworkLogger.logRequest(
            provider = "OpenAI",
            model = "test-model",
            url = "https://example.com",
            headers = emptyMap(),
            body = "body"
        )

        assertEquals(-1L, requestId)
        assertTrue(NetworkLogger.getLogs().isEmpty())
    }

    @Test
    fun `body capture bounds streamed response content`() {
        NetworkLogger.setEnabledForTests(true)
        val capture = NetworkLogger.newBodyCapture()

        repeat(20) {
            capture.append("x".repeat(500))
        }

        val captured = capture.toString()
        assertTrue(captured.length < 5000)
        assertTrue(captured.contains("[truncated"))
    }
}
