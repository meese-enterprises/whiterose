package enterprises.meese.whiterose

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import java.util.Locale
import kotlin.math.ceil

/**
 * Implementation of App Widget functionality for Whiterose timer.
 * Shows current status, remaining time, and provides Start/Stop button.
 */
class WhiteroseWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_UPDATE = "enterprises.meese.whiterose.action.WIDGET_UPDATE"
        const val ACTION_WIDGET_TOGGLE = "enterprises.meese.whiterose.action.WIDGET_TOGGLE"
        
        private const val PREF_NAME = "whiterose_prefs"
        
        /**
         * Updates all widget instances
         */
        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, WhiteroseWidgetProvider::class.java)
            )
            
            if (appWidgetIds.isNotEmpty()) {
                // Update all widgets
                appWidgetManager.updateAppWidget(appWidgetIds, buildRemoteViews(context))
            }
        }
        
        /**
         * Builds the RemoteViews for the widget
         */
        private fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.whiterose_widget)
            
            // Get preferences
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val serviceRunning = prefs.getBoolean("service_running", false)
            val nextTriggerMs = prefs.getLong("next_trigger_ms", 0L)
            val mode = prefs.getString("mode", IntervalTimerService.MODE_SIMPLE) ?: IntervalTimerService.MODE_SIMPLE
            val currentPhase = prefs.getString("current_phase", "work") ?: "work"
            val intervalMinutes = prefs.getInt("interval_minutes", 5)
            
            // Format remaining time
            val timeText = if (serviceRunning) {
                val remainingMs = (nextTriggerMs - System.currentTimeMillis()).coerceAtLeast(0L)
                // Use ceiling to avoid off-by-one truncation
                val remainingSecTotal = ceil(remainingMs / 1000.0).toLong()
                val mins = (remainingSecTotal / 60).toInt()
                val secs = (remainingSecTotal % 60).toInt()
                String.format(Locale.US, "%02d:%02d", mins, secs)
            } else {
                "--:--"
            }
            
            // Set time text
            views.setTextViewText(R.id.txtTime, timeText)
            
            // Set phase text
            val phaseText = if (!serviceRunning) {
                "Stopped"
            } else if (mode == IntervalTimerService.MODE_SIMPLE) {
                "Simple"
            } else {
                val phaseLabel = if (currentPhase == "work") "Work" else "Break"
                "$phaseLabel"
            }
            views.setTextViewText(R.id.txtPhase, phaseText)
            
            // Set button text
            val buttonText = if (serviceRunning) {
                context.getString(R.string.btn_stop)
            } else {
                context.getString(R.string.btn_start)
            }
            views.setTextViewText(R.id.btnToggle, buttonText)
            
            // Set button click action
            val toggleIntent = Intent(context, WhiteroseWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_TOGGLE
            }
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            
            val togglePendingIntent = PendingIntent.getBroadcast(
                context, 0, toggleIntent, pendingFlags
            )
            views.setOnClickPendingIntent(R.id.btnToggle, togglePendingIntent)
            
            return views
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update all widgets
        updateAll(context)
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_WIDGET_UPDATE -> {
                // Update all widgets
                updateAll(context)
            }
            ACTION_WIDGET_TOGGLE -> {
                // Toggle service state
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val serviceRunning = prefs.getBoolean("service_running", false)
                
                if (serviceRunning) {
                    // Stop service
                    IntervalTimerService.stopService(context)
                } else {
                    // Start service with current interval
                    val intervalMinutes = prefs.getInt("interval_minutes", 5)
                    IntervalTimerService.startService(context, intervalMinutes)
                }
                
                // Update widgets immediately for better UX
                updateAll(context)
            }
        }
    }
}
