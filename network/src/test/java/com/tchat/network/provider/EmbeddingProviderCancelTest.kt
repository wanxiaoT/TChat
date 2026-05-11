package com.tchat.network.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EmbeddingProviderCancelTest {

    @Test
    fun openAICancelCancelsAllActiveRequests() = runBlocking {
        BlockingHttpServer(expectedRequests = 2).use { server ->
            server.start()
            val provider = OpenAIEmbeddingProvider(
                apiKey = "test-key",
                baseUrl = server.baseUrl("/v1")
            )

            val first = async(Dispatchers.IO) {
                runCatching { provider.embed(listOf("first"), "text-embedding-3-small") }
            }
            val second = async(Dispatchers.IO) {
                runCatching { provider.embed(listOf("second"), "text-embedding-3-small") }
            }

            assertTrue(server.awaitRequests())
            provider.cancel()

            withTimeout(5_000) {
                assertTrue(first.await().isFailure)
                assertTrue(second.await().isFailure)
            }
        }
    }

    @Test
    fun geminiCancelCancelsAllActiveRequests() = runBlocking {
        BlockingHttpServer(expectedRequests = 2).use { server ->
            server.start()
            val provider = GeminiEmbeddingProvider(
                apiKey = "test-key",
                baseUrl = server.baseUrl("/v1")
            )

            val first = async(Dispatchers.IO) {
                runCatching { provider.embed(listOf("first"), "text-embedding-004") }
            }
            val second = async(Dispatchers.IO) {
                runCatching { provider.embed(listOf("second"), "text-embedding-004") }
            }

            assertTrue(server.awaitRequests())
            provider.cancel()

            withTimeout(5_000) {
                assertTrue(first.await().isFailure)
                assertTrue(second.await().isFailure)
            }
        }
    }

    private class BlockingHttpServer(
        expectedRequests: Int
    ) : Closeable {
        private val address = InetAddress.getByName("127.0.0.1")
        private val serverSocket = ServerSocket(0, 50, address)
        private val requestsStarted = CountDownLatch(expectedRequests)
        private val releaseResponses = CountDownLatch(1)
        private val executor = Executors.newCachedThreadPool()

        @Volatile
        private var running = true

        fun start() {
            executor.execute {
                while (running) {
                    try {
                        val socket = serverSocket.accept()
                        executor.execute { handle(socket) }
                    } catch (e: Exception) {
                        if (running) throw e
                    }
                }
            }
        }

        fun baseUrl(pathPrefix: String): String {
            return "http://127.0.0.1:${serverSocket.localPort}$pathPrefix"
        }

        fun awaitRequests(): Boolean {
            return requestsStarted.await(5, TimeUnit.SECONDS)
        }

        private fun handle(socket: Socket) {
            socket.use { client ->
                readHeaders(client)
                requestsStarted.countDown()
                releaseResponses.await(10, TimeUnit.SECONDS)

                val body = """{"data":[{"index":0,"embedding":[0.1]}],"embedding":{"values":[0.1]},"embeddings":[{"values":[0.1]}]}"""
                val response = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: application/json\r\n")
                    append("Content-Length: ${body.toByteArray().size}\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                    append(body)
                }
                client.getOutputStream().write(response.toByteArray())
                client.getOutputStream().flush()
            }
        }

        private fun readHeaders(socket: Socket) {
            val input = socket.getInputStream()
            var previous = -1
            var matched = 0
            while (true) {
                val current = input.read()
                if (current == -1) return
                matched = when {
                    previous == '\r'.code && current == '\n'.code && matched == 0 -> 1
                    previous == '\n'.code && current == '\r'.code && matched == 1 -> 2
                    previous == '\r'.code && current == '\n'.code && matched == 2 -> return
                    else -> 0
                }
                previous = current
            }
        }

        override fun close() {
            running = false
            releaseResponses.countDown()
            runCatching { serverSocket.close() }
            executor.shutdownNow()
        }
    }
}
