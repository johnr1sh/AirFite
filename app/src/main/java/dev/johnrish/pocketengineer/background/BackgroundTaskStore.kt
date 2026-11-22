package dev.johnrish.pocketengineer.background

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BackgroundTaskRecord(
    val id: String,
    val status: String,
    val createdAt: Long,
    val encryptedResult: String = "",
    val error: String = ""
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

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(context: Context): List<BackgroundTaskRecord> = runCatching {
        val array = JSONArray(prefs(context).getString(RECORDS, "[]"))
        (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { json ->
                BackgroundTaskRecord(
                    id = json.optString("id"),
                    status = json.optString("status"),
                    createdAt = json.optLong("createdAt"),
                    encryptedResult = json.optString("result"),
                    error = json.optString("error")
                )
            }
        }.sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    fun add(context: Context, id: String, input: BackgroundTaskInput) {
        val encryptedPrompt = dev.johnrish.pocketengineer.SecureKeyStore().encrypt(input.prompt)
        val storedInput = JSONObject()
            .put("prompt", encryptedPrompt)
            .put("apiKey", input.encryptedApiKey)
            .put("baseUrl", input.baseUrl)
            .put("model", input.model)
        prefs(context).edit().putString(INPUT_PREFIX + id, storedInput.toString()).apply()
        update(context, BackgroundTaskRecord(id, "Queued", System.currentTimeMillis()))
    }

    fun input(context: Context, id: String): BackgroundTaskInput? = runCatching {
        val json = JSONObject(prefs(context).getString(INPUT_PREFIX + id, null) ?: return null)
        BackgroundTaskInput(
            prompt = dev.johnrish.pocketengineer.SecureKeyStore().decrypt(json.getString("prompt")),
            encryptedApiKey = json.getString("apiKey"),
            baseUrl = json.getString("baseUrl"),
            model = json.getString("model")
        )
    }.getOrNull()

    fun update(context: Context, record: BackgroundTaskRecord) {
        val current = list(context).filterNot { it.id == record.id }
        val json = JSONArray().apply {
            (current + record).sortedByDescending { it.createdAt }.take(20).forEach {
                put(JSONObject().put("id", it.id).put("status", it.status).put("createdAt", it.createdAt)
                    .put("result", it.encryptedResult).put("error", it.error))
            }
        }
        val edit = prefs(context).edit().putString(RECORDS, json.toString())
        if (record.status == "Completed" || record.status == "Failed" || record.status == "Cancelled") edit.remove(INPUT_PREFIX + record.id)
        edit.apply()
    }

    fun delete(context: Context, id: String) {
        val records = JSONArray().apply {
            list(context).filterNot { it.id == id }.forEach {
                put(JSONObject().put("id", it.id).put("status", it.status).put("createdAt", it.createdAt)
                    .put("result", it.encryptedResult).put("error", it.error))
            }
        }
        prefs(context).edit().putString(RECORDS, records.toString()).remove(INPUT_PREFIX + id).apply()
    }

    fun decryptResult(record: BackgroundTaskRecord): String =
        dev.johnrish.pocketengineer.SecureKeyStore().decrypt(record.encryptedResult)
}
