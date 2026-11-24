package dev.johnrish.pocketengineer.workspace

/**
 * Best-effort credential safeguards for files that may be shown to or shared with an AI agent.
 * These heuristics deliberately fail closed for likely credential files. A clean result does not
 * prove arbitrary content is secret-free, so callers must keep file access explicit and bounded.
 */
object SensitiveFileGuard {
    const val SENSITIVE_PATH_MESSAGE =
        "This file name indicates it may contain credentials, so AirFite will not share it with the agent."
    const val SENSITIVE_CONTENT_MESSAGE =
        "This file appears to contain a credential, so AirFite will not share it with the agent."

    private val sensitiveNameParts = listOf(
        "secret", "credential", "private", "token", "password", "passwd",
        "apikey", "api_key", "api-key", "id_rsa", "id_ed25519", "id_ecdsa", "id_dsa", "keystore"
    )
    private val sensitiveSuffixes = listOf(
        ".env", ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".mobileprovision"
    )
    private val sensitiveExactNames = setOf(
        ".npmrc", ".pypirc", ".netrc", ".git-credentials", "dockerconfig.json"
    )

    private val secretPattern = Regex(
        listOf(
            """-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----""",
            """\bAKIA[0-9A-Z]{16}\b""",
            """\bAIza[0-9A-Za-z_-]{30,}\b""",
            // OpenAI/Anthropic-style keys, including sk-proj-… and sk-ant-… which contain hyphens.
            """\bsk-[A-Za-z0-9][A-Za-z0-9_-]{19,}""",
            """\bgh[pousr]_[A-Za-z0-9]{36,}\b""",
            """\bgithub_pat_[A-Za-z0-9_]{22,}\b""",
            """\bxox[abprs]-[A-Za-z0-9-]{10,}"""
        ).joinToString("|")
    )

    fun isSensitivePath(path: String): Boolean {
        val segments = path.replace('\\', '/').lowercase().split('/')
        return segments.any { segment ->
            segment.startsWith(".env") ||
                segment in sensitiveExactNames ||
                sensitiveSuffixes.any(segment::endsWith) ||
                sensitiveNameParts.any(segment::contains)
        }
    }

    fun containsSecret(content: String): Boolean = secretPattern.containsMatchIn(content)

    /** Reject a likely credential path before opening it. */
    fun requireSafePath(path: String) {
        if (isSensitivePath(path)) throw SecurityException(SENSITIVE_PATH_MESSAGE)
    }

    /** Reject recognized credential material after a bounded read. */
    fun requireSafeContent(content: String) {
        if (containsSecret(content)) throw SecurityException(SENSITIVE_CONTENT_MESSAGE)
    }

    fun requireSafeToShare(path: String, content: String) {
        requireSafePath(path)
        requireSafeContent(content)
    }
}
