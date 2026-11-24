package dev.johnrish.pocketengineer.workspace

/**
 * Heuristics that keep credential-looking files out of AI requests. Best-effort only: a pass here
 * does not prove a file is free of secrets, so attachments stay explicit, bounded user actions.
 */
object SensitiveFileGuard {
    private val sensitiveNameParts = listOf("secret", "credential", "private", "token", "password", "passwd")
    private val sensitiveSuffixes = listOf(".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".keychain", ".env")
    private val sensitiveExactNames = setOf(
        ".envrc", ".netrc", ".npmrc", ".pypirc",
        "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519", "id_ed25519_sk"
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
            // GitLab personal/project access tokens and Google OAuth client secrets.
            """\bglpat-[A-Za-z0-9_-]{20,}\b""",
            """\bGOCSPX-[A-Za-z0-9_-]{20,}\b""",
            // Stripe secret/restricted keys (public pk_ keys are intentionally not included).
            """\b(?:sk|rk)_(?:live|test)_[A-Za-z0-9]{16,}\b""",
            // JWTs: three base64url-like segments, with a conservative minimum segment length.
            """\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b"""
        ).joinToString("|")
    )

    private val secretAssignmentPattern = Regex(
        """(?i)\b(?:password|passwd|passphrase|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|private[_-]?key)\b\s*[:=]\s*["']?([^\s"' ,;]{8,})"""
    )
    private val placeholderValuePattern = Regex(
        """(?i)^(?:changeme|change[_-]?me|example(?:[_-].*)?|sample(?:[_-].*)?|placeholder|redacted|your(?:[_-].*)?|xxx+|\*{4,}|<[^>]+>|\$\{[^}]+\})$"""
    )

    fun isSensitivePath(path: String): Boolean {
        return path.split('/').any { segment ->
            val name = segment.lowercase()
            name in sensitiveExactNames ||
                (name == ".env" || name.startsWith(".env.") || name.endsWith(".env")) &&
                    name !in safeDotEnvTemplates ||
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
}
