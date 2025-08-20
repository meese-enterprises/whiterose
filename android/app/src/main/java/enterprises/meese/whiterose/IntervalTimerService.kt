package enterprises.meese.whiterose

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

class IntervalTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null
    private val notificationIdCounter = AtomicInteger(NOTIFICATION_ID_DING_BASE)

    companion object {
        const val ACTION_START = "enterprises.meese.whiterose.action.START"
        const val ACTION_STOP = "enterprises.meese.whiterose.action.STOP"
        const val EXTRA_INTERVAL_MINUTES = "enterprises.meese.whiterose.extra.INTERVAL_MINUTES"
        
        private const val NOTIFICATION_ID_FOREGROUND = 1001
        private const val NOTIFICATION_ID_DING_BASE = 2000
        
        const val CHANNEL_ID_TRACKER = "whiterose_tracker"
        const val CHANNEL_ID_DING = "whiterose_ding"
        
        private const val PREF_NAME = "whiterose_prefs"
        private const val PREF_INTERVAL_MINUTES = "interval_minutes"
        private const val DEFAULT_INTERVAL_MINUTES = 5

        fun buildStartIntent(context: Context, intervalMinutes: Int): Intent {
            return Intent(context, IntervalTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INTERVAL_MINUTES, intervalMinutes)
            }
        }

        fun startService(context: Context, intervalMinutes: Int) {
            val intent = buildStartIntent(context, intervalMinutes)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, IntervalTimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                // Get interval from intent or shared preferences
                val intervalMinutes = intent.getIntExtra(
                    EXTRA_INTERVAL_MINUTES,
                    getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                        .getInt(PREF_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
                )
                
                // Save interval to preferences
                getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(PREF_INTERVAL_MINUTES, intervalMinutes)
                    .apply()
                
                // Start foreground service with notification
                startForeground(
                    NOTIFICATION_ID_FOREGROUND,
                    createForegroundNotification(intervalMinutes)
                )
                
                // Start the timer coroutine
                startIntervalTimer(intervalMinutes)
                
                return START_REDELIVER_INTENT
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun startIntervalTimer(intervalMinutes: Int) {
        // Cancel any existing timer
        timerJob?.cancel()
        
        // Start a new timer
        timerJob = serviceScope.launch {
            while (isActive) {
                // Wait for the specified interval
                delay(intervalMinutes * 60_000L)
                
                // Post notification if we have permission
                if (hasNotificationPermission()) {
                    val notificationId = notificationIdCounter.getAndIncrement()
                    val notification = NotificationCompat.Builder(this@IntervalTimerService, CHANNEL_ID_DING)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Time tick")
                        .setContentText("Another $intervalMinutes minutes passed")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
                        .setAutoCancel(true)
                        .build()
                    
                    with(NotificationManagerCompat.from(this@IntervalTimerService)) {
                        notify(notificationId, notification)
                    }
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the tracker channel (low importance, no sound)
            val trackerChannel = NotificationChannel(
                CHANNEL_ID_TRACKER,
                "Time Tracker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the time tracking service is running"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }
            
            // Create the ding channel (high importance, with sound and vibration)
            val dingChannel = NotificationChannel(
                CHANNEL_ID_DING,
                "Time Intervals",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when time intervals have passed"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Register both channels
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(trackerChannel)
            notificationManager.createNotificationChannel(dingChannel)
        }
    }

    private fun createForegroundNotification(intervalMinutes: Int): android.app.Notification {
        // Create a stop intent for the notification action
        val stopIntent = Intent(this, IntervalTimerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Create the notification
        return NotificationCompat.Builder(this, CHANNEL_ID_TRACKER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Whiterose Timer")
            .setContentText("Tracking time in $intervalMinutes minute intervals")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission is implicitly granted on older Android versions
        }
    }

    override fun onDestroy() {
        timerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't provide binding
    }
}
