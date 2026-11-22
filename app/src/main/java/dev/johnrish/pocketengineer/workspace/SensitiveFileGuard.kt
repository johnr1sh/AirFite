package dev.johnrish.pocketengineer.workspace

/**
 * Heuristics that keep credential-looking files out of AI requests. Best-effort only: a pass here
 * does not prove a file is free of secrets, so attachments stay explicit, bounded user actions.
 */
object SensitiveFileGuard {
    private val sensitiveNameParts = listOf("secret", "credential", "private", "token")
    private val sensitiveSuffixes = listOf(".env", ".pem", ".key")

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
        val lower = path.lowercase()
        return sensitiveSuffixes.any(lower::endsWith) || sensitiveNameParts.any(lower::contains)
    }

    fun containsSecret(content: String): Boolean = secretPattern.containsMatchIn(content)
}
