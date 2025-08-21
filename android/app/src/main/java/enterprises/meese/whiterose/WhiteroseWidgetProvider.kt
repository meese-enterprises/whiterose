package enterprises.meese.whiterose

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
         * Choose the appropriate layout based on widget size
         */
        private fun chooseLayoutRes(context: Context, options: Bundle?): Int {
            if (options != null) {
                // Get min width and height in dp
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                
                // Compact if either dimension is below our large-layout threshold
                // (≈ 3×2 cells ~ 180×110 dp).  Otherwise use large layout.
                if (minWidth < 180 || minHeight < 110) {
                    return R.layout.whiterose_widget_compact
                } else {
                    return R.layout.whiterose_widget
                }
            }
            
            // Fallback when options unavailable
            return R.layout.whiterose_widget_compact
        }
        
        /**
         * Updates all widget instances
         */
        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, WhiteroseWidgetProvider::class.java)
            )
            
            // Update each widget individually
            for (appWidgetId in appWidgetIds) {
                updateSingle(context, appWidgetManager, appWidgetId)
            }
        }
        
        /**
         * Updates a single widget instance
         */
        private fun updateSingle(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            // Get widget options to determine size
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            
            // Build RemoteViews with the appropriate layout
            val views = buildRemoteViews(context, options)
            
            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        /**
         * Builds the RemoteViews for the widget
         */
        private fun buildRemoteViews(context: Context, options: Bundle? = null): RemoteViews {
            // Choose layout based on size
            val layoutId = chooseLayoutRes(context, options)
            val views = RemoteViews(context.packageName, layoutId)
            
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
            
            // Set phase text (always set even if hidden in compact layout)
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
            
            // Determine background color (try dynamic color first, fallback to semi-transparent black)
            var bgColor = 0xCC000000.toInt() // Default: semi-transparent black
            val useMaterial = prefs.getBoolean("widget_material", true)
            
            if (useMaterial && Build.VERSION.SDK_INT >= 31) { // Android 12+ and user allows Material You
                try {
                    // Try to get system accent color
                    val accentColor = context.getColor(android.R.color.system_accent1_800)
                    // Apply alpha for translucency
                    bgColor = (0xCC000000.toInt() and 0xFF000000.toInt()) or (accentColor and 0x00FFFFFF)
                } catch (e: Exception) {
                    // Fallback to default if system color not available
                }
            }
            
            // Apply background color
            views.setInt(R.id.bg, "setColorFilter", bgColor)
            
            // Build PendingIntent that directly starts/stops the foreground service
            val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

            val serviceIntent: Intent = if (serviceRunning) {
                // Stop the timer
                Intent(context, IntervalTimerService::class.java).apply {
                    action = IntervalTimerService.ACTION_STOP
                }
            } else {
                // Start the timer with the user-selected interval
                Intent(context, IntervalTimerService::class.java).apply {
                    action = IntervalTimerService.ACTION_START
                    putExtra(IntervalTimerService.EXTRA_INTERVAL_MINUTES, intervalMinutes)
                }
            }

            val togglePendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, 0, serviceIntent, pendingFlags)
            } else {
                PendingIntent.getService(context, 0, serviceIntent, pendingFlags)
            }

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
        for (appWidgetId in appWidgetIds) {
            updateSingle(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        // Widget size changed, update it
        updateSingle(context, appWidgetManager, appWidgetId)
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
