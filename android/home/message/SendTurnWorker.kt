package com.indoone.home.message

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends one pending turn, off the screen's lifecycle.
 *
 * The send used to run in `rememberCoroutineScope()`, which is tied to the composition:
 * leaving the screen or backgrounding the app cancelled it, and the reply was lost. A
 * Worker belongs to the application, so the request finishes whether or not anyone is
 * looking at the chat.
 *
 * Two rules, both deliberate.
 *
 * **No automatic retry.** A failed turn returns `Result.failure()`, not `Result.retry()`.
 * A chat turn can be a GitHub write - a branch, a commit - and WorkManager retrying it
 * would repeat that side effect without anyone asking. The turn is marked failed, the
 * thread shows it with a retry button, and the user decides.
 *
 * **Text only.** A turn with attachments is not queued here: replaying it would need the
 * picked URIs and their persistable permissions, and a read that silently fails later is
 * worse than the composer restore that already covers that case.
 */
class SendTurnWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val turnId = inputData.getString(KEY_TURN_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()

        val store = PendingTurnStore.get(applicationContext)
        val turn = store.find(turnId) ?: return Result.success()

        // Already settled by another run, or by the user: nothing to do.
        if (turn.status != PendingTurnStore.STATUS_SENDING) {
            return Result.success()
        }

        return try {
            // ChatApi.sendMessage is a blocking call; a Worker's coroutine is not the
            // place to run network I/O on the main dispatcher.
            val response = withContext(Dispatchers.IO) {
                ChatApi.sendMessage(
                    message = turn.text,
                    conversationId = turn.conversationId,
                )
            }
            store.markDelivered(turn.id, response.reply, chipsToJson(response.actionChips))
            Result.success()
        } catch (error: Exception) {
            val reason = error.message
                ?.takeIf { it.isNotBlank() && !it.contains("undefined", ignoreCase = true) }
                ?: "Sankalp could not complete the request."
            store.markFailed(turn.id, reason)
            // Not Result.retry(): see the class comment. A person decides, not the queue.
            Result.failure()
        }
    }

    companion object {
        const val KEY_TURN_ID = "turn_id"
        private const val WORK_PREFIX = "indoone-send-turn-"

        /** One work item per turn, so a re-enqueue cannot double-send the same message. */
        fun workName(turnId: String): String = WORK_PREFIX + turnId
    }
}
