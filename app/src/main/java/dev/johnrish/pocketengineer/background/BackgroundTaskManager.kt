package dev.johnrish.pocketengineer.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.johnrish.pocketengineer.ProviderEndpoint
import java.util.UUID
import java.util.concurrent.TimeUnit

object BackgroundTaskManager {
    fun enqueue(context: Context, prompt: String, endpoint: String, model: String, encryptedApiKey: String): String {
        require(prompt.isNotBlank()) { "Enter a task prompt first." }
        require(prompt.length <= MAX_PROMPT_CHARS) { "Background prompts are limited to $MAX_PROMPT_CHARS characters." }
        val normalized = ProviderEndpoint.normalize(endpoint) ?: throw IllegalArgumentException(ProviderEndpoint.ERROR_MESSAGE)
        require(model.isNotBlank()) { "Choose a model in Settings first." }
        require(encryptedApiKey.isNotBlank()) { "Add an API key in Settings first." }

        val id = UUID.randomUUID().toString()
        BackgroundTaskStore.add(context, id, BackgroundTaskInput(prompt, encryptedApiKey, normalized, model.trim()))
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = OneTimeWorkRequestBuilder<BackgroundAgentWorker>()
            .setInputData(workDataOf(BackgroundAgentWorker.KEY_TASK_ID to id))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(id)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("airfite_task_$id", ExistingWorkPolicy.KEEP, request)
        return id
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("airfite_task_$id")
        BackgroundTaskStore.delete(context, id)
    }

    const val TAG = "airfite_background_task"
    const val MAX_PROMPT_CHARS = 6000
}
