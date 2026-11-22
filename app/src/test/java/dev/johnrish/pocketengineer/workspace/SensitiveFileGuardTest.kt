package dev.johnrish.pocketengineer.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveFileGuardTest {
    @Test
    fun flagsSensitiveFileNames() {
        listOf(".env", "config/prod.ENV", "certs/server.pem", "id.key", "my-secrets.txt", "aws_credentials", "api_token.json")
            .forEach { assertTrue("should flag '$it'", SensitiveFileGuard.isSensitivePath(it)) }
    }

    @Test
    fun allowsOrdinarySourceFiles() {
        listOf("README.md", "app/src/main/Main.kt", "docs/ROADMAP.md")
            .forEach { assertFalse("should allow '$it'", SensitiveFileGuard.isSensitivePath(it)) }
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
    }

    @Test
    fun detectsHyphenatedProviderKeys() {
        // sk-proj-… and sk-ant-… style keys contain hyphens and previously slipped through.
        assertTrue(SensitiveFileGuard.containsSecret("k=sk-proj-" + "Ab1_".repeat(8)))
        assertTrue(SensitiveFileGuard.containsSecret("k=sk-ant-api03-" + "Zy9-".repeat(8)))
    }

    @Test
    fun detectsVcsAndChatTokens() {
        assertTrue(SensitiveFileGuard.containsSecret("t=ghp_" + "a1B2".repeat(9)))
        assertTrue(SensitiveFileGuard.containsSecret("t=github_pat_" + "a1B2_".repeat(6)))
        assertTrue(SensitiveFileGuard.containsSecret("t=xoxb-1234567890-abcdefghij"))
    }

    @Test
    fun ignoresPlainProseAndShortLookalikes() {
        assertFalse(SensitiveFileGuard.containsSecret("fun main() { println(\"hello\") }"))
        assertFalse(SensitiveFileGuard.containsSecret("the sk-8 setting and ghp_short are fine"))
        assertFalse(SensitiveFileGuard.containsSecret("AKIA is a prefix, but this is not a key"))
    }
}
