package dev.johnrish.pocketengineer.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveFileGuardTest {
    @Test
    fun flagsSensitiveFileNames() {
        listOf(
            ".env", ".env.production", ".env.local", "config/prod.ENV", "prod.env", ".envrc",
            "certs/server.pem", "id.key", "server.p12", "signing.jks", ".ssh/id_ed25519",
            ".netrc", ".npmrc", ".pypirc", "my-secrets.txt", "aws_credentials", "api_token.json",
            "config/passwords.yaml"
        ).forEach { assertTrue("should flag '$it'", SensitiveFileGuard.isSensitivePath(it)) }
    }

    @Test
    fun allowsOrdinarySourceFilesAndEnvTemplates() {
        listOf(
            "README.md", "app/src/main/Main.kt", "docs/ROADMAP.md",
            ".env.example", ".env.sample", ".env.template", "public-key.pem.txt"
        ).forEach { assertFalse("should allow '$it'", SensitiveFileGuard.isSensitivePath(it)) }
    }

    @Test
    fun detectsPrivateKeyBlocks() {
        assertTrue(SensitiveFileGuard.containsSecret("x\n-----BEGIN RSA PRIVATE KEY-----\nabc"))
        assertTrue(SensitiveFileGuard.containsSecret("-----BEGIN OPENSSH PRIVATE KEY-----"))
        assertTrue(SensitiveFileGuard.containsSecret("-----BEGIN PRIVATE KEY-----"))
    }

    @Test
    fun detectsCloudAndProviderKeys() {
        assertTrue(SensitiveFileGuard.containsSecret("aws=AKIAABCDEFGHIJKLMNOP"))
        assertTrue(SensitiveFileGuard.containsSecret("key=AIza" + "a".repeat(35)))
        assertTrue(SensitiveFileGuard.containsSecret("OPENAI=sk-" + "A1b2".repeat(6)))
        assertTrue(SensitiveFileGuard.containsSecret("stripe=sk_live_" + "a".repeat(24)))
        assertTrue(SensitiveFileGuard.containsSecret("oauth=GOCSPX-" + "a".repeat(24)))
    }

    @Test
    fun detectsHyphenatedProviderKeys() {
        assertTrue(SensitiveFileGuard.containsSecret("k=sk-proj-" + "Ab1_".repeat(8)))
        assertTrue(SensitiveFileGuard.containsSecret("k=sk-ant-api03-" + "Zy9-".repeat(8)))
    }

    @Test
    fun detectsVcsAndChatTokens() {
        assertTrue(SensitiveFileGuard.containsSecret("t=ghp_" + "a1B2".repeat(9)))
        assertTrue(SensitiveFileGuard.containsSecret("t=github_pat_" + "a1B2_".repeat(6)))
        assertTrue(SensitiveFileGuard.containsSecret("t=xoxb-1234567890-abcdefghij"))
        assertTrue(SensitiveFileGuard.containsSecret("t=glpat-" + "aB12_".repeat(5)))
    }

    @Test
    fun detectsJwtAndCredentialAssignments() {
        assertTrue(SensitiveFileGuard.containsSecret("jwt=eyJabcdefghijk.eyJabcdefghijk.signatureabcdefghijk"))
        assertTrue(SensitiveFileGuard.containsSecret("password=correct-horse-battery"))
        assertTrue(SensitiveFileGuard.containsSecret("client_secret: \"secret-value-123\""))
        assertTrue(SensitiveFileGuard.containsSecret("API_KEY=abcdef0123456789"))
    }

    @Test
    fun ignoresPlainProseShortLookalikesAndPlaceholders() {
        assertFalse(SensitiveFileGuard.containsSecret("fun main() { println(\"hello\") }"))
        assertFalse(SensitiveFileGuard.containsSecret("the sk-8 setting and ghp_short are fine"))
        assertFalse(SensitiveFileGuard.containsSecret("AKIA is a prefix, but this is not a key"))
        assertFalse(SensitiveFileGuard.containsSecret("api_key=YOUR_API_KEY"))
        assertFalse(SensitiveFileGuard.containsSecret("password=changeme"))
        assertFalse(SensitiveFileGuard.containsSecret("secret: example-value"))
    }
}
