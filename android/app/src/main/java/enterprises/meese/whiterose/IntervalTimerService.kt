package enterprises.meese.whiterose
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.Calendar
import java.util.Locale

class IntervalTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null
    private var tickerJob: Job? = null
    private val notificationIdCounter = AtomicInteger(NOTIFICATION_ID_DING_BASE)
    
    // Sound and vibration
    private var soundPool: SoundPool? = null
    private var soundId: Int = 0
    private var soundLoaded: Boolean = false
    
    // Pomodoro tracking
    private var currentPhase: String = "work"
    private var cycleCount: Int = 0

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
        private const val PREF_SERVICE_RUNNING = "service_running"
        private const val PREF_ALIGN_TO_CLOCK = "align_to_clock"
        private const val PREF_PLAY_SOUND = "play_sound"
        private const val PREF_VIBRATE = "vibrate"
        private const val PREF_MODE = "mode"
        private const val PREF_NEXT_TRIGGER_MS = "next_trigger_ms"
        
        private const val DEFAULT_INTERVAL_MINUTES = 5
        
        // Timer modes
        const val MODE_SIMPLE = "simple"
        const val MODE_POMODORO_SIMPLE = "pomodoro_simple"
        const val MODE_POMODORO_ADVANCED = "pomodoro_advanced"
        
        // Pomodoro durations
        const val WORK_MIN = 25
        const val BREAK_MIN = 5
        const val LONG_BREAK_MIN = 15

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
        setupSoundPool()
    }
    
    private fun setupSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
            
        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()
            
        soundPool?.setOnLoadCompleteListener { _, _, status ->
            soundLoaded = status == 0
        }
        
        soundId = soundPool?.load(this, R.raw.tick, 1) ?: 0
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                
                // Get mode from preferences
                val mode = prefs.getString(PREF_MODE, MODE_SIMPLE) ?: MODE_SIMPLE
                
                // Get interval from intent or determine based on mode
                var intervalMinutes = intent.getIntExtra(
                    EXTRA_INTERVAL_MINUTES,
                    prefs.getInt(PREF_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
                )
                
                // For Pomodoro modes, set initial phase and interval
                if (mode != MODE_SIMPLE) {
                    currentPhase = "work"
                    cycleCount = 0
                    intervalMinutes = WORK_MIN
                }
                
                // Save settings to preferences
                prefs.edit()
                    .putInt(PREF_INTERVAL_MINUTES, intervalMinutes)
                    .putBoolean(PREF_SERVICE_RUNNING, true)
                    .putString(PREF_MODE, mode)
                    .apply()
                
                // Compute initial delay
                val alignPref = prefs.getBoolean(PREF_ALIGN_TO_CLOCK, false)
                val delayMs = if (alignPref) computeAlignedDelayMillis(intervalMinutes) else intervalMinutes * 60_000L
                
                // Set next trigger time
                val nextTriggerMs = System.currentTimeMillis() + delayMs
                prefs.edit().putLong(PREF_NEXT_TRIGGER_MS, nextTriggerMs).apply()
                
                // Start foreground service with notification
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID_FOREGROUND,
                        createForegroundNotification(intervalMinutes),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(
                        NOTIFICATION_ID_FOREGROUND,
                        createForegroundNotification(intervalMinutes)
                    )
                }
                
                // Start the ticker to update the notification every second
                startTicker()
                
                // Start the timer coroutine
                startIntervalTimer(intervalMinutes)
                
                return START_REDELIVER_INTENT
            }
            else -> return START_NOT_STICKY
        }
    }
    
    private fun startTicker() {
        // Cancel existing ticker if any
        tickerJob?.cancel()
        
        // Start a new ticker that updates the notification every second
        tickerJob = serviceScope.launch {
            while (isActive) {
                val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                val intervalMinutes = prefs.getInt(PREF_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
                
                // Update the foreground notification with the countdown
                val notification = createForegroundNotification(intervalMinutes)
                with(NotificationManagerCompat.from(this@IntervalTimerService)) {
                    if (hasNotificationPermission()) {
                        notify(NOTIFICATION_ID_FOREGROUND, notification)
                    }
                }
                
                delay(1000) // Update every second
            }
        }
    }

    private fun startIntervalTimer(intervalMinutes: Int) {
        // Cancel any existing timer
        timerJob?.cancel()
        
        // Start a new timer
        timerJob = serviceScope.launch {
            while (isActive) {
                val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                val alignPref = prefs.getBoolean(PREF_ALIGN_TO_CLOCK, false)
                val mode = prefs.getString(PREF_MODE, MODE_SIMPLE) ?: MODE_SIMPLE
                val playSound = prefs.getBoolean(PREF_PLAY_SOUND, true)
                val vibrate = prefs.getBoolean(PREF_VIBRATE, false)
                
                // Compute delay for this interval
                val delayMs = if (alignPref) computeAlignedDelayMillis(intervalMinutes) else intervalMinutes * 60_000L
                
                // Set next trigger time
                val nextTriggerMs = System.currentTimeMillis() + delayMs
                prefs.edit().putLong(PREF_NEXT_TRIGGER_MS, nextTriggerMs).apply()
                
                // Wait for the interval
                delay(delayMs)
                
                // Play sound if enabled
                if (playSound && soundLoaded) {
                    soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
                }
                
                // Vibrate if enabled
                if (vibrate) {
                    vibrate(200)
                }
                
                // Post silent notification if we have permission
                if (hasNotificationPermission()) {
                    val notificationId = notificationIdCounter.getAndIncrement()
                    val notification = NotificationCompat.Builder(this@IntervalTimerService, CHANNEL_ID_DING)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(getString(R.string.ding_title))
                        .setContentText(getString(R.string.ding_text, minutesLabel(intervalMinutes)))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        // No sound or vibration defaults - we handle these manually
                        .setAutoCancel(true)
                        .build()
                    
                    with(NotificationManagerCompat.from(this@IntervalTimerService)) {
                        notify(notificationId, notification)
                    }
                }
                
                // If using Pomodoro mode, update phase and interval for next cycle
                if (mode != MODE_SIMPLE) {
                    if (currentPhase == "work") {
                        currentPhase = "break"
                        
                        // For advanced mode, every 4th break is a long break
                        if (mode == MODE_POMODORO_ADVANCED && cycleCount > 0 && cycleCount % 4 == 0) {
                            prefs.edit().putInt(PREF_INTERVAL_MINUTES, LONG_BREAK_MIN).apply()
                        } else {
                            prefs.edit().putInt(PREF_INTERVAL_MINUTES, BREAK_MIN).apply()
                        }
                    } else {
                        currentPhase = "work"
                        cycleCount++
                        prefs.edit().putInt(PREF_INTERVAL_MINUTES, WORK_MIN).apply()
                    }
                }
            }
        }
    }

    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }

    private fun computeAlignedDelayMillis(intervalMinutes: Int): Long {
        val now = Calendar.getInstance()
        val currentMinute = now.get(Calendar.MINUTE)
        val currentSecond = now.get(Calendar.SECOND)
        val currentMillis = now.get(Calendar.MILLISECOND)
        
        // Calculate minutes until next boundary
        val minutesToNextBoundary = intervalMinutes - (currentMinute % intervalMinutes)
        val minutesToWait = if (minutesToNextBoundary == 0 && (currentSecond > 0 || currentMillis > 0)) 
            intervalMinutes else minutesToNextBoundary
        
        // Convert to milliseconds, subtracting elapsed seconds and milliseconds
        return (minutesToWait * 60 * 1000L) - (currentSecond * 1000L) - currentMillis
    }

    private fun minutesLabel(minutes: Int): String = 
        resources.getQuantityString(R.plurals.minutes_label, minutes, minutes)
        
    private fun formatRemaining(millisUntil: Long): String {
        if (millisUntil <= 0) return "00:00"
        
        val seconds = (millisUntil / 1000) % 60
        val minutes = (millisUntil / (1000 * 60)) % 60
        
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun createNotificationChannels() {
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
        
        // Get the next trigger time and compute remaining time
        val nextTriggerMs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getLong(PREF_NEXT_TRIGGER_MS, 0L)
        val remainingMs = maxOf(0L, nextTriggerMs - System.currentTimeMillis())
        val remainingFormatted = formatRemaining(remainingMs)
        
        // Get mode and phase information
        val mode = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getString(PREF_MODE, MODE_SIMPLE) ?: MODE_SIMPLE
        
        // Build the content text
        val contentText = if (mode == MODE_SIMPLE) {
            // Simple mode: just show the interval and countdown
            getString(R.string.tracker_text, minutesLabel(intervalMinutes)) + 
                " • " + getString(R.string.next_in_label, remainingFormatted)
        } else {
            // Pomodoro mode: show phase and countdown
            val phaseText = getString(
                if (currentPhase == "work") R.string.phase_work else R.string.phase_break
            )
            "$phaseText • ${getString(R.string.next_in_label, remainingFormatted)}"
        }
        
        // Create the notification
        return NotificationCompat.Builder(this, CHANNEL_ID_TRACKER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.tracker_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_stop),
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
        // Update service running state
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SERVICE_RUNNING, false)
            .apply()
        
        // Clean up resources
        timerJob?.cancel()
        tickerJob?.cancel()
        soundPool?.release()
        soundPool = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't provide binding
    }
}
