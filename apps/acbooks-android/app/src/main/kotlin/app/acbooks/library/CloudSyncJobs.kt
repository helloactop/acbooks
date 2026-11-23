package app.acbooks.library

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import app.acbooks.core.database.LibraryDatabase
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val RetryJob = 12101
private const val PeriodicJob = 12102

internal object CloudSyncJobs {
    fun request(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val component = ComponentName(context, CloudSyncJobService::class.java)
        if (scheduler.getPendingJob(RetryJob) == null) {
            scheduler.schedule(JobInfo.Builder(RetryJob, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(5_000)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true)
                .build())
        }
        if (scheduler.getPendingJob(PeriodicJob) == null) {
            scheduler.schedule(JobInfo.Builder(PeriodicJob, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(30 * 60_000L)
                .setPersisted(true)
                .build())
        }
    }

    fun cancel(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        scheduler.cancel(RetryJob)
        scheduler.cancel(PeriodicJob)
    }
}

class CloudSyncJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val retry = try {
                val dao = LibraryDatabase.open(applicationContext).library()
                val sync = LibraryCloudSync(applicationContext, dao, LibraryRepository(applicationContext, dao))
                sync.sync() == false && sync.hasRetryableWork()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: CloudIdentityChanged) {
                false
            } catch (failure: CloudHttpException) {
                failure.status == 429 || failure.status >= 500
            } catch (_: IOException) {
                true
            } catch (_: Exception) {
                Log.w("LibrarySync", "Scheduled synchronization could not finish")
                false
            }
            jobs.remove(params.jobId)
            jobFinished(params, retry)
        }
        jobs[params.jobId] = job
        job.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobs.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
