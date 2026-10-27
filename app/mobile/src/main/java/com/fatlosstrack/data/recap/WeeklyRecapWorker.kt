package com.fatlosstrack.data.recap

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Posts the weekly recap notification, then queues next week's run.
 * Only local numbers go into the notification — the AI paragraph is
 * generated when the recap screen is opened, so nothing is spent here.
 */
@HiltWorker
class WeeklyRecapWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WeeklyRecapRepository,
    private val preferencesManager: PreferencesManager,
    private val appLogger: AppLogger,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!preferencesManager.weeklyRecapEnabled.first()) return Result.success()
        try {
            val recap = repository.load(LocalDate.now())
            val outcome = when {
                !recap.hasData -> "skipped — no data"
                WeeklyRecapNotifier.show(applicationContext, recap, preferencesManager.language.first()) -> "posted"
                else -> "skipped — notifications not allowed"
            }
            appLogger.log("RECAP", "Weekly recap ${recap.start}..${recap.end} $outcome")
        } catch (e: CancellationException) {
            // Replaced from Settings — the new request already owns the schedule
            throw e
        } catch (e: Exception) {
            appLogger.error("RECAP", "Weekly recap failed", e)
        }
        // Always succeed: the next run is chained after this one and would be dropped on failure
        WeeklyRecapScheduler.scheduleNextRun(applicationContext, preferencesManager)
        return Result.success()
    }
}
