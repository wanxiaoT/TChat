package com.tchat.wanxiaot.ocr

data class OcrExtractedCredentials(
    val rawText: String,
    val apiKey: String?,
    val baseUrl: String?
)

object OcrCredentialExtractor {

    private val urlRegex = Regex(
        pattern = """https?://[^\s"'<>]+""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val labeledApiKeyRegex = Regex(
        pattern = """(?i)(api[-_ ]?key|api[-_ ]?token|token|key)\s*[:=]\s*([A-Za-z0-9_\-]{16,})"""
    )

    private val openAiKeyRegex = Regex("""\bsk-[A-Za-z0-9]{20,}\b""")
    private val anthropicKeyRegex = Regex("""\bsk-ant-[A-Za-z0-9_\-]{10,}\b""", RegexOption.IGNORE_CASE)
    private val geminiKeyRegex = Regex("""\bAIza[0-9A-Za-z_\-]{20,}\b""")
    private val genericKeyRegex = Regex("""\b[A-Za-z0-9_\-]{24,}\b""")

    fun extract(text: String): OcrExtractedCredentials {
        val normalized = text
            .replace('\uFF1A', ':') // full-width colon
            .replace('\uFF1D', '=') // full-width equals
            .trim()

        val urls = urlRegex.findAll(normalized).map { it.value.trimEnd('.', ',', ';') }.toList()
        val baseUrl = urls
            .asSequence()
            .map { normalizeBaseUrl(it) }
            .distinct()
            .sortedWith(compareByDescending<String> { scoreBaseUrl(it) }.thenByDescending { it.length })
            .firstOrNull()

        val candidates = linkedSetOf<String>()

        labeledApiKeyRegex.findAll(normalized).forEach { match ->
            candidates += match.groupValues.getOrNull(2).orEmpty()
        }

        listOf(openAiKeyRegex, anthropicKeyRegex, geminiKeyRegex, genericKeyRegex).forEach { regex ->
            regex.findAll(normalized).forEach { match ->
                candidates += match.value
            }
        }

        val apiKey = candidates
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.contains("http", ignoreCase = true) }
            .sortedWith(compareByDescending<String> { scoreApiKey(it) }.thenByDescending { it.length })
            .firstOrNull()

        return OcrExtractedCredentials(
            rawText = text,
            apiKey = apiKey,
            baseUrl = baseUrl
        )
    }

    private fun scoreApiKey(value: String): Int {
        return when {
            value.startsWith("sk-ant-", ignoreCase = true) -> 100
            value.startsWith("sk-") -> 90
            value.startsWith("AIza") -> 80
            else -> 10
        }
    }

    private fun scoreBaseUrl(value: String): Int {
        val lowered = value.lowercase()
        var score = 0
        if ("/v1" in lowered) score += 50
        if ("/v1beta" in lowered) score += 50
        if ("api." in lowered || "/api" in lowered) score += 10
        if ("docs" in lowered) score -= 30
        if ("github" in lowered) score -= 30
        return score
    }

    private fun normalizeBaseUrl(value: String): String {
        val cleaned = value
            .substringBefore('#')
            .substringBefore('?')
            .trim()
            .trimEnd('/')

        val versionMatch = Regex("""^(https?://[^/]+/(v\d+(?:beta)?))(?:/.*)?$""", RegexOption.IGNORE_CASE)
            .find(cleaned)
        return if (versionMatch != null) {
            versionMatch.groupValues[1].trimEnd('/')
        } else {
            cleaned
        }
    }
}
