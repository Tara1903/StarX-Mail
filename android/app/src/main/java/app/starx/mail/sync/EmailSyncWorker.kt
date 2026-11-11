package app.starx.mail.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.starx.mail.core.network.NetworkResult
import app.starx.mail.domain.repository.EmailRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EmailSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val emailRepository: EmailRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return when (val result = emailRepository.syncEmails()) {
            is NetworkResult.Success -> Result.success()
            is NetworkResult.Error -> {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
            is NetworkResult.Loading -> Result.retry()
        }
    }
}
