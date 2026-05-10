package com.tchat.network.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {

    @Test
    fun `registry exposes built in providers and aliases`() {
        assertNotNull(ProviderRegistry.get("openai"))
        assertEquals("openai-responses", ProviderRegistry.get("openai_responses")?.id)
        assertEquals("openai-responses", ProviderRegistry.get("responses")?.id)
        assertEquals("naapi-tchat", ProviderRegistry.get("naapi_tchat")?.id)
        assertEquals("naapi-tchat", ProviderRegistry.get("naapi")?.id)
    }

    @Test
    fun `definition resolves defaults before provider creation`() {
        val naapi = ProviderRegistry.get("naapi_tchat")
            ?: error("NAAPI TChat provider definition missing")

        val resolved = naapi.resolve(ProviderCreateConfig(apiKey = "test-key"))

        assertEquals("naapi-tchat", resolved.providerId)
        assertEquals("https://t.naapi.cc/v1", resolved.baseUrl)
        assertEquals("gpt-4o-mini", resolved.model)
        assertEquals("/chat/completions", resolved.chatPath)
        assertEquals("/images/generations", resolved.imagesPath)
    }

    @Test
    fun `model registry exposes model capabilities`() {
        assertTrue(ModelRegistry.supports("gemini", "gemini-1.5-pro", ModelCapability.VIDEO_INPUT))
        assertTrue(ModelRegistry.supports("openai", "gpt-4o", ModelCapability.VISION))
        assertFalse(ModelRegistry.supports("openai", "gpt-3.5-turbo", ModelCapability.VISION))
        assertEquals("deepseek-chat", ModelRegistry.getDefaultModel("deepseek")?.id)
    }

    @Test
    fun `factory keeps old entrypoints while using registry definitions`() {
        val gemini = AIProviderFactory.create(
            AIProviderFactory.ProviderConfig(
                type = AIProviderFactory.ProviderType.GEMINI,
                apiKey = "test-key"
            )
        )
        val naapi = AIProviderFactory.create(
            providerType = "naapi_tchat",
            apiKey = "test-key",
            model = ""
        )

        assertTrue(gemini is GeminiProvider)
        assertTrue(naapi is OpenAIProvider)
    }
}
