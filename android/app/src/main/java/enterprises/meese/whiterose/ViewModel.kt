package enterprises.meese.whiterose

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = application.getSharedPreferences("whiterose_prefs", Application.MODE_PRIVATE)

    private val _intervalMinutes = MutableStateFlow(prefs.getInt("interval_minutes", 5))
    val intervalMinutes = _intervalMinutes.asStateFlow()

    private val _serviceRunning = MutableStateFlow(prefs.getBoolean("service_running", false))
    val serviceRunning = _serviceRunning.asStateFlow()

    private val _alignToClock = MutableStateFlow(prefs.getBoolean("align_to_clock", false))
    val alignToClock = _alignToClock.asStateFlow()

    private val _playSound = MutableStateFlow(prefs.getBoolean("play_sound", true))
    val playSound = _playSound.asStateFlow()

    private val _vibrate = MutableStateFlow(prefs.getBoolean("vibrate", false))
    val vibrate = _vibrate.asStateFlow()

    private val _mode = MutableStateFlow(prefs.getString("mode", "simple") ?: "simple")
    val mode = _mode.asStateFlow()

    private val _nextTriggerMs = MutableStateFlow(prefs.getLong("next_trigger_ms", 0L))
    val nextTriggerMs = _nextTriggerMs.asStateFlow()

    /* ---------------------- Pomodoro & phase additions ---------------------- */
    private val _currentPhase = MutableStateFlow(prefs.getString("current_phase", "work") ?: "work")
    val currentPhase = _currentPhase.asStateFlow()

    private val _pomoWorkMin = MutableStateFlow(prefs.getInt("pomo_work_min", 25))
    val pomoWorkMin = _pomoWorkMin.asStateFlow()

    private val _pomoBreakMin = MutableStateFlow(prefs.getInt("pomo_break_min", 5))
    val pomoBreakMin = _pomoBreakMin.asStateFlow()

    private val _pomoLongMin = MutableStateFlow(prefs.getInt("pomo_long_min", 15))
    val pomoLongMin = _pomoLongMin.asStateFlow()

    private val _pomoLongEvery = MutableStateFlow(prefs.getInt("pomo_long_every", 4))
    val pomoLongEvery = _pomoLongEvery.asStateFlow()

    /* ----------------------- Widget appearance toggle ---------------------- */
    private val _widgetMaterial = MutableStateFlow(prefs.getBoolean("widget_material", true))
    val widgetMaterial = _widgetMaterial.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "interval_minutes" -> _intervalMinutes.value = prefs.getInt("interval_minutes", 5)
            "service_running" -> _serviceRunning.value = prefs.getBoolean("service_running", false)
            "align_to_clock" -> _alignToClock.value = prefs.getBoolean("align_to_clock", false)
            "play_sound" -> _playSound.value = prefs.getBoolean("play_sound", true)
            "vibrate" -> _vibrate.value = prefs.getBoolean("vibrate", false)
            "mode" -> _mode.value = prefs.getString("mode", "simple") ?: "simple"
            "next_trigger_ms" -> _nextTriggerMs.value = prefs.getLong("next_trigger_ms", 0L)
            "current_phase" -> _currentPhase.value = prefs.getString("current_phase", "work") ?: "work"
            "pomo_work_min" -> _pomoWorkMin.value = prefs.getInt("pomo_work_min", 25)
            "pomo_break_min" -> _pomoBreakMin.value = prefs.getInt("pomo_break_min", 5)
            "pomo_long_min" -> _pomoLongMin.value = prefs.getInt("pomo_long_min", 15)
            "pomo_long_every" -> _pomoLongEvery.value = prefs.getInt("pomo_long_every", 4)
            "widget_material" -> _widgetMaterial.value = prefs.getBoolean("widget_material", true)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onCleared()
    }

    fun setIntervalMinutes(minutes: Int) {
        viewModelScope.launch {
            _intervalMinutes.emit(minutes)
            prefs.edit().putInt("interval_minutes", minutes).apply()
        }
    }

    fun setAlignToClock(enabled: Boolean) {
        viewModelScope.launch {
            _alignToClock.emit(enabled)
            prefs.edit().putBoolean("align_to_clock", enabled).apply()
        }
    }

    fun setPlaySound(enabled: Boolean) {
        viewModelScope.launch {
            _playSound.emit(enabled)
            prefs.edit().putBoolean("play_sound", enabled).apply()
        }
    }

    fun setVibrate(enabled: Boolean) {
        viewModelScope.launch {
            _vibrate.emit(enabled)
            prefs.edit().putBoolean("vibrate", enabled).apply()
        }
    }

    fun setMode(newMode: String) {
        viewModelScope.launch {
            _mode.emit(newMode)
            prefs.edit().putString("mode", newMode).apply()
        }
    }

    /* ---------------- Pomodoro setters ---------------- */
    fun setPomodoroWork(minutes: Int) {
        viewModelScope.launch {
            _pomoWorkMin.emit(minutes)
            prefs.edit().putInt("pomo_work_min", minutes).apply()
        }
    }

    fun setPomodoroBreak(minutes: Int) {
        viewModelScope.launch {
            _pomoBreakMin.emit(minutes)
            prefs.edit().putInt("pomo_break_min", minutes).apply()
        }
    }

    fun setPomodoroLong(minutes: Int) {
        viewModelScope.launch {
            _pomoLongMin.emit(minutes)
            prefs.edit().putInt("pomo_long_min", minutes).apply()
        }
    }

    fun setPomodoroLongEvery(cycles: Int) {
        viewModelScope.launch {
            _pomoLongEvery.emit(cycles)
            prefs.edit().putInt("pomo_long_every", cycles).apply()
        }
    }

    /* ---------------- Widget style setter ---------------- */
    fun setWidgetMaterial(enabled: Boolean) {
        viewModelScope.launch {
            _widgetMaterial.emit(enabled)
            prefs.edit().putBoolean("widget_material", enabled).apply()

            // Broadcast to refresh widgets immediately
            val ctx = getApplication<Application>()
            val intent = Intent(WhiteroseWidgetProvider.ACTION_WIDGET_UPDATE)
            ctx.sendBroadcast(intent)
        }
    }

    fun startTimerService() {
        IntervalTimerService.startService(getApplication(), intervalMinutes.value)
    }

    fun stopTimerService() {
        IntervalTimerService.stopService(getApplication())
    }
}