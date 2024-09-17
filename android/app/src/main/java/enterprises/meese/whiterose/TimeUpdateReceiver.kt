package enterprises.meese.whiterose

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*

class TimeUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Trigger the worker to do the work
        val workRequest = OneTimeWorkRequestBuilder<TimeUpdateWorker>()
            .build()

        WorkManager.getInstance(context)
            .enqueue(workRequest)
    }
}
