package dev.johnrish.pocketengineer.background

/**
 * Pure policy for bounded task history. Active tasks are never evicted just to satisfy the
 * history cap; terminal records are removed oldest-first. The caller must delete inputs for
 * [Merge.removedIds] in the same SharedPreferences edit as the merged records.
 */
object BackgroundTaskRecordPolicy {
    const val MAX_RECORDS = 20
    const val MAX_ACTIVE_TASKS = 10

    private val activeStatuses = setOf("Queued", "Running", "Retrying")

    data class Merge(
        val records: List<BackgroundTaskRecord>,
        val removedIds: List<String>
    )

    fun isActive(status: String): Boolean = status in activeStatuses

    fun activeCount(records: List<BackgroundTaskRecord>): Int = records.count { isActive(it.status) }

    fun merge(
        existing: List<BackgroundTaskRecord>,
        incoming: BackgroundTaskRecord,
        maxRecords: Int = MAX_RECORDS
    ): Merge {
        require(maxRecords > 0) { "Record limit must be positive." }
        val updated = (existing.filterNot { it.id == incoming.id } + incoming)
            .groupBy { it.id }
            .map { (_, duplicates) -> duplicates.maxBy { it.createdAt } }
            .sortedWith(compareByDescending<BackgroundTaskRecord> { it.createdAt }.thenBy { it.id })
        if (updated.size <= maxRecords) return Merge(updated, emptyList())

        val excess = updated.size - maxRecords
        val removable = updated.filterNot { isActive(it.status) }
            .sortedWith(compareBy<BackgroundTaskRecord> { it.createdAt }.thenBy { it.id })
            .take(excess)
        val removedIds = removable.map { it.id }.toSet()
        return Merge(updated.filterNot { it.id in removedIds }, removedIds.toList())
    }
}
