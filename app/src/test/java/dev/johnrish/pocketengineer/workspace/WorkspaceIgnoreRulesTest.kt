package dev.johnrish.pocketengineer.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceIgnoreRulesTest {
    @Test
    fun ignoresRootAnchoredDirectoriesOnlyAtRoot() {
        val rules = WorkspaceIgnoreRules.parse("/build/")
        assertTrue(rules.isIgnored("build", isDirectory = true))
        assertTrue(rules.isIgnored("build/output.apk", isDirectory = false))
        assertFalse(rules.isIgnored("app/build", isDirectory = true))
        assertFalse(rules.isIgnored("app/build/output.apk", isDirectory = false))
    }

    @Test
    fun unanchoredDirectoryRulesMatchAtAnyDepth() {
        val rules = WorkspaceIgnoreRules.parse("build/")
        assertTrue(rules.isIgnored("build", isDirectory = true))
        assertTrue(rules.isIgnored("app/build", isDirectory = true))
        assertTrue(rules.isIgnored("app/build/classes/Main.class", isDirectory = false))
        assertFalse(rules.isIgnored("app/build.gradle.kts", isDirectory = false))
    }

    @Test
    fun globRulesMatchFilesAndDirectories() {
        val rules = WorkspaceIgnoreRules.parse("*.tmp\n**/generated/**\ncache/")
        assertTrue(rules.isIgnored("notes.tmp", isDirectory = false))
        assertTrue(rules.isIgnored("src/notes.tmp", isDirectory = false))
        assertTrue(rules.isIgnored("generated/Model.kt", isDirectory = false))
        assertTrue(rules.isIgnored("app/generated/Model.kt", isDirectory = false))
        assertTrue(rules.isIgnored("cache", isDirectory = true))
        assertTrue(rules.isIgnored("cache/data.bin", isDirectory = false))
        assertFalse(rules.isIgnored("cache.txt", isDirectory = false))
    }

    @Test
    fun supportsQuestionMarkAndSlashScopedPatterns() {
        val rules = WorkspaceIgnoreRules.parse("/config/*.json\nsrc/**/test?.kt")
        assertTrue(rules.isIgnored("config/app.json", isDirectory = false))
        assertFalse(rules.isIgnored("other/config/app.json", isDirectory = false))
        assertTrue(rules.isIgnored("src/test1.kt", isDirectory = false))
        assertTrue(rules.isIgnored("src/unit/test2.kt", isDirectory = false))
        assertFalse(rules.isIgnored("src/unit/test12.kt", isDirectory = false))
        assertFalse(rules.isIgnored("test1.kt", isDirectory = false))
    }

    @Test
    fun laterNegationOverridesEarlierRule() {
        val rules = WorkspaceIgnoreRules.parse("*.log\n!important.log")
        assertTrue(rules.isIgnored("debug.log", isDirectory = false))
        assertFalse(rules.isIgnored("important.log", isDirectory = false))
        assertFalse(rules.isIgnored("logs/important.log", isDirectory = false))
    }

    @Test
    fun escapedCommentAndNegationMarkersAreLiteral() {
        val rules = WorkspaceIgnoreRules.parse("\\#literal.txt\n\\!literal.txt")
        assertTrue(rules.isIgnored("#literal.txt", isDirectory = false))
        assertTrue(rules.isIgnored("!literal.txt", isDirectory = false))
        assertFalse(rules.isIgnored("ordinary.txt", isDirectory = false))
    }

    @Test
    fun blankLinesAndCommentsAreIgnored() {
        val rules = WorkspaceIgnoreRules.parse("\n# generated\n  \n*.bak\n")
        assertTrue(rules.isIgnored("a.bak", isDirectory = false))
        assertFalse(rules.isIgnored("README.md", isDirectory = false))
    }
}
