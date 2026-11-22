package dev.johnrish.pocketengineer.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.johnrish.pocketengineer.MainActivity
import dev.johnrish.pocketengineer.ProviderEndpoint
import dev.johnrish.pocketengineer.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class BackgroundAgentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()
        val store = BackgroundTaskStore
        val record = store.list(applicationContext).firstOrNull { it.id == id } ?: return@withContext Result.failure()
        val task = store.input(applicationContext, id) ?: return@withContext Result.failure()
        store.update(applicationContext, record.copy(status = "Running", error = ""))
        setForeground(createForegroundInfo(id))

        try {
            val endpoint = ProviderEndpoint.normalize(task.baseUrl) ?: throw IllegalArgumentException(ProviderEndpoint.ERROR_MESSAGE)
            val apiKey = SecureKeyStore().decrypt(task.encryptedApiKey)
            if (apiKey.isBlank()) throw IllegalStateException("Provider key is missing.")
            val requestBody = JSONObject()
                .put("model", task.model)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "You are AirFite, a concise mobile coding assistant."))
                    .put(JSONObject().put("role", "user").put("content", task.prompt)))
                .toString()
            val request = Request.Builder().url("$endpoint/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .build()
            client.newCall(request).execute().use { response ->
                val raw = readBounded(response, MAX_RESPONSE_BYTES)
                if (!response.isSuccessful) throw IllegalStateException("Provider returned HTTP ${response.code}.")
                val answer = JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Provider returned an empty response.")
                val encrypted = SecureKeyStore().encrypt(answer)
                store.update(applicationContext, record.copy(status = "Completed", encryptedResult = encrypted, error = ""))
                notifyUser(id, "Background task completed", "Your response is ready in AirFite.")
            }
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < MAX_RETRIES) {
                store.update(applicationContext, record.copy(status = "Retrying", error = "Network interrupted; retry scheduled."))
                Result.retry()
            } else {
                store.update(applicationContext, record.copy(status = "Failed", error = "Network request failed after retries."))
                notifyUser(id, "Background task failed", "Open AirFite to retry.")
                Result.failure()
            }
        } catch (e: Exception) {
            store.update(applicationContext, record.copy(status = "Failed", error = (e.message ?: "Task failed").take(240)))
            notifyUser(id, "Background task failed", "Open AirFite to review the task.")
            Result.failure()
        }
    }

    private fun readBounded(response: okhttp3.Response, maxBytes: Int): String {
        val body = response.body ?: return ""
        if (body.contentLength() > maxBytes) throw IllegalStateException("Response exceeds the 2 MB limit.")
        body.byteStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) throw IllegalStateException("Response exceeds the 2 MB limit.")
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun createForegroundInfo(taskId: String): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("AirFite is working")
            .setContentText("Your task is running in the background.")
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(taskId.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(taskId.hashCode(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Background tasks", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notifyUser(taskId: String, title: String, message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Background tasks", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_DESTINATION, "Agents")
            .putExtra(EXTRA_TASK_ID, taskId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(applicationContext, taskId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(taskId.hashCode(), notification) }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val EXTRA_TASK_ID = "background_task_id"
        const val CHANNEL_ID = "airfite_background_tasks"
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_RETRIES = 3
    }
}
