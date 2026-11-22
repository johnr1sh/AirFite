package dev.johnrish.pocketengineer.workspace

/**
 * A syntactically valid path relative to the user-granted workspace root.
 *
 * This only proves the string is safe to *compare* against entries enumerated from the Storage
 * Access Framework tree; it never touches the filesystem. Callers must still resolve the path by
 * exact match against the listing, so a valid [WorkspacePath] grants nothing on its own.
 *
 * Rejected: empty input, absolute paths, `.`/`..` segments, empty segments (`a//b`, trailing `/`),
 * backslashes, control characters (including NUL), and over-long paths or segments.
 */
class WorkspacePath private constructor(val value: String) {
    val segments: List<String> get() = value.split('/')

    override fun equals(other: Any?): Boolean = other is WorkspacePath && other.value == value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 1024
        const val MAX_SEGMENT_LENGTH = 255

        /** Returns the validated path, or throws [IllegalArgumentException] with a safe, user-showable reason. */
        fun parse(raw: String): WorkspacePath {
            reject(raw.isEmpty() || raw.isBlank(), "Path is empty.")
            reject(raw.length > MAX_LENGTH, "Path is longer than $MAX_LENGTH characters.")
            reject(raw.any { it.code < 0x20 || it.code == 0x7F }, "Path contains control characters.")
            reject('\\' in raw, "Path must use '/' separators.")
            reject(raw.startsWith("/"), "Path must be relative to the workspace.")
            val segments = raw.split('/')
            for (segment in segments) {
                reject(segment.isEmpty(), "Path contains an empty segment.")
                reject(segment == "." || segment == "..", "Path must not contain '.' or '..' segments.")
                reject(segment.length > MAX_SEGMENT_LENGTH, "Path segment is longer than $MAX_SEGMENT_LENGTH characters.")
            }
            return WorkspacePath(raw)
        }

        fun parseOrNull(raw: String): WorkspacePath? = try {
            parse(raw)
        } catch (_: IllegalArgumentException) {
            null
        }

        private fun reject(condition: Boolean, message: String) {
            if (condition) throw IllegalArgumentException(message)
        }
    }
}
