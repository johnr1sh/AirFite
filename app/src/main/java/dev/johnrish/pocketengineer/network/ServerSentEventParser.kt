package dev.johnrish.pocketengineer.network

/**
 * Incremental parser for UTF-8 Server-Sent Events.
 *
 * Call [feed] with decoded text chunks. Chunk boundaries may split CRLF pairs or fields.
 * Events are dispatched only after a blank line, as required by the SSE format. Incomplete
 * events at end-of-stream are intentionally discarded.
 */
data class ServerSentEvent(
    val data: String,
    val type: String,
    val id: String?
)

class ServerSentEventParser(
    private val maxLineChars: Int = DEFAULT_MAX_LINE_CHARS,
    private val maxEventDataChars: Int = DEFAULT_MAX_EVENT_DATA_CHARS
) {
    private val line = StringBuilder()
    private val data = StringBuilder()
    private var eventType = "message"
    private var lastEventId: String? = null
    private var skipNextLineFeed = false
    private var firstCharacter = true

    init {
        require(maxLineChars > 0) { "Line limit must be positive." }
        require(maxEventDataChars > 0) { "Event data limit must be positive." }
    }

    fun feed(chunk: String): List<ServerSentEvent> {
        if (chunk.isEmpty()) return emptyList()
        val emitted = ArrayList<ServerSentEvent>()

        for (character in chunk) {
            if (firstCharacter) {
                firstCharacter = false
                if (character == '\uFEFF') continue
            }

            if (skipNextLineFeed) {
                skipNextLineFeed = false
                if (character == '\n') continue
            }

            when (character) {
                '\r' -> {
                    processLine(emitted)
                    skipNextLineFeed = true
                }
                '\n' -> processLine(emitted)
                else -> {
                    line.append(character)
                    if (line.length > maxLineChars) {
                        throw IllegalArgumentException("SSE line exceeds the configured limit.")
                    }
                }
            }
        }
        return emitted
    }

    /**
     * SSE requires a terminating blank line. Any partial line/event is discarded at EOF.
     */
    fun finish(): List<ServerSentEvent> {
        line.setLength(0)
        data.setLength(0)
        eventType = "message"
        skipNextLineFeed = false
        return emptyList()
    }

    private fun processLine(emitted: MutableList<ServerSentEvent>) {
        if (line.isEmpty()) {
            dispatch(emitted)
            return
        }

        val valueLine = line.toString()
        line.setLength(0)
        if (valueLine.startsWith(':')) return

        val separator = valueLine.indexOf(':')
        val field = if (separator < 0) valueLine else valueLine.substring(0, separator)
        var value = if (separator < 0) "" else valueLine.substring(separator + 1)
        if (value.startsWith(' ')) value = value.substring(1)

        when (field) {
            "data" -> {
                if (value.length + data.length + 1 > maxEventDataChars) {
                    throw IllegalArgumentException("SSE event exceeds the configured data limit.")
                }
                data.append(value).append('\n')
            }
            "event" -> eventType = value.ifEmpty { "message" }
            "id" -> if ('\u0000' !in value) lastEventId = value
        }
    }

    private fun dispatch(emitted: MutableList<ServerSentEvent>) {
        if (data.isNotEmpty()) {
            data.setLength(data.length - 1)
            emitted += ServerSentEvent(data.toString(), eventType, lastEventId)
        }
        data.setLength(0)
        eventType = "message"
    }

    companion object {
        const val DEFAULT_MAX_LINE_CHARS = 64 * 1024
        const val DEFAULT_MAX_EVENT_DATA_CHARS = 256 * 1024
    }
}
