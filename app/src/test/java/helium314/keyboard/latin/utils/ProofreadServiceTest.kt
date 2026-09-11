package helium314.keyboard.latin.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProofreadServiceTest {

    // ----------------------------------------------------------------------------------- URL Construction

    @Test
    fun geminiChatUrlDoesNotContainKeyParam() {
        val url = ProofreadService.buildChatUrl(
            provider = ProofreadService.AiProvider.GEMINI,
            endpoint = "https://ignored.com"
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            url
        )
        assertFalse(url.contains("?key="), "Gemini chat URL must not include key in query: $url")
        assertFalse(url.contains("ignored.com"), "Gemini chat URL must use generative language endpoint")
    }

    @Test
    fun geminiModelsUrlContainsKeyParam() {
        val url = ProofreadService.buildModelsUrl(
            provider = ProofreadService.AiProvider.GEMINI,
            endpoint = "https://ignored.com",
            apiKey = "AIzaSyTestKey123"
        )
        assertTrue(url.contains("?key=AIzaSyTestKey123"), "Gemini models URL must include API key query parameter: $url")
    }

    @Test
    fun openaiChatUrlDoesNotContainKeyInQuery() {
        val endpoint = "https://api.openai.com/v1"
        val url = ProofreadService.buildChatUrl(
            provider = ProofreadService.AiProvider.OPENAI,
            endpoint = endpoint
        )
        assertEquals("https://api.openai.com/v1/chat/completions", url)
        assertFalse(url.contains("?key="), "OpenAI chat URL must not include key in query")
    }

    @Test
    fun openaiModelsUrlDoesNotContainKeyInQuery() {
        val endpoint = "https://api.openai.com/v1"
        val url = ProofreadService.buildModelsUrl(
            provider = ProofreadService.AiProvider.OPENAI,
            endpoint = endpoint,
            apiKey = "sk-secretKey"
        )
        assertEquals("https://api.openai.com/v1/models", url)
        assertFalse(url.contains("?key="), "OpenAI models URL must not include key in query")
    }

    // ----------------------------------------------------------------------------------- Auth Headers

    private val geminiChatUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    private val geminiModelsUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=AIzaSyTestKey123"

    @Test
    fun geminiOpenAiProxyChatSendsBearerHeader() {
        val header = ProofreadService.buildAuthHeader(
            provider = ProofreadService.AiProvider.GEMINI,
            apiKey = "AIzaSyTestKey123",
            requestUrl = geminiChatUrl
        )
        assertEquals("Bearer AIzaSyTestKey123", header, "Gemini OpenAI-compatible chat must send Bearer header")
    }

    @Test
    fun geminiNativeModelsNeverSendsAuthorizationHeader() {
        val header = ProofreadService.buildAuthHeader(
            provider = ProofreadService.AiProvider.GEMINI,
            apiKey = "AIzaSyTestKey123",
            requestUrl = geminiModelsUrl
        )
        assertNull(header, "Gemini native REST must not send Authorization header (key goes in query)")

        val headerWithoutKey = ProofreadService.buildAuthHeader(
            provider = ProofreadService.AiProvider.GEMINI,
            apiKey = null,
            requestUrl = geminiModelsUrl
        )
        assertNull(headerWithoutKey)
    }

    @Test
    fun geminiOpenAiProxyWithoutKeySendsNoHeader() {
        val header = ProofreadService.buildAuthHeader(
            provider = ProofreadService.AiProvider.GEMINI,
            apiKey = null,
            requestUrl = geminiChatUrl
        )
        assertNull(header)
    }

    @Test
    fun openaiRemoteSendsBearerHeader() {
        val header = ProofreadService.buildAuthHeader(
            provider = ProofreadService.AiProvider.OPENAI,
            apiKey = "sk-proj-12345",
            requestUrl = "https://api.openai.com/v1/chat/completions"
        )
        assertEquals("Bearer sk-proj-12345", header)
    }

    @Test
    fun openaiLocalWithKeySendsBearerHeader() {
        val header = ProofreadService.buildAuthHeader(
            provider = ProofreadService.AiProvider.OPENAI,
            apiKey = "my-local-token",
            requestUrl = "http://localhost:11434/v1/chat/completions"
        )
        assertEquals("Bearer my-local-token", header)
    }

    @Test
    fun openaiLocalWithoutKeyNeverSendsFakeBearerLocal() {
        val headerNull = ProofreadService.buildAuthHeader(
            provider = ProofreadService.AiProvider.OPENAI,
            apiKey = null,
            requestUrl = "http://localhost:11434/v1/chat/completions"
        )
        assertNull(headerNull, "Local endpoint without key must not send Authorization header")

        val headerBlank = ProofreadService.buildAuthHeader(
            provider = ProofreadService.AiProvider.OPENAI,
            apiKey = "   ",
            requestUrl = "http://localhost:11434/v1/chat/completions"
        )
        assertNull(headerBlank, "Local endpoint with blank key must not send Authorization header")
    }

    // ----------------------------------------------------------------------------------- Local Endpoint Detection

    @Test
    fun detectsLocalEndpointsCorrectly() {
        assertTrue(ProofreadService.isLocalEndpoint("http://localhost:11434/v1"))
        assertTrue(ProofreadService.isLocalEndpoint("http://localhost:8000"))
        assertTrue(ProofreadService.isLocalEndpoint("http://127.0.0.1:11434/v1"))
        assertTrue(ProofreadService.isLocalEndpoint("http://127.0.0.1:8000"))
        assertTrue(ProofreadService.isLocalEndpoint("http://[::1]:11434/v1"))
        assertTrue(ProofreadService.isLocalEndpoint("http://192.168.1.50:11434/v1"))
        assertTrue(ProofreadService.isLocalEndpoint("http://192.168.0.1:8080"))
        assertTrue(ProofreadService.isLocalEndpoint("http://10.0.0.1:11434/v1"))
        assertTrue(ProofreadService.isLocalEndpoint("http://10.254.0.1:8000"))
        assertTrue(ProofreadService.isLocalEndpoint("http://172.16.0.1:11434/v1"))
        assertTrue(ProofreadService.isLocalEndpoint("http://172.24.1.1:11434/v1"))
        assertTrue(ProofreadService.isLocalEndpoint("http://172.31.255.255:11434/v1"))
        assertTrue(ProofreadService.isLocalEndpoint("http://server.local:11434/v1"))
        assertTrue(ProofreadService.isLocalEndpoint("http://ollama.localhost:11434/v1"))
    }

    @Test
    fun nonLocalEndpointsAreNotDetectAsLocal() {
        assertFalse(ProofreadService.isLocalEndpoint("https://api.openai.com/v1"))
        assertFalse(ProofreadService.isLocalEndpoint("https://api.huggingface.co/v1"))
        assertFalse(ProofreadService.isLocalEndpoint("https://openrouter.ai/api/v1"))
        assertFalse(ProofreadService.isLocalEndpoint("https://api.groq.com/openai/v1"))
        // Host containing 192.168 substring in path or query must NOT be detected as local
        assertFalse(ProofreadService.isLocalEndpoint("https://example.com/proxy/192.168.1.1/v1"))
        assertFalse(ProofreadService.isLocalEndpoint("https://example.com?target=192.168.1.1"))
        // 172.32.0.1 is outside RFC 1918 private range (172.16.0.0 - 172.31.255.255)
        assertFalse(ProofreadService.isLocalEndpoint("http://172.32.0.1:11434/v1"))
        // Public IPs
        assertFalse(ProofreadService.isLocalEndpoint("http://8.8.8.8:8000"))
        assertFalse(ProofreadService.isLocalEndpoint("http://1.1.1.1:8000"))
    }
}
