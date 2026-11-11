package app.starx.mail.sync

import android.content.Context
import androidx.work.*
import app.starx.mail.core.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun schedulePeriodicSync(intervalMinutes: Long = 15) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<EmailSyncWorker>(
            intervalMinutes, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(Constants.SYNC_WORK_TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            Constants.SYNC_WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeWork = OneTimeWorkRequestBuilder<EmailSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueue(oneTimeWork)
    }

    fun cancelAllSync() {
        workManager.cancelUniqueWork(Constants.SYNC_WORK_TAG)
    }
}
