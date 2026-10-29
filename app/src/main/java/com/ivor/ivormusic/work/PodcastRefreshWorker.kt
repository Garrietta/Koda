package com.ivor.ivormusic.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ivor.ivormusic.data.PodcastRefreshPolicy
import com.ivor.ivormusic.data.PodcastRepository
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Silent periodic refresh of the subscribed RSS podcast feeds, so the New
 * episodes rail can update even when the app is never opened. Deliberately
 * silent: no notification, no opt-in gate — auto-update is the feature the
 * subscriber already asked for, and a round is a handful of small RSS
 * fetches, not a user-visible event. The repository debounces the round to
 * one per hour, so a worker run shortly after a foreground refresh is a
 * no-op instead of a second fetch.
 *
 * A failed round waits for the next scheduled round instead of retrying:
 * feeds are re-checked hours later anyway, and a dead feed URL must never
 * turn into a retry storm.
 */
class PodcastRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        return try {
            PodcastRepository(applicationContext).refreshAllFeeds()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(TAG, "Podcast refresh failed", e)
            Result.success()
        }
    }

    companion object {
        private const val TAG = "PodcastRefreshWorker"
        private const val UNIQUE_WORK_NAME = "podcast_refresh"

        /** Keep exactly one periodic refresh scheduled. */
        fun sync(context: Context) {
            val request = PeriodicWorkRequestBuilder<PodcastRefreshWorker>(
                PodcastRefreshPolicy.BACKGROUND_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
