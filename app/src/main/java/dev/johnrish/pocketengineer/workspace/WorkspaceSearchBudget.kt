package dev.johnrish.pocketengineer.workspace

/**
 * Tracks bytes actually consumed while scanning workspace files.
 *
 * File metadata is supplied by the storage provider and may be stale or under-reported, so it
 * must not be used as the aggregate search-I/O counter. A failed read conservatively consumes
 * its reservation through [abandonFile].
 */
class WorkspaceSearchBudget(
    private val maxFiles: Int,
    private val maxTotalBytes: Int,
    private val maxFileBytes: Int
) {
    init {
        require(maxFiles > 0) { "File limit must be positive." }
        require(maxTotalBytes > 0) { "Total byte limit must be positive." }
        require(maxFileBytes > 0) { "Per-file byte limit must be positive." }
    }

    var filesStarted: Int = 0
        private set

    var bytesRead: Int = 0
        private set

    private var activeReservation = 0

    /** Reserves the next file's maximum read allowance, or returns null when exhausted. */
    fun beginFile(): Int? {
        check(activeReservation == 0) { "Finish or abandon the previous file before starting another." }
        if (filesStarted >= maxFiles || bytesRead >= maxTotalBytes) return null
        filesStarted++
        val limit = minOf(maxFileBytes, maxTotalBytes - bytesRead)
        activeReservation = limit
        return limit
    }

    /** Records the actual number of bytes consumed for the active file. */
    fun finishFile(actualBytesRead: Int) {
        check(activeReservation > 0) { "No workspace file read is active." }
        require(actualBytesRead in 0..activeReservation) { "File read exceeded its reserved byte limit." }
        bytesRead += actualBytesRead
        activeReservation = 0
    }

    /**
     * Charges the full reservation when a read fails and its exact I/O consumption is unknown.
     * This is intentionally conservative to keep repeated failures within the total budget.
     */
    fun abandonFile() {
        if (activeReservation == 0) return
        bytesRead += activeReservation
        activeReservation = 0
    }
}
