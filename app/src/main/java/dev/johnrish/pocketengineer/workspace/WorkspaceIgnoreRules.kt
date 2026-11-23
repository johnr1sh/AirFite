package dev.johnrish.pocketengineer.workspace

/**
 * Conservative root-level .gitignore matcher for workspace browsing and bounded search.
 *
 * Supports comments, blank lines, negation, directory-only rules, root anchoring,
 * basename rules, '*', '?', and '**'. Character classes and escaped trailing spaces
 * are intentionally unsupported; an unsupported pattern is treated literally.
 */
class WorkspaceIgnoreRules private constructor(private val rules: List<Rule>) {
    private data class Rule(
        val pattern: String,
        val negated: Boolean,
        val directoryOnly: Boolean,
        val anchored: Boolean,
        val hasSlash: Boolean,
        val regex: Regex
    ) {
        fun matches(path: String, isDirectory: Boolean): Boolean {
            if (directoryOnly && !isDirectory) return false
            return if (anchored || hasSlash) {
                regex.matches(path)
            } else {
                regex.matches(path.substringAfterLast('/'))
            }
        }
    }

    fun isIgnored(relativePath: String, isDirectory: Boolean): Boolean {
        val segments = relativePath.split('/').filter(String::isNotEmpty)
        if (segments.isEmpty()) return false

        // Evaluate ancestors as directories so directory rules also exclude descendants.
        val candidates = segments.indices.map { index ->
            segments.take(index + 1).joinToString("/") to (index < segments.lastIndex || isDirectory)
        }
        var ignored = false
        for (rule in rules) {
            if (candidates.any { (path, directory) -> rule.matches(path, directory) }) {
                ignored = !rule.negated
            }
        }
        return ignored
    }

    companion object {
        val EMPTY = WorkspaceIgnoreRules(emptyList())

        fun parse(contents: String): WorkspaceIgnoreRules {
            val rules = contents.lineSequence().mapNotNull { raw ->
                var line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null

                var negated = false
                if (line.startsWith("\\#") || line.startsWith("\\!")) {
                    line = line.drop(1)
                } else if (line.startsWith("!")) {
                    negated = true
                    line = line.drop(1)
                }
                line = line.trimEnd()
                if (line.isEmpty()) return@mapNotNull null

                val directoryOnly = line.endsWith("/")
                if (directoryOnly) line = line.dropLast(1)
                val anchored = line.startsWith("/")
                if (anchored) line = line.drop(1)
                if (line.isEmpty()) return@mapNotNull null

                Rule(
                    pattern = line,
                    negated = negated,
                    directoryOnly = directoryOnly,
                    anchored = anchored,
                    hasSlash = line.contains('/'),
                    regex = globRegex(line)
                )
            }.toList()
            return if (rules.isEmpty()) EMPTY else WorkspaceIgnoreRules(rules)
        }

        private fun globRegex(pattern: String): Regex {
            val out = StringBuilder("^")
            var index = 0
            while (index < pattern.length) {
                when (pattern[index]) {
                    '\\' -> {
                        if (index + 1 < pattern.length) {
                            out.append(Regex.escape(pattern[index + 1].toString()))
                            index += 2
                        } else {
                            out.append(Regex.escape("\\"))
                            index++
                        }
                    }
                    '*' -> {
                        if (index + 1 < pattern.length && pattern[index + 1] == '*') {
                            if (index + 2 < pattern.length && pattern[index + 2] == '/') {
                                out.append("(?:.*/)?")
                                index += 3
                            } else {
                                out.append(".*")
                                index += 2
                            }
                        } else {
                            out.append("[^/]*")
                            index++
                        }
                    }
                    '?' -> {
                        out.append("[^/]")
                        index++
                    }
                    else -> {
                        out.append(Regex.escape(pattern[index].toString()))
                        index++
                    }
                }
            }
            out.append('
            return Regex(out.toString())
        }
    }
}
)
            return Regex(out.toString())
        }
    }
}
