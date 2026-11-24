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
        "secret", "credential", "private", "token", "password", "passwd"
    )
    private val sensitiveSuffixes = listOf(
        ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".keychain", ".mobileprovision"
    )
    private val sensitiveExactNames = setOf(
        ".envrc", ".netrc", ".npmrc", ".pypirc", ".git-credentials",
        "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519", "id_ed25519_sk", "dockerconfig.json"
    )
    private val safeDotEnvTemplates = setOf(".env.example", ".env.sample", ".env.template")

    private val secretPattern = Regex(
        listOf(
            """-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----""",
            """\bAKIA[0-9A-Z]{16}\b""",
            """\bAIza[0-9A-Za-z_-]{30,}\b""",
            // OpenAI/Anthropic-style keys, including sk-proj-… and sk-ant-… which contain hyphens.
            """\bsk-[A-Za-z0-9][A-Za-z0-9_-]{19,}""",
            """\bgh[pousr]_[A-Za-z0-9]{36,}\b""",
            """\bgithub_pat_[A-Za-z0-9_]{22,}\b""",
            """\bxox[abprs]-[A-Za-z0-9-]{10,}""",
            // GitLab PATs and Google OAuth client secrets.
            """\bglpat-[A-Za-z0-9_-]{20,}\b""",
            """\bGOCSPX-[A-Za-z0-9_-]{20,}\b""",
            // Stripe secret/restricted keys; public pk_ keys are intentionally excluded.
            """\b(?:sk|rk)_(?:live|test)_[A-Za-z0-9]{16,}\b""",
            // JWT-like values; this is a heuristic, not JWT signature validation.
            """\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"""
        ).joinToString("|")
    )

    private val secretAssignmentPattern = Regex(
        """(?i)(?:^|[^A-Za-z0-9])(?:password|passwd|passphrase|secret(?:[_-]?key)?|api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|private[_-]?key|db[_-]?password)\s*[:=]\s*["']?([^\s"' ,;]{8,})"""
    )
    private val placeholderValuePattern = Regex(
        """(?i)^(?:changeme|change[_-]?me|example(?:[_-].*)?|sample(?:[_-].*)?|placeholder|redacted|your(?:[_-].*)?|xxx+|\*{4,}|<[^>]+>|\$\{[^}]+})$"""
    )

    fun isSensitivePath(path: String): Boolean {
        return path.replace('\\', '/').split('/').any { segment ->
            val name = segment.lowercase()
            name in sensitiveExactNames ||
                ((name == ".env" || name.startsWith(".env.") || name.endsWith(".env")) &&
                    name !in safeDotEnvTemplates) ||
                sensitiveSuffixes.any(name::endsWith) ||
                sensitiveNameParts.any(name::contains)
        }
    }

    fun containsSecret(content: String): Boolean {
        if (secretPattern.containsMatchIn(content)) return true
        return secretAssignmentPattern.findAll(content).any { match ->
            val value = match.groupValues.getOrNull(1).orEmpty()
            value.isNotBlank() && !placeholderValuePattern.matches(value)
        }
    }

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
