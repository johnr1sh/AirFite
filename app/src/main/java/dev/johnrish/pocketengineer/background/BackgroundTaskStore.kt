package dev.johnrish.pocketengineer.background

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import dev.johnrish.pocketengineer.SecureKeyStore

data class BackgroundTaskRecord(
    val id: String,
    val status: String,
    val createdAt: Long,
    val encryptedResult: String = "",
    val error: String = "",
    val encryptedTitle: String = ""
)

data class BackgroundTaskInput(
    val prompt: String,
    val encryptedApiKey: String,
    val baseUrl: String,
    val model: String
)

object BackgroundTaskStore {
    private const val PREFS = "airfite_background_tasks"
    private const val RECORDS = "records"
    private const val INPUT_PREFIX = "input_"
    private val keys = SecureKeyStore()
    private val lock = Any()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readRecords(context: Context): List<BackgroundTaskRecord> = runCatching {
        val array = JSONArray(prefs(context).getString(RECORDS, "[]"))
        (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { json ->
                BackgroundTaskRecord(
                    id = json.optString("id"),
                    status = json.optString("status"),
                    createdAt = json.optLong("createdAt"),
                    encryptedResult = json.optString("result"),
                    error = json.optString("error"),
                    encryptedTitle = json.optString("title")
                ).takeIf { it.id.isNotBlank() }
            }
        }
    }.getOrDefault(emptyList())

    fun list(context: Context): List<BackgroundTaskRecord> = synchronized(lock) {
        readRecords(context).sortedWith(compareByDescending<BackgroundTaskRecord> { it.createdAt }.thenBy { it.id })
    }

    fun add(context: Context, id: String, input: BackgroundTaskInput) = synchronized(lock) {
        require(id.isNotBlank()) { "Task ID is required." }
        val current = readRecords(context)
        require(BackgroundTaskRecordPolicy.activeCount(current) < BackgroundTaskRecordPolicy.MAX_ACTIVE_TASKS) {
            "AirFite already has ${BackgroundTaskRecordPolicy.MAX_ACTIVE_TASKS} active background tasks. Wait for one to finish before queueing another."
        }
        val storedInput = JSONObject()
            .put("prompt", keys.encrypt(input.prompt))
            .put("apiKey", input.encryptedApiKey)
            .put("baseUrl", input.baseUrl)
            .put("model", input.model)
        val record = BackgroundTaskRecord(id, "Queued", System.currentTimeMillis(), encryptedTitle = keys.encrypt(input.prompt.take(72)))
        val merge = BackgroundTaskRecordPolicy.merge(current, record)
        val editor = prefs(context).edit()
            .putString(INPUT_PREFIX + id, storedInput.toString())
            .putString(RECORDS, encodeRecords(merge.records))
        merge.removedIds.forEach { editor.remove(INPUT_PREFIX + it) }
        editor.apply()
    }

    fun input(context: Context, id: String): BackgroundTaskInput? = synchronized(lock) {
        runCatching {
            val json = JSONObject(prefs(context).getString(INPUT_PREFIX + id, null) ?: return null)
            BackgroundTaskInput(
                prompt = keys.decrypt(json.getString("prompt")),
                encryptedApiKey = json.getString("apiKey"),
                baseUrl = json.getString("baseUrl"),
                model = json.getString("model")
            )
        }.getOrNull()
    }

    fun update(context: Context, record: BackgroundTaskRecord) = synchronized(lock) {
        val current = readRecords(context)
        val existing = current.firstOrNull { it.id == record.id } ?: return@synchronized
        // Cancellation is final. A late worker callback must not resurrect a cancelled task.
        if (existing.status == "Cancelled" && record.status != "Cancelled") return@synchronized
        val merge = BackgroundTaskRecordPolicy.merge(current, record)
        val editor = prefs(context).edit().putString(RECORDS, encodeRecords(merge.records))
        if (record.status == "Completed" || record.status == "Cancelled") editor.remove(INPUT_PREFIX + record.id)
        merge.removedIds.forEach { editor.remove(INPUT_PREFIX + it) }
        editor.apply()
    }

    fun delete(context: Context, id: String) = synchronized(lock) {
        val records = readRecords(context).filterNot { it.id == id }
        prefs(context).edit().putString(RECORDS, encodeRecords(records)).remove(INPUT_PREFIX + id).apply()
    }

    private fun encodeRecords(records: List<BackgroundTaskRecord>): String = JSONArray().apply {
        records.forEach {
            put(JSONObject().put("id", it.id).put("status", it.status).put("createdAt", it.createdAt)
                .put("result", it.encryptedResult).put("error", it.error).put("title", it.encryptedTitle))
        }
    }.toString()

    fun decryptResult(record: BackgroundTaskRecord): String = keys.decrypt(record.encryptedResult)
    fun decryptTitle(record: BackgroundTaskRecord): String = keys.decrypt(record.encryptedTitle)
}
